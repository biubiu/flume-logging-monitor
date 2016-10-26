Log Monitor
=========
1. 准备
    - 拷贝flume-logging-monitor-client-*-bin.tar.gz 至每个需要监控的服务器，解压tar.gz压缩包。
    - 确保运行monitor的用户对监控文件有读的权限。
    - JRE1.7以上
    - 修改 ./bin/monitor 39行

      > java -classpath ${CLASSPATH} com.shawn.logging.monitor.handler.LogWatchRunScript &

      由于传输时会有大量内存IO操作，请根据情况增大java运行时的heap size，buffer size等,如:

     > java -Xms512m -Xmx1024m -classpath ${CLASSPATH}    com.shawn.logging.monitor.handler.LogWatchRunScript &

2. 配置 conf/configuration.properties
    - 配置flume agent的监听ip和端口。端口号参照flume-transfer中conf/collect-conf.properties。如,在flume-transfer中定义以下
       > collect_agent.sources.collect_source.type = avro
       > collect_agent.sources.collect_source.bind = 10.40.5.3
       > collect_agent.sources.collect_source.port = 41410

        则在conf/configuration.properties 中配置
           > monitor.agent.host=10.40.5.3
           > monitor.agent.port=41410


    -  配置agent 连接参数，最大等待时间，最大尝试次数。每次检测到变化时，都会建立网络链接，将变化的log传输到flume-transfer，在此配置网络链接的超时时间和建立链接尝试次数。
           > monitor.client.timeout=10000(单位为毫秒)
           > monitor.client.maxAttempts=2

    - 配置检查文件变化的时间间隔和监控目录(检测多个log文件用 , 号间隔)，如:
        > monitor.client.schedule.delay=2000(单位为毫秒)
        > monitor.client.schedule.watchpaths=/var/log/nginx/www.shawn.com.access_log,/var/log/nginx/www.shawn.com.error_log

      以上配置为每个两秒，查看www.shawn.com.access_log和www.shawn.com.error_log 是否变化。


    - 配置头文件位置和logStatus文件位置，如:
       > monitor.event.headerConfigPath=./conf/logHeader.json
       > monitor.client.logStatusRecordPath=./conf/log_record.json
       （具体logHeader.json和log_record.json文件格式请参照  *3.头文件和log status文件格式*）

    - 配置每次发送的eventBatch中单个event的最大大小。event为单个log改变部分的包。eventBatch由多个event组成，eventBatch的大小取决于有几个log变化，则eventBatch包含几个event。此参数限定每次发送的eventBatch中单个event的最大大小。
      >  monitor.client.event.size = 5242880 (单位为byte)


3. 头文件和log status文件格式
    * 头文件
      - 用于对特定文件配置特殊属性，格式为以被监控文件全路径为key的map结构，value为方便flume-transfer做multiplexing以及在hdfs中根据log属性建立目录结构
      - 如:  "/var/log/nginx/www.shawn.com.access_log": {
             "domain": "www", //domain可为www,job等
            "log_type": "access",//可为access,error等
            "server_type":"nginx"
            };"/var/log/nginx/www.shawn.com.access_log"为监控路径，domain,log_type,server_type为自定义的属性，可参照logHeader.json.sample。
      - 自定义的属性会以event header的方式发送给agent，以进行后续处理

    * log status
      - 在monitor正常停止时，会以json格式记录当时文件读取的大小 和其他状态

4. 测试
    * 运行网络测试，测试对应agent是否配置正常
     > ./bin/monitor test

    查看logs/client.log,如出现*Successfully finishing sending events test*,
    则网络以及配置正常

4. 启动monitor
    - 频繁的读取和传输都在内存中进行，请适量增大java运行时里的heap size
    >./bin/monitor start

    运行jps，此时应该有LogWatchRunScript 的java进程在运行，启动成功


5. 停止monitor
    > ./bin/monitor stop


