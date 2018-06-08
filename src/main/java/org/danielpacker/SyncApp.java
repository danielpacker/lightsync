package org.danielpacker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SyncApp {

    // Load up the configuration
    private static SyncConfig config = new SyncConfig();

    private static final Logger log = LogManager.getLogger(SyncApp.class);

    public static void main(String[] args) {

        log.info("Starting LightSync. Running on '" + SyncUtil.getOS().name() + "' OS.");

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
