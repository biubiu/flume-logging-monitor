package com.shawn.logging.monitor.handler;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.apache.http.protocol.HTTP;
import org.springframework.stereotype.Service;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;

@Service
public class EventSendingTest extends LogWatcher{

    public EventSendingTest() throws Exception {
    }
    private String test = "TestTestTestTestTest";
    @Override
    public void start() throws Exception {
    //    logStatusMap = JsonFormatMapConfigHelper.loadJsonMap(logStatusMap, logStatusRecordPath,new TypeToken<LogAttribute>() {});
    //    headerConfigMap = JsonFormatMapConfigHelper.loadJsonMap(headerConfigMap, headerConfigPath,new TypeToken<Map<String, String>>() {});
        logger.info("================================================");
        logger.info("the event sending test started ");
    }

    @Override
    public void process() {
        List<Event> events = Lists.newLinkedList();
       for (int i= 0 ; i < 10; i ++) {
        Map<String, String> header = Maps.newHashMap(new ImmutableMap.Builder<String, String>().put(HttpHeaders.LOCATION, IP)
                .put(FILE_NAME, "test").put(HTTP.CONTENT_TYPE, System.getProperty("file.encoding"))
                 .put("domain","test").put("log_type","test").put("server_type","test").put("timestamp", new Date().getTime()+"")
                 .build());

            Event event = EventBuilder.withBody(test, Charsets.UTF_8, header);
            events.add(event);
        }

        logger.info("finished assemblng events: " + events.size());
        client.init();
        if(client.sendEventsToFlume(events)){
            logger.info("Successfully findished sending events test");
        }else{
            logger.info("Events sending test failed....");
        }
        logger.info("================================================");
        client.cleanUp();

    }

}
