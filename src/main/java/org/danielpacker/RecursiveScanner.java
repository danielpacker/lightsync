package org.danielpacker;

/**
 * This can be used in a thread, but that's not really necessary.
 * Performs a recursive sync between two folders and generates sync tasks.
 * Runs on startup and when done, the watcher worker takes over producing tasks.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.Callable;


public class RecursiveScanner implements Callable<Void> {

    private static final Logger log = LogManager.getLogger(RecursiveScanner.class);
    private final SyncStats stats;
    private final Path dir1;
    private final Path dir2;
    private final Queue<SyncTask> q;

    RecursiveScanner(SyncConfig config, Queue<SyncTask> q, SyncStats stats) {

        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        this.q = q;
        this.stats = stats;
    }

    void doScan() {

        log.debug("starting recursive scan...");

        try {
            Files.walk(dir1)
                    .forEach(path -> {
                        try {
                            addTaskIfNeeded(path, dir1, dir2);
                        } catch (IOException e) {
                            log.error("Problem adding tasks in recursive search: " + e.getMessage());
                        }
                    });

            Files.walk(dir2)
                    .forEach(path -> {
                        try {
                            addTaskIfNeeded(path, dir2, dir1);
                        } catch (IOException e) {
                            log.error("Problem adding tasks in recursive search: " + e.getMessage());
                        }
                    });
        }
        catch (IOException e) {
            log.error("IO problem in recursive sync: " + e.getMessage());
            log.error("Stacktrace:", e);
        }

        log.debug("recursive scan complete.");
    }

    private void addTask(SyncTask.TYPE type, Path src, Path dst) {

        SyncTask task = new SyncTask(type, src, dst);
        q.add(task);
        stats.setNumTasksQueued(stats.getNumTasksQueued()+1);
    }

    private void addTaskIfNeeded(Path path, Path src, Path dst) throws IOException {

        Path destPath = SyncUtil.srcTodestPath(path, src, dst);

        // Avoid trying to process root folders from config
        if (destPath.toString().equals(dir1.toString()) ||
                destPath.toString().equals((dir2.toString()))) {

            log.debug("SKIPPING ROOT DIR IN WALK (" + destPath + ")");
            return;
        }

        if (Files.exists(destPath)) {
            long pathTime = Files.getLastModifiedTime(path).toMillis();
            long destTime = Files.getLastModifiedTime(destPath).toMillis();

             // Only care about newer files in this directory, not older, and not folders.
             // Since this same check runs on the other directory, only need one direction at a time.
             if (pathTime > destTime && !Files.isDirectory(path))
                addTask(SyncTask.TYPE.CP, path, destPath);
        }
        else {
            if (Files.isDirectory(path))
                addTask(SyncTask.TYPE.MKDIR, path, destPath);
            else
                addTask(SyncTask.TYPE.CP, path, destPath);
        }
    }

    public Void call() {

        doScan();
        return null;
    }
}
