package com.shawn.logging.monitor.handler;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class EventSendingTestScript {
    public static void main(String[] args) throws Exception{
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        LogWatcher service = context.getBean(EventSendingTest.class);
        service.start();
        service.process();
    }
}
