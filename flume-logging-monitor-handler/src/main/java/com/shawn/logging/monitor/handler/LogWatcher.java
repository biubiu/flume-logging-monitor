package com.shawn.logging.monitor.handler;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;

public abstract class LogWatcher {

      public ExecutorService executorService;
        public ScheduledExecutorService scheduledExecutorService;



        @Value("#{config['monitor.client.timeout']}")
        public Integer timeout;

        @Value("#{config['monitor.client.schedule.delay']}")
        public int delay;

        @Value("#{config['monitor.client.schedule.watchpaths'].split(',')}")
        public List<String> watchFilePaths;

        @Value("#{config['monitor.client.logStatusRecordPath']}")
        public String logStatusRecordPath;

        @Value("#{config['monitor.event.headerConfigPath']}")
        public String headerConfigPath;

        @Value("#{config['monitor.log.suffix.date']}")
        public String dateFormat;

        @Value("#{config['monitor.client.event.size']}")
        public int eventMaxSize;

         Map<String, LogAttribute> logStatusMap ;
         Map<String, Map<String, String>> headerConfigMap;


        @Autowired
        LogMonitorClient client;


        final Logger logger = LoggerFactory.getLogger(LogWatcher.class);
        static final String FILE_NAME = "file_name";
        static final String CONTENT_TYPE = System.getProperty("file.encoding");
        static final String FILE_SEPARATOR = System.getProperty("file.separator");
        final String IP = getLocalHostLANAddress().getHostAddress();
        public LogWatcher() throws Exception {}

        public Map<String, String> buildHeader(String path) {
            Map<String, String> header = Maps.newHashMap(new ImmutableMap.Builder<String, String>().put(HttpHeaders.LOCATION, IP)
                    .put(FILE_NAME, path.substring(path.lastIndexOf(FILE_SEPARATOR) + 1)).put(HTTP.CONTENT_TYPE, CONTENT_TYPE)
                    .build());
            if (headerConfigMap.containsKey(path)) {
                Map<String, String> logSpecifiedAttr = headerConfigMap.get(path);
                Set<String> keys = logSpecifiedAttr.keySet();
                for (String key : keys) {
                    header.put(key, logSpecifiedAttr.get(key));
                }
            }
            headerConfigMap.put(path, header);
            return header;
        }

        public void rollBackLogStatus() {
            logger.warn("roll back file status ------");
            Set<String> logPaths = logStatusMap.keySet();
            for (String log : logPaths) {
                LogAttribute logAttribute = logStatusMap.get(log);
                long current = logAttribute.getLastModifiedSize();
                logAttribute.setCurrentSize(current);
            }
        }

    public abstract void start() throws Exception;

    public abstract void process();

    public void shutdown() {
        executorService.shutdown();
        scheduledExecutorService.shutdown();
    }

    private InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            // Iterate all NICs (network interface cards)...
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {

                        if (inetAddr.isSiteLocalAddress()) {
                            // Found non-loopback site-local address. Return it immediately...
                            return inetAddr;
                        }
                        else if (candidateAddress == null) {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local address is not subsequently found...
                            candidateAddress = inetAddr;
                            // Note that we don't repeatedly assign non-loopback non-site-local addresses as candidates,
                            // only the first. For subsequent iterations, candidate will be non-null.
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                // We did not find a site-local address, but we found some other non-loopback address.
                // Server might have a non-site-local address assigned to its NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                // Return this non-loopback candidate address...
                return candidateAddress;
            }
            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        }
        catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
