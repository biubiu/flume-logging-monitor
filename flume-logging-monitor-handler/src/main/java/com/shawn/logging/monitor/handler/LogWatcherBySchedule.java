package com.shawn.logging.monitor.handler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.flume.Event;
import org.apache.flume.FlumeException;
import org.apache.flume.event.EventBuilder;
import org.springframework.stereotype.Service;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Service
public class LogWatcherBySchedule extends LogWatcher{



    public LogWatcherBySchedule() throws Exception {
        super();
    }

    public void start() throws Exception {
        logStatusMap = JsonFormatMapConfigHelper.loadJsonMap(logStatusMap, logStatusRecordPath,new TypeToken<LogAttribute>() {});
        headerConfigMap = JsonFormatMapConfigHelper.loadJsonMap(headerConfigMap, headerConfigPath,new TypeToken<Map<String, String>>() {});
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("log-watcher-sechdule").build());
        executorService = Executors.newFixedThreadPool(watchFilePaths.size(), new ThreadFactoryBuilder().setNameFormat("file-change-thread").build());
        logger.info("the log watcher started successfully ");
    }

    public void process() {
        List<FileChangeCallable> fileChangeTasks = Lists.newArrayListWithCapacity(watchFilePaths.size());
        for (String path : watchFilePaths) {
            File file = new File(path);
            LogAttribute logAttribute = logStatusMap.containsKey(path)?logStatusMap.get(path):new LogAttribute.Builder()
                                                                                                  .logPath(path)
                                                                                                  .lastModifiedTime(file.lastModified())
                                                                                                  .lastModifiedSize(0L)
                                                                                                  .currentSize(file.length()).build();
            logStatusMap.put(logAttribute.getLogPath(), logAttribute);
            FileChangeCallable fileChangeRunnable = new FileChangeCallable(file,logAttribute, buildHeader(path));
            fileChangeTasks.add(fileChangeRunnable);
        }
        scheduledExecutorService.scheduleAtFixedRate(new ScheduledFileChangeJob(fileChangeTasks, executorService, client), 0, delay, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(){
                public void run() {
                    logger.info("storing log processing to the temp file");
                    JsonFormatMapConfigHelper.writeJsonMap(logStatusMap, logStatusRecordPath,new TypeToken<LogAttribute>() {});
                }
        });
    }


    private final class ScheduledFileChangeJob implements Runnable {
        private List<FileChangeCallable> tasks;
        private ExecutorService executorService;
        private LogMonitorClient client;

        public ScheduledFileChangeJob(List<FileChangeCallable> tasks, ExecutorService executorService, LogMonitorClient client) {
            this.tasks = tasks;
            this.executorService = executorService;
            this.client = client;
        }

        public synchronized void run() {
            List<Event> events = null;
            try {
                    List<Future<Event>> results = executorService.invokeAll(tasks);
                    events = Lists.newLinkedList();
                    for (Future<Event> e : results) {
                        if (e.get() != null)
                            events.add(e.get());
                    }
                    if(events.size() <1 ) return ;
            } catch (InterruptedException | ExecutionException e) {
                logger.error(" thread exception : {}",Throwables.getStackTraceAsString(e ));
            }
                try {
                    client.init();
                } catch (FlumeException e) {
                    rollBackLogStatus();
                    logger.error("cannot establish connection to flume agent , please check the network .{}",Throwables.getStackTraceAsString(e));
                    client.cleanUp();
                    return;
                }
                logger.info("established the connection , prepare to send the event batch");
                if (!client.sendEventsToFlume(events)) {
                    rollBackLogStatus();
                }
                client.cleanUp();
            //}
        }

    }


    private final class FileChangeCallable implements Callable<Event> {
        private File file;
        private LogAttribute logAttribute;
        private ByteBuffer byteBuffer;
        //private long total ;
        private Map<String, String> headers;

        public FileChangeCallable(File file,LogAttribute logAttribute, Map<String, String> headers) {
            this.file = file;
            this.headers = headers;
            this.logAttribute = logAttribute;
        }

        public Event call() {
            Event event = null;
            long fileCurrentSize;
            if (file.exists() && ((fileCurrentSize = file.length()) > 0) && (fileCurrentSize != logAttribute.getCurrentSize())) {
                logger.info(String.format("file %s changed from %s to %s ", file.getName(), logAttribute.getCurrentSize(), fileCurrentSize));
                if (fileCurrentSize < logAttribute.getLastModifiedSize()) {
                    logAttribute.setCurrentSize(0L);
                }
                    long lastModifiedSize = logAttribute.getCurrentSize();

                    long rawReadLength = fileCurrentSize - logAttribute.getCurrentSize();
                    long actualReadLength = (rawReadLength > eventMaxSize)? eventMaxSize: rawReadLength;

                    headers.put("timestamp", String.valueOf(new Date().getTime()));
                    byte[] data = assemblingEvent(logAttribute.getCurrentSize(),actualReadLength);
                    event = EventBuilder.withBody(data, headers);
                    logAttribute.setLastModifiedTime(file.lastModified());
                    logAttribute.setLastModifiedSize(lastModifiedSize);
                    logAttribute.setCurrentSize(lastModifiedSize+actualReadLength);
                    long consumed = logAttribute.getTotalConsumed() + data.length;
                    logAttribute.setTotalConsumed(consumed);
            }
            return event;
        }

        private byte[] assemblingEvent(long currentPosition,long readLength){
             byte[] data = new byte[0];

             if(readLength < eventMaxSize){
                 byteBuffer = ByteBuffer.allocate((int)readLength);
             }else{
                 byteBuffer = ByteBuffer.allocate(eventMaxSize);
             }
             try(SeekableByteChannel channel = Files.newByteChannel(Paths.get(file.getPath()), StandardOpenOption.READ);){
                    channel.position(currentPosition);              
                    channel.read(byteBuffer);
                    data = byteBuffer.array();
                    byteBuffer.flip();
                    byteBuffer.rewind();
                    byteBuffer.clear();
                    channel.close();
             }catch(IOException e){
                 logger.error("Io error: {}",Throwables.getStackTraceAsString(e));
             }
            byteBuffer =null;
            return data;
        }
    }
}
