# lightsync

Simple and lightweight bidirectional folder sync using Java 8. Tested on OSX 10.13.4 w/ Java 1.8.0_66 and Ubuntu 16.04.1 LTS w/ Java 1.8.0_171.

**NOTE:** This utility is not production-ready. This was a proof of concept developed in roughly 24 hours as an exercise.

## Getting Started

Just clone the github project, cd into the directory, and run bootRun to build and start the application:

* git clone https://github.com/danielpacker/lightsync.git

* cd lightsync

* ./gradlew clean build run

(you can also 'gradle jar' and run the jar file yourself if you want. it's /build/libs/lightsync-1.0-SNAPSHOT.jar)

### Overview of Design


* The root class is SyncApp, which contains main()
* main() invokes various methods via a SyncTaskManager instance, which is the application service controller.
* The TaskManager is able to control and check on the various worker threads, running in ExecutorService pools.
* There is one main-thread process, RecursiveScanner, which runs only at startup, and provides that initial non-destructive file sync. RecursiveScanner populates a shared queue with sync tasks (e.g. mkdir, rmdir, cp, rm).
* There are two workers implemented as Callable's.
- The SyncWatcherWorker uses the watch service to produce sync tasks and put them on the shared queue.
- The SyncDoerWorker consumes tasks from the queue and performs them in the same worker thread.


### Assumptions & Explanations

* The main feature of this app is that it has an extremely low memory and cpu footprint.
- Use of streams for processing directories cuts down on memory usage as opposed to stateful tables, maps, etc.
- Use of the watch service cuts down on memory and useage as compares to traditional recursive polling methods.
- I was able to general 10k files with a 1ms delay between each create and the sync was done almost as soon as the files were done being generated (a few seconds) -- performance is quite good with the caveat of overflows mentioned before.
* There are several limitations to this software
1. Only mac and Linux are supported so far, due to how each OS implements polling differently via the Java watch service.
2. Only 2 directories can be synced, configured in the config.properties file. The code could be easily generalized to an arbitrary number of pairs, left as an exercise for the reader.
3. During startup, only non-destructive operations are performed via a recursive sync that does copy/mkdir. That means if you've deleted files, that won't reflect -- only additions will be made. This may not work for every use case. Supporting statefulness between runs would require a database or serialization of data and that wasn't in scope for this exercise.
4. Since this program attempts to be as lightweight as possible, it uses the Java watch service, which can use native file system interfaces to monitor file and directory events with almost no perceptible resource usage compared with traditional polling. The downside is that as I learned, there are severe limitations on the # of simultaneous events that can be grabbed from the system buffer. More than 512 in a given directory will cause an OVERFLOW event, and I have decided not to handle this at this time. A nice feature would be to address #3 above, by adding persistence, and then when an OVERFLOW occurs, reverse to recursive polling. As-is, the caveat is that you should delay at least 1ms between file modifications in a single directory if you're doing more than 512 modifications. This suits most use-cases, but not all.
* Some initial work was put in to generate some custom exceptions, but for the most part, they don't do anything and exception handling is non-existent -- this would be an excellent thing to review.
* The use of inheritence and interfaces was basically avoided to keep the project small, and as a result, extensibility is limited in this form

### Configuration and Output

There are two properties file:

* /src/main/resources/config.properties -- configure the 2 directories primarily
* /src/main/resources/log4j.properties -- logging setup

### Prerequisites

The only prerequisites are Java 8 SE and the Java 8 SDK. Untested on other versions, but may work.

## Running the tests

gradle test (runs w/ build as well)

### Test Coverage

* Test 1: 
* Test 2:

## Misc

* stuff

## Built With

* Gradle
* JUnit
* log4j2

## Authors

* **Daniel Packer** - *Author* - [Daniel Packer](https://github.com/danielpacker)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details. Keep in mind it contains some modified code form the Oracle examples.
