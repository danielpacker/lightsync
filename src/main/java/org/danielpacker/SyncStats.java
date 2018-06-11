package org.danielpacker;

class SyncStats {

    volatile private long numTasksQueued = 0;
    volatile private long numTasksCompleted = 0;
    volatile private long startTime = 0;

    public SyncStats() {
        startTime = System.currentTimeMillis();
    }

    public long getNumTasksQueued() {
        return numTasksQueued;
    }

    public long getNumTasksCompleted() {
        return numTasksCompleted;
    }

    public double getRunTime() {
        return (System.currentTimeMillis() - startTime)/1000.0;
    }

    synchronized void setNumTasksQueued(long numTasksQueued) {
        this.numTasksQueued = numTasksQueued;
    }

    synchronized public void setNumTasksCompleted(long numTasksCompleted) {
        this.numTasksCompleted = numTasksCompleted;
    }

    public double tasksCompletedPerSec() {
        if (getRunTime() > 0)
            return numTasksCompleted/getRunTime();
        else
            return 0.0;
    }

    public String toString() {

        return "Statistics:\n" +
                "\n===================================================================\n" +
                "Total # of sync tasks queued: " + getNumTasksQueued() + "\n" +
                "Total # of sync tasks completed: " + getNumTasksCompleted() + "\n" +
                "Total runtime (s): " + getRunTime() + "\n" +
                "Tasks completed/s: " + String.format( "%.2f", tasksCompletedPerSec()) + "\n" +
                "===================================================================\n";
    }

}


