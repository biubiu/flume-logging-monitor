package com.shawn.logging.monitor.handler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
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
public class LogWatcherByWatchEvent extends LogWatcher{
    public LogWatcherByWatchEvent() throws Exception {
    }

    public void start() throws Exception {
        logStatusMap = JsonFormatMapConfigHelper.loadJsonMap(logStatusMap, logStatusRecordPath, new TypeToken<LogAttribute>() {});
        headerConfigMap = JsonFormatMapConfigHelper.loadJsonMap(headerConfigMap, headerConfigPath, new TypeToken<Map<String, String>>() {});
        executorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("file-change-thread").build());
        logger.info("the log watcher started successfully ");
    }

    public void process() {
        for (String path : watchFilePaths) {
            File file = new File(path);
            LogAttribute logAttribute = logStatusMap.containsKey(path) ? logStatusMap.get(path) : new LogAttribute.Builder().logPath(path)
                    .lastModifiedTime(file.lastModified()).lastModifiedSize(0L).currentSize(file.length()).build();
            logStatusMap.put(logAttribute.getLogPath(), logAttribute);
            buildHeader(path);
        }
        executorService.submit(new WatcherRunnable(watchFilePaths,delay));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                executorService.shutdown();
                logger.info("storing log processing to the temp file");
                JsonFormatMapConfigHelper.writeJsonMap(logStatusMap, logStatusRecordPath, new TypeToken<LogAttribute>() {
                });
                ;
            }
        });
    }





    private final class WatcherRunnable implements Runnable {

        final FileSystem fs = FileSystems.getDefault();
        private WatchService ws;

        private List<String> filenames;
        final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
        int delay ;
        public WatcherRunnable(List<String> paths,int delay) {
            try{
                ws = fs.newWatchService();
            }catch(IOException e){
                logger.error("error on creating watch service,{} ",Throwables.getStackTraceAsString(e));
            }
            filenames = Lists.newArrayListWithCapacity(paths.size());
            for (String path : paths) {
                reg(fs.getPath(path.substring(0,path.lastIndexOf(System.getProperty("file.separator")))), keys, ws);
                filenames.add(path);
            }
            this.delay = delay;
        }

        public void run() {
            logger.info("watcher start...");
            while (Thread.interrupted() == false) {
                WatchKey key;
                try {
                    key = ws.poll(delay, TimeUnit.MILLISECONDS);
                    logger.debug("wait millisec: " + delay);
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    logger.error(Throwables.getStackTraceAsString(e));
                    break;
                }
                if (key != null) {
                    List<Event> events = Lists.newArrayList();
                    Path path = keys.get(key);
                    for (WatchEvent<?> i : key.pollEvents()) {
                        WatchEvent<Path> event = cast(i);
                        WatchEvent.Kind<Path> kind = event.kind();
                        Path name = event.context();
                        Path fullPath = path.resolve(name);
                        logger.info(String.format("changed file : %s: %s%n", kind.name(), fullPath));
                        if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY) && filenames.contains(fullPath.toString())){
                            logger.info(String.format("event : %s %d %n", fullPath,fullPath.toFile().length()));
                            events.add(transformChangedFileToEvent(fullPath));
                        }
                    }	if(!events.isEmpty())
                            processingEvent(events);
                    if (key.reset() == false) {
                        logger.error("{} is invalid ",key);
                        keys.remove(key);
                        if (keys.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }


        void reg(Path path, Map<WatchKey, Path> keys, WatchService ws){
            try{
                WatchKey key = path.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                keys.put(key, path);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unchecked")
        <T> WatchEvent<T> cast(WatchEvent<?> event) {
            return (WatchEvent<T>) event;
        }

        Event transformChangedFileToEvent(Path path){
            LogAttribute logAttribute = logStatusMap.get(path.toString());
            File file = path.toFile();
            ByteBuffer byteBuffer;
            Event event = null;
               if (file.length() < logAttribute.getLastModifiedSize()) {
                   logAttribute.setCurrentSize(0L);
               }
               try {
                   int readLength = (int) (file.length() - logAttribute.getCurrentSize());

                   SeekableByteChannel seekableByteChannel = Files.newByteChannel(Paths.get(file.getPath()), StandardOpenOption.READ);
                   seekableByteChannel.position(logAttribute.getCurrentSize());
                   byteBuffer = ByteBuffer.allocate(readLength);
                   while (seekableByteChannel.read(byteBuffer) > 0) {
                       byteBuffer.flip();
                       event = EventBuilder.withBody(byteBuffer.array(), headerConfigMap.get(path.toString()));
                       byteBuffer.clear();
                   }
                   logAttribute.setLastModifiedTime(file.lastModified());
                   logAttribute.setLastModifiedSize(logAttribute.getCurrentSize());
                   logAttribute.setCurrentSize(file.length());
               } catch (IOException e) {
                   logger.error(Throwables.getStackTraceAsString(e));
               }
               return event;
           }


        private void processingEvent(List<Event> events){
               synchronized (LogMonitorClient.class) {
                   try {
                       client.init();
                   } catch (FlumeException e) {
                       // shutdown();
                       rollBackLogStatus();
                       logger.error("cannot establish connection to flume agent , please check the network .");
                       return;
                   }
                   logger.info("established the connection , prepare to send the event batch");
                   if (!client.sendEventsToFlume(events)) {
                       rollBackLogStatus();
                   } 
                   client.cleanUp();
               }
        }
    }

}
