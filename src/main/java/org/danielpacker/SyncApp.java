package org.danielpacker;

/**
 * The main application class.
 * Validates the current OS, launches producers and consumers
 * via the Task Manager controller.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import org.danielpacker.SyncUtil.OS;

public class SyncApp {

    // Load up the configuration
    private static SyncConfig config = new SyncConfig();

    private static final Logger log = LogManager.getLogger(SyncApp.class);

    // Currently only Mac and Linux have been tested and are supported
    private static final List<SyncUtil.OS> supportedOS = Arrays.asList(OS.LINUX, OS.MAC);

    public static void main(String[] args) {

        log.info("Starting LightSync. Running on '" + SyncUtil.getOS().name() + "' OS.");

        if (!supportedOS.contains(SyncUtil.getOS())) {
            log.error("Exiting - LightSync does support OS: " +  SyncUtil.getOS().name());
            System.exit(1);
        }

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        // Scan recursively for changes and produce tasks
        taskMgr.recursiveScan();

        // Watch for tasks and consume/perform tasks
        taskMgr.startDoerWorker();

        // Watch for real-time events and produce tasks
        taskMgr.startWatcherWorker();
    }
}
