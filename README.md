# lightsync

Simple and lightweight bidirectional folder sync using Java 8. Tested on OSX 10.13.4 w/ Java 1.8.0_66 and Ubuntu 16.04.1 LTS w/ Java 1.8.0_171.

**NOTE:** This utility is not production-ready. This was a proof of concept developed in roughly 24 hours as an exercise.

## Getting Started

Clone the github project, cd into the directory, and 'gradle clean build run' to start the application:

```
git clone https://github.com/danielpacker/lightsync.git

cd lightsync

gradle clean build run
````

(you can also 'gradle jar' and run the jar file yourself if you want. it's /build/libs/lightsync-1.0-SNAPSHOT.jar)

### Overview of Design

* The root class is SyncApp, which contains main()
* main() invokes various methods via a SyncTaskManager instance, which is the application service controller.
* The TaskManager is able to control and check on the various worker threads, running in ExecutorService pools.
* There is one main-thread process, RecursiveScanner, which runs only at startup, and provides that initial non-destructive file sync. RecursiveScanner populates a shared queue with sync tasks (e.g. mkdir, rmdir, cp, rm).
* There are two workers implemented as Callable's.
  - The SyncWatcherWorker uses the watch service to produce sync tasks and put them on the shared queue.
  - The SyncDoerWorker consumes tasks from the queue and performs them in the same worker thread.
* In the SyncWatcherWorker, there is fairly complex logic to implement handling of the various file system events that mac and linux generate when files are modified. The use of maps with counters to track what events should be ignored allows for some statefulness in processing events out of order (order is not guaranteed in watch service). The logic would need to be customized to suport other operating systems. This is one more big drawback to the watch service, which is otherwise very promising.


### Assumptions & Explanations

* The main feature of this app is that it has an extremely low memory and cpu footprint.
  - Use of streams for processing directories cuts down on memory usage as opposed to stateful tables, maps, etc.
  - Use of the watch service cuts down on memory and useage as compares to traditional recursive polling methods.
  - I was able to general 10k files with a 1ms delay between each create and the sync was done almost as soon as the files were done being generated (a few seconds) -- performance is quite good with the caveat of overflows mentioned below.
* There are several limitations to this software
  1. Only mac and Linux are supported so far, due to how each OS implements polling differently via the Java watch service.
  2. Only 2 directories can be synced, configured in the config.properties file. The code could be easily generalized to an arbitrary number of pairs, left as an exercise for the reader.
  3. During startup, only non-destructive operations are performed via a recursive sync that does copy/mkdir. That means if you've deleted files, that won't reflect -- only additions will be made. This may not work for every use case. Supporting statefulness between runs would require a database or serialization of data and that wasn't in scope for this exercise.
  4. Since this program attempts to be as lightweight as possible, it uses the Java watch service, which can use native file system interfaces to monitor file and directory events with almost no perceptible resource usage compared with traditional polling. The downside is that as I learned, there are severe limitations on the # of simultaneous events that can be grabbed from the system buffer. More than 512 in a given directory will cause an OVERFLOW event, and I have decided not to handle this at this time. A nice feature would be to address #3 above, by adding persistence, and then when an OVERFLOW occurs, reverse to recursive polling. As-is, the caveat is that you should delay at least 1ms between file modifications in a single directory if you're doing more than 512 modifications. This suits most use-cases, but not all.
* Some initial work was put in to generate some custom exceptions, but for the most part, they don't do anything and exception handling is non-existent -- this would be an excellent thing to review.
* The use of inheritence and interfaces was basically avoided to keep the project small, and as a result, extensibility is limited in this form.
* No attempt was made at handling links or other exotic files.
* The code is a bit messy and could use cleanup if put to use in the future (e.g. lots of conversion between String's and Path's and File's that's probably not really necessary)

### Configuration and Output

There are two properties file:

* /src/main/resources/config.properties -- configure the 2 directories primarily
* /src/main/resources/log4j.properties -- logging setup

The output of the logger will tell you when the program is starting, stopping or performing a sync operation. To see all the inner processes in action, set filter.threshold.level = debug in your log4j.properties file. You'll see the logic for ignoring events, and other things.

When it starts it should look like this:

```
Daniels-MacBook-Pro:lightsync danielpacker$ gradle build run
Starting a Gradle Daemon (subsequent builds will be faster)

> Task :run
2018-06-08 10:12:38 INFO  SyncConfig:29 - Configuration loaded from: src/main/resources/config.properties
2018-06-08 10:12:38 INFO  SyncApp:28 - Starting LightSync. Running on 'MAC' OS.
2018-06-08 10:12:38 INFO  SyncTaskManager:51 - Launching recursive scan...
2018-06-08 10:12:38 INFO  SyncTaskManager:56 - Completed recursive scan!
2018-06-08 10:12:38 INFO  SyncWatcherWorker:97 - Recursively Watching /tmp/lightsync/dir1 for changes...
2018-06-08 10:12:38 INFO  SyncWatcherWorker:97 - Recursively Watching /tmp/lightsync/dir2 for changes...
```

### Prerequisites

The only prerequisites are Java 8 SE and the Java 8 SDK. Untested on other versions, but may work. Any compilation dependencies will be pulled in via MavenCentral/gradle. The only external dependency is log4j.

## Running the tests

gradle test (runs w/ build as well)

### Test Coverage

As of this writing, these are the tests included:
  1. clean up directories, create base temp dirs
  2. check that dirs are clean
  3. create a file in both dirs, startup the recursive sync and make sure both get synced (checks both dirs)
  4. same as above but with a modification of a file in each dir
  5. create a file in both dirs, startup the watcher and make sure both get synced
  6. same as above but for modification
  7. create a dir in both dirs, startup the watcher and make sure both get synced
  8. same as above but remove dirs instead

It's worth mentioning that in each test the Task Manager is asked to stop the worker threads at the end of each test so that the next test can start them back up to make each test stand-alone.

## FAQ

**How do I add support for my OS?**

As mentioned above, only mac and Linux are currently supported, but you could add your OS to the list of supported OS's in SyncApp.java, and implement additional processing logic in SyncWatcherWorker to make it happen. With luck other OS's such as Windows would be very easy to port. 

## Built With

* Gradle
* JUnit
* log4j2

## Authors

Feel free to send me any questions about this code.

* **Daniel Packer** - *Author* - [Daniel Packer](https://github.com/danielpacker)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details. Keep in mind it contains some modified code form the Oracle examples.
