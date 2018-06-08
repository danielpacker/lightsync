package org.danielpacker;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SyncUtil {

    static Path srcTodestPath(Path p, Path src, Path dst) {

        String norm = p.toString().substring(src.toString().length());
        if (norm.indexOf(File.separator)==0)
            norm = norm.substring(1);
        norm = dst + File.separator + norm;
        return Paths.get(norm);
    }

    static Path normalizePath(Path p, Path base) {

        String norm = p.toString().substring(base.toString().length());
        if (norm.indexOf(File.separator)==0)
            norm = norm.substring(1);
        return Paths.get(norm);
    }

    enum OS { WINDOWS, MAC, LINUX, UNIX, SOLARIS, UNKNOWN };

    static OS getOS() {

        String OSString = System.getProperty("os.name").toLowerCase();

        if (OSString.contains("win")) {
                return OS.WINDOWS;
        }
        else if (OSString.contains("mac")) {
            return OS.MAC;
        }
        else if (OSString.contains("nix") || OSString.contains("aix")) {
            return OS.UNIX;
        }
        else if (OSString.contains("inux")) {
            return OS.LINUX;
        }
        else if (OSString.contains("sunos")) {
            return OS.SOLARIS;
        }
        else {
            return OS.UNKNOWN;
        }
    }
}
