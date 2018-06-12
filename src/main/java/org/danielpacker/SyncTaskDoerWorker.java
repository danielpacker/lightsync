package org.danielpacker;

/**
 * Consumes sync tasks from the queue and performs file operations
 * to fulfill those tasks.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import static java.nio.file.StandardCopyOption.*;


public class SyncTaskDoerWorker implements Callable<Void> {

    private static final Logger log = LogManager.getLogger(SyncTaskDoerWorker.class);
    private final SyncStats stats;
    private final BlockingQueue<SyncTask> q;
    private final SyncConfig config;
    private final Path dir1;
    private final Path dir2;

    SyncTaskDoerWorker(SyncConfig config, BlockingQueue<SyncTask> q, SyncStats stats) {

        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        this.q = q;
        this.config = config;
        this.stats = stats;
    }

    private void doCP(SyncTask cpTask) throws IOException {
        Files.copy(cpTask.getSrc(), cpTask.getDst(), REPLACE_EXISTING, COPY_ATTRIBUTES);
    }

    private void doRM(SyncTask rmTask) throws IOException {
        Files.deleteIfExists(rmTask.getDst());
    }

    private void doMKDIR(SyncTask mkdirTask) throws IOException {
        if (!Files.exists(mkdirTask.getDst()))
            Files.createDirectory(mkdirTask.getDst());
    }

    private void doRMDIR(SyncTask rmdirTask) throws IOException {

        Files.walk(rmdirTask.getDst())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .peek(path -> log.info("RMDIR recursively deleting: " + path))
                .forEach(File::delete);
    }

    // Sanity check any path we're about to operate on and
    //  make sure the root of the path is from the config.
    private boolean taskPathsInConfig(SyncTask task) {

        boolean inConfig = true;

        if (
                task.getSrc().toString().indexOf(config.getDir1()) != 0 &&
                        task.getSrc().toString().indexOf(config.getDir2()) != 0
                ) return false;

        if (
                task.getDst().toString().indexOf(config.getDir1()) != 0 &&
                        task.getDst().toString().indexOf(config.getDir2()) != 0
                ) return false;

        return true;
    }

     void doTasks(boolean stopWhenEmpty) {

        // Wrap all the code in try/catch for Interruption/cancellation.
        try {
            while (true) {

                if (stopWhenEmpty && q.isEmpty()) {
                    log.debug("returning from stopWhenEmpty mode");
                    return;
                }

                SyncTask task = q.take();
                log.info("Doing task: " + task);

                if (!taskPathsInConfig(task)) {
                    log.error("Attempted to modify file dor dir outside config params!");
                    log.error("Offending task: " + task);
                    System.exit(1);
                }

                switch (task.getType()) {
                    case CP:
                        doCP(task);
                        break;
                    case RM:
                        doRM(task);
                        break;
                    case MKDIR:
                        doMKDIR(task);
                        break;
                    case RMDIR:
                        doRMDIR(task);
                        break;
                }

                stats.setNumTasksCompleted(stats.getNumTasksCompleted()+1);
            }
        } catch (IOException e) {
            log.error("File handling exception while doing task!: " + e.getMessage());
            log.error(e);
            return;
        } catch (InterruptedException e) {
            log.debug("SyncTaskDoer thread interrupted. Stopping.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Void call() {
        doTasks(false);
        return null;
    }
}
