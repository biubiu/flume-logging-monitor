package com.shawn;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.api.RpcClient;
import org.apache.flume.api.RpcClientFactory;
import org.apache.flume.event.EventBuilder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class App {
    public static void main(String[] args) throws Exception {
        MyRpcClientFacade client = new MyRpcClientFacade();
        client.init("10.40.5.3", 41410);
        for(int k=0;k<50;k++){
            client.sendDataToFlume(k);
            System.out.println("--------111231231231=================");
        }
        client.cleanUp();
    }
}

class MyRpcClientFacade {

    private RpcClient client;

    private String hostname;

    private int port;

    public void init(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;

//        Properties props = new Properties();
//        props.put("client.type", "default_loadbalance");
//        props.put("hosts", "h1 h2");
//        String host1 = "localhost:41411";
//        String host2 = "localhost:41410";
//        props.put("hosts.h1", host1);
//        props.put("hosts.h2", host2);


        this.client = RpcClientFactory.getDefaultInstance(hostname, port);

    }

    public void sendDataToFlume(int k) throws UnknownHostException {

        List<Event> list = Lists.newArrayList();
        StringBuilder builder = new StringBuilder();
        for(int i=0;i<20000;i++){
            String data = "10.40.5.253 [27/Feb/2014:15:43:49 +0800] \"GET /career/index?from=shawn HTTP/1.1\" 302 448 \"http://www.shawn.com/home\" \"-\" \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/30.0.1599.101 Safari/537.36\""+i + "=="+k;
            builder.append(data);
        }

        try {
            Event event = EventBuilder.withBody(builder.toString(), Charset.forName("UTF-8"));
            Map<String,String> headerMap = Maps.newHashMap();
            headerMap.put("server_type","nginx");
            headerMap.put("server_host","DEFAULT");
            headerMap.put("filename","haproxy.log");
            headerMap.put("timestamp",new Date().getTime()+"");
            event.setHeaders(headerMap);
            list.add(event);
            client.appendBatch(list);
        } catch (EventDeliveryException e) {
            client.close();
            client = null;
            client = RpcClientFactory.getDefaultInstance(hostname, port);
        }
    }

    public void sendBigDataToFlume(String file,String header) throws Exception {
        Path path = Paths.get(file);
        Event event = EventBuilder.withBody(Files.readAllBytes(path));
        Map<String,String> headerMap = Maps.newHashMap();
        headerMap.put("server_host",header);
        headerMap.put("timestamp",new Date().getTime()+"");
        headerMap.put("filename",path.getFileName().toString());
        InetAddress addr = InetAddress.getLocalHost();
        String ip=addr.getHostAddress();
        headerMap.put("host",ip);

        event.setHeaders(headerMap);

        try {
            client.append(event);
        } catch (EventDeliveryException e) {
            client.close();
            client = null;
            client = RpcClientFactory.getDefaultInstance(hostname, port);
        }
    }

    public void cleanUp() {
        // Close the RPC connection
        client.close();
    }
}
