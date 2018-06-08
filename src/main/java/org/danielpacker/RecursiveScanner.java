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


public class RecursiveScanner implements Runnable {

    private static final Logger log = LogManager.getLogger(RecursiveScanner.class);

    private Path dir1;
    private Path dir2;
    Queue<SyncTask> q;

    RecursiveScanner(SyncConfig config, Queue<SyncTask> q) {

        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        this.q = q;
    }

    void doScan() throws IOException {

        Files.walk(dir1)
                .forEach(path -> {
                    try {
                        addTaskIfNeeded(path, dir1, dir2);
                    }
                    catch (IOException e) {
                        log.error("Problem adding tasks in recursive search: " + e.getMessage());
                    }
                });

        Files.walk(dir2)
                .forEach(path -> {
                    try {
                        addTaskIfNeeded(path, dir2, dir1);
                    }
                    catch (IOException e) {
                        log.error("Problem adding tasks in recursive search: " + e.getMessage());
                    }
                });
    }

    private void addTask(SyncTask.TYPE type, Path src, Path dst) {

        SyncTask task = new SyncTask(type, src, dst);
        q.add(task);
    }

    private void addTaskIfNeeded(Path path, Path src, Path dst) throws IOException {
        /*
        System.out.println("Path: " + path + ", Src: " + src + ", Dest: "
                + dst + ", Norm: " + SyncUtil.normalizePath(path, src)
                + ", DestPath: " + SyncUtil.srcTodestPath(path, src, dst));
        */

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


    @Override
    public void run() {

        try {
            doScan();

        }
        catch (IOException e) {
            log.error("File problem in recursive sync: " + e.getMessage());
        }
    }
}
