package org.danielpacker;

/**
 * A service controller class that starts up, shuts down, or monitors workers.
 * Maintains "futures" and thread pools for each worker type for interaction.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;


public class SyncTaskManager {

    private static final Logger log = LogManager.getLogger(SyncTaskManager.class);
    private static BlockingQueue<SyncTask> q = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService checkOverflowPool = Executors.newScheduledThreadPool(1);
    private final ExecutorService recursivePool = Executors.newSingleThreadExecutor();
    private final ExecutorService watcherPool = Executors.newSingleThreadExecutor();
    private final ExecutorService doerPool = Executors.newSingleThreadExecutor();
    private Future<?> recursiveFuture;
    private Future<?> doerFuture;
    private Future<?> watcherFuture;
    private final SyncConfig config;
    private static boolean overflowed = false;

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

    // Run in main thread for initial scan/catchup mode.
    public void recursiveScan() {

        // Scan for file changes
        new RecursiveScanner(config, q).doScan();

        // Perform catch-up file operations
        new SyncTaskDoerWorker(config, q).doTasks(true);
    }

    public void startWatcherWorker() {

        if (watcherFuture == null || watcherFuture.isDone() || watcherFuture.isCancelled())
            watcherFuture = watcherPool.submit(new SyncWatcherWorker(config, q, true));

        // Periodically check for an OVERFLOW exception in the watcher
        checkForOverflow();
    }

    public void startDoerWorker() {

        if (doerFuture == null || doerFuture.isDone() || doerFuture.isCancelled())
            doerFuture = doerPool.submit(new SyncTaskDoerWorker(config, q));
    }

    public void checkForOverflow() {

        // periodically check whether the watcher thread experienced an OVERFLOW
        checkOverflowPool.scheduleAtFixedRate(()->{
            log.trace("checking if watcher future done");
            if (watcherFuture.isDone())
                log.trace("watcher future is done");
                try {
                    watcherFuture.get();
                } catch (ExecutionException e) {
                    Throwable ex = e.getCause();
                    if (ex instanceof SyncOverflowException) {
                        log.info("Sync OVERFLOW captured in task mgr. Not yet implemented.");
                        this.overflowed = true;
                        System.exit(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
        }, 0, 5000, TimeUnit.MILLISECONDS);
    }



    // Leaving this here for debugging during development.
    public void displayTasks() {

        SyncTask[] tasks = new SyncTask[q.size()];
        tasks = q.toArray(tasks);
        for (SyncTask t : tasks)
            System.out.println(t);
    }
}
