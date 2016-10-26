Log Monitor
=========

logging client is used to capture the changed text and send them by avro client in the format of flume event.

In the conf folder , there are configurations that you can work with .
-  *configuration.propreties* is the basic config , like the listening agent host and port ,the file that you want to watch the changes and send these to flume .etc .
- *logHeader.json.sample* is the sample log header file that indicates the format of customized event header for certain watching file .The path of such header config can be specified by  *monitor.event.headerConfigPath* in configuration.properties
- everytime the client shuts down ,a hook method will automatically execute in order to stash the current status of the watching paths . When the client starts again ,it will first check the log status file ,if there is any , the status of the watching paths will be loaded and be further processed .

To start the client:
>./bin/logWatcherClient start 

Abstraction:
The client consists of three phases .
- Initialization phase
    
   Firstly, The client loads all static configuration from *configuration.properties*.If the the log status is pre stashed , it will load the log status file and map each log status to the current watching path .If the customized header file exists ,the client will also map the header to the current watching path.

- Process phase

  During the process phase ,the client will firstly initialized a schedule thread to in order to do the job at fixed time interval and a thread pool with each thread assigning a task of checking the log changes on given path .When the changes occurs , the thread pool will return the result set of changes (List<Future<Event>>) to the schedule thread ,eventually the schedule thread will initilize the avro client and send the event batch to the target agent . If the sending procedure is unsuccessful , the client will roll back the status of the watching path back to the status before the patches being sent. 
  
- Shutdown phase

 If the client is shut down by keyboard interruption or kill -5 . The status of current watching path will be logged to a file by a shut down hook method .Next time the client start , it will read and load the path status from that file .
 
