package org.danielpacker;

import java.nio.file.Path;

public class SyncTask {

    public enum TYPE { MKDIR, RMDIR, CP, RM };

    private Path dst;
    private Path src;
    private TYPE type;

    SyncTask(TYPE type, Path src, Path dst) {

        this.src = src;
        this.dst = dst;
        this.type = type;
    }

    public String toString() {

        return "Type: " + type + ", Src: " + src + ", Dst: " + dst;
    }

    public Path getDst() {
        return dst;
    }

    public void setDst(Path dst) {
        this.dst = dst;
    }

    public Path getSrc() {
        return src;
    }

    public void setSrc(Path src) {
        this.src = src;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }
}
