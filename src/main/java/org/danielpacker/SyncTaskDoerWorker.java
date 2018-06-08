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

    private Path dir1;
    private Path dir2;
    BlockingQueue<SyncTask> q;
    SyncConfig config;

    SyncTaskDoerWorker(SyncConfig config, BlockingQueue<SyncTask> q) {

        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        this.q = q;
        this.config = config;
    }

    void doCP(SyncTask cpTask) throws IOException {
        Files.copy(cpTask.getSrc(), cpTask.getDst(), REPLACE_EXISTING, COPY_ATTRIBUTES);
    }

    void doRM(SyncTask rmTask) throws IOException {
        Files.deleteIfExists(rmTask.getDst());
    }

    void doMKDIR(SyncTask mkdirTask) throws IOException {
        if (! Files.exists(mkdirTask.getDst()))
            Files.createDirectory(mkdirTask.getDst());
    }

    void doRMDIR(SyncTask rmdirTask) throws IOException {

        Files.walk(rmdirTask.getDst())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .peek(path -> log.info("RMDIR recursively deleting: " + path))
                .forEach(File::delete);
    }


    // Sanity check any path we're about to operate on and
    //  make sure the root of the path is from the config.
    boolean taskPathsInConfig(SyncTask task) {

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

    void doTasks() throws IOException {

        try {
            while (true) {

                SyncTask task = q.take();
                log.info("Doing task: " + task);

                if (! taskPathsInConfig(task)) {
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
            }
        }
        catch (InterruptedException e) {
            log.error("SyncTaskDoer thread interrupted: " + e.getMessage());
        }
    }


    @Override
    public Void call() {

        try {
            doTasks();
        }
        catch (IOException e) {
            log.error("File problem while doing task: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }
}
