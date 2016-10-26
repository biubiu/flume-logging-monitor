package com.shawn.logging.monitor.handler;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class LogWatchRunScript {
    public static void main(String[] args) throws Exception {
          ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
//          LogWatcher service = context.getBean(LogWatcherByWatchEvent.class);
          LogWatcher service = context.getBean(LogWatcherBySchedule.class);
          service.start();
          service.process();
    }
}
