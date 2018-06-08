package org.danielpacker;

/**
 * This class started as an oracle example class, and was retrofitted to be a Callable
 * along with a bunch of logic for queing tasks based on incoming events.
 */

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;


public class SyncWatcherWorker implements Callable<Void> {

    private static final Logger log = LogManager.getLogger(SyncWatcherWorker.class);

    SyncConfig config;
    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
    private Path dir1;
    private Path dir2;
    BlockingQueue<SyncTask> q;
    Map<Path, Integer> ignoreNextCreate = new HashMap<>();
    Map<Path, Integer> ignoreNextModify = new HashMap<>();
    Map<Path, Integer> ignoreNextDelete = new HashMap<>();

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher,
                new WatchEvent.Kind[] {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY},
                SensitivityWatchEventModifier.HIGH);

        log.debug("Registered to watch " + dir);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                log.debug("Registered to watch: " + dir);
            } else {
                if (!dir.equals(prev)) {
                    log.debug("Updated registration: " + prev + " -> " + dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    SyncWatcherWorker(SyncConfig config, BlockingQueue<SyncTask> q, boolean recursive) throws IOException {
        this.config = config;
        this.q = q;
        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;
        Path[] dirs = { dir1, dir2 };

        for (Path dir : dirs) {
            if (recursive) {
                log.info("Recursively Watching " + dir + " for changes...");
                registerAll(dir);
            } else {
                register(dir);
            }
        }

        // enable trace after initial registration
        this.trace = true;
    }

    private boolean taskIsNeeded(String eventType, Path path, Path equivPath) {

        // Ignore this path until another task acts on it
        if (eventType.equals("ENTRY_CREATE")) {

            if (SyncUtil.getOS() == SyncUtil.OS.LINUX
                    && !Files.isDirectory(path)) {
                log.debug("IGNORED (ALWAYS) CREATE ON LINUX FILES for path: " + path);
                return false;
            }

            if (ignoreNextCreate.getOrDefault(path, 0) > 0) {
                ignoreNextCreate.put(path, ignoreNextCreate.get(path) - 1);
                log.debug("IGNORED SUBSEQUENT CREATE FOR path: " + path);
                return false;
            } else {
                log.debug("WILL IGNORE SUBSEQUENT CREATE FOR equivPath: " + equivPath);
                ignoreNextCreate.put(equivPath, 1);
            }

        } else if (eventType.equals("ENTRY_MODIFY")) {

            if (ignoreNextModify.getOrDefault(path, 0) > 0) {
                ignoreNextModify.put(path, ignoreNextModify.get(path) - 1);
                log.debug("IGNORED SUBSEQUENT MODIFY FOR path: " + path);
                return false;
            } else {
                log.debug("WILL IGNORE SUBSEQUENT MODIFY FOR equivPath: " + equivPath);
                ignoreNextModify.put(equivPath, ignoreNextModify.getOrDefault(equivPath, 0) + 1);

                //System.out.println("OS: " + SyncUtil.getOS().name());
                if (SyncUtil.getOS() == SyncUtil.OS.LINUX) {
                    if (Files.exists(equivPath)) {
                        ignoreNextDelete.put(equivPath, ignoreNextDelete.getOrDefault(equivPath, 0) + 1);
                        log.debug("WILL IGNORE SUBSEQUENT DELETE ON LINUX FILES for equivPath: " + equivPath);
                    }
                }
            }
        }
        else if (eventType.equals("ENTRY_DELETE")) {

            if (ignoreNextDelete.getOrDefault(path, 0) > 0) {
                ignoreNextDelete.put(path, ignoreNextDelete.get(path) - 1);
                log.debug("IGNORED SUBSEQUENT DELETE FOR path: " + path);
                return false;
            }
        }
        return true;
    }


    private void addTask(String eventType, Path path, Path equivPath) throws IOException {

        switch (eventType) {

            case "ENTRY_CREATE":
                if (Files.isDirectory(path))
                    q.add(new SyncTask(SyncTask.TYPE.MKDIR, path, equivPath));
                else
                    q.add(new SyncTask(SyncTask.TYPE.CP, path, equivPath));
                break;
            case "ENTRY_MODIFY":
                if (! Files.isDirectory(path))
                    q.add(new SyncTask(SyncTask.TYPE.CP, path, equivPath));
                break;
            case "ENTRY_DELETE":
                if (Files.exists(equivPath))
                    if (Files.isDirectory(equivPath))
                        q.add(new SyncTask(SyncTask.TYPE.RMDIR, path, equivPath));
                    else
                        q.add(new SyncTask(SyncTask.TYPE.RM, path, equivPath));
                break;
        }

    }

    /**
     * Process all events for keys queued to the watcher
     */

    void processEvents() throws SyncOverflowException {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();

                // Sleep to avoid potential duplicate events on some OS's
                //Thread.sleep( 50 );
            } catch (InterruptedException e) {
                log.error("Watcher interrupted during take()" + e.getMessage());
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                log.error("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // Due to a hard-coded limit of 512 queued events
                //  in AbstractKeyWatcher, overflows are fairly common.
                // If >512 files are modified simultaneously in one wached
                //  directory, it will overflow and lose events. The comments
                //  suggest that this maybe tunable in a future version of Watch Service.
                //  As of Java 9, the value is hard-coded.
                if (kind == OVERFLOW) {
                    log.error("OVERFLOW!!!");
                    throw new SyncOverflowException("OVERFLOWED!!!");
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                log.debug(String.format("%s: %s", event.kind().name(), child));

                //System.out.println("Type: " + eventType + ", Path: " + path);
                Path equivPath;
                if (child.toString().indexOf(dir1.toString()) == 0)
                    equivPath = SyncUtil.srcTodestPath(child, dir1, dir2);
                else
                    equivPath = SyncUtil.srcTodestPath(child, dir2, dir1);

                try {
                    if (taskIsNeeded(event.kind().name(), child, equivPath))
                        addTask(event.kind().name(), child, equivPath);
                }
                catch (IOException e) {
                    log.error("File exception during watching: " + e.getMessage());
                }

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException e) {
                        log.error("File exception while registering dirs: " + e.getMessage());
                    }
                }

            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }


    @Override
    public Void call() throws SyncOverflowException {
        processEvents();
        return null;
    }
}
