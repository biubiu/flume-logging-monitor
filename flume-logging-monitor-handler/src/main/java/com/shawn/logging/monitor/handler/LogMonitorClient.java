package com.shawn.logging.monitor.handler;

import java.util.List;
import java.util.Properties;

import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.api.RpcClient;
import org.apache.flume.api.RpcClientConfigurationConstants;
import org.apache.flume.api.RpcClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.base.Throwables;

@Service
public class LogMonitorClient {

    private RpcClient client;

    @Value("#{config['monitor.agent.host']}")
    private String hostname;

    @Value("#{config['monitor.agent.port']}")
    private String port;

    @Value("#{config['monitor.client.timeout']}")
    private Integer timeout;

    @Value("#{config['monitor.client.maxAttempts']}")
    private Integer maxAttempts;

    final Logger logger = LoggerFactory.getLogger(LogMonitorClient.class);

    public void init() {
        Properties properties = new Properties();
        properties.setProperty(RpcClientConfigurationConstants.CONFIG_CONNECT_TIMEOUT, String.valueOf(timeout));
        properties.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS, "h1");
        properties.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS_PREFIX + "h1",hostname + ":" + port);
        properties.setProperty(RpcClientConfigurationConstants.CONFIG_MAX_ATTEMPTS, maxAttempts+"");
        try{
            this.client = RpcClientFactory.getInstance(properties);

        }catch(Exception e){
            //e.printStackTrace();
            logger.error("can not init client instance:  " + Throwables.getStackTraceAsString(e));
            throw new FlumeException("connection refused ");
        }

    }

    public boolean sendEventsToFlume(List<Event> events) {
        if (events ==null || events.size() == 0) {
            return true;
        }
        try {
            client.appendBatch(events);
           logger.info("finished sending to flume agent, total events batch number is {}",events.size());
        } catch (EventDeliveryException e) {
            logger.error("Event delivery exception: {}",Throwables.getStackTraceAsString(e));
            cleanUp();
            return false;
        }
        return true;
    }

    public void cleanUp() {
        try{
            client.close();
            }catch(FlumeException e){
                logger.error("cannot shut down the clinet {}", Throwables.getStackTraceAsString(e));
            }
    }


    public boolean isActive() {
        if (client!=null && client.isActive()) {
            return true;
        }
        return false;
    }

}
