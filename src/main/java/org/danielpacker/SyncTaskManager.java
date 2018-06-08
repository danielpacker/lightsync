package org.danielpacker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.*;


public class SyncTaskManager {

    private static final Logger log = LogManager.getLogger(SyncTaskManager.class);

    // Task queue
    private static BlockingQueue<SyncTask> q = new LinkedBlockingQueue<>();

    // Threads
    private ExecutorService watcherPool = Executors.newSingleThreadExecutor();
    private ExecutorService doerPool = Executors.newSingleThreadExecutor();
    private Future<?> doerFuture;
    private Future<?> watcherFuture;

    private SyncConfig config;

    public SyncTaskManager(SyncConfig config) {

        this.config = config;
    }

    public void shutDown() {

        log.info("Task manager is shutting down watch and doer workers...");
        if (watcherFuture != null)
            watcherFuture.cancel(true);
        watcherPool.shutdownNow();

        if (doerFuture != null)
            doerFuture.cancel(true);
        doerPool.shutdownNow();
        log.info("Shutdown complete.");
    }

    // Run in main thread to avoid having to manage other threads
    public void recursiveScan() {

        log.info("Launching recursive scan...");
        //pool.execute(new RecursiveScanner(config, q));
        RecursiveScanner rs = new RecursiveScanner(config, q);
        rs.run();
        //displayTasks();
        log.info("Completed recursive scan!");
    }

    public void startWatcherWorker() {

        if (watcherFuture == null || watcherFuture.isDone() || watcherFuture.isCancelled())
            try {
                watcherFuture = watcherPool.submit(new SyncWatcherWorker(config, q, true));
                //new Thread(new SyncWatcherWorker(config, q, true)).start();
            }
            catch (IOException e) {
                log.error("File exception during watching: " + e.getMessage());
            }
    }

    public void startDoerWorker() {

        if (doerFuture == null || doerFuture.isDone() || doerFuture.isCancelled())
            doerFuture = doerPool.submit(new SyncTaskDoerWorker(config, q));
        //new Thread(new SyncTaskDoerWorker(config, q));
    }

    // Leaving this here for debugging during development.
    public void displayTasks() {

        SyncTask[] tasks = new SyncTask[q.size()];
        tasks = q.toArray(tasks);
        for (SyncTask t : tasks)
            System.out.println(t);
    }
}
