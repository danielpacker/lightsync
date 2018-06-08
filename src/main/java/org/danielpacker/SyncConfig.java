package org.danielpacker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;


public class SyncConfig {

    private static final Logger log = LogManager.getLogger(SyncConfig.class);

    private String dir1 = "";
    private String dir2 = "";
    private final String CONFIG_FULL_PATH = "src/main/resources/config.properties";
    private final String CONFIG_BASE_PATH = "/config.properties";

    public SyncConfig() {

        try {

            String source = load();
            log.info("Configuration loaded from: " + source);
        }
        catch (SyncException e) {
            log.error("Configuration problem: " + e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            log.error("Problem loading config: " + e.getMessage());
            System.exit(1);
        }
    }

    public String getDir1() {
        return dir1;
    }

    public void setDir1(String dir1) {
        this.dir1 = dir1;
    }

    public String getDir2() {
        return dir2;
    }

    public void setDir2(String dir2) {
        this.dir2 = dir2;
    }

    public String load() throws Exception {

        Properties props = new Properties();
        String source = "";

        if (Files.exists(Paths.get(CONFIG_FULL_PATH))) {
            try (FileInputStream in = new FileInputStream(CONFIG_FULL_PATH)) {
                props.load(in);
            } catch (IOException e) {
                log.error("Problem loading config file: " + e.getMessage());
                System.exit(1);
            }
            source = CONFIG_FULL_PATH;
        }
        else {
            InputStream in = getClass().getResourceAsStream(CONFIG_BASE_PATH);
            props.load(in);
            source = CONFIG_BASE_PATH;
        }

        dir1 = props.getProperty("dir1");
        dir2 = props.getProperty("dir2");

        if (dir1 == null || dir2 == null) {
            log.error("Both dir1 and dir2 must be configured.");
            System.exit(1);
        }

        File f1 = new File(dir1);
        File f2 = new File(dir2);

        if (! f1.exists()) {
            log.error("dir1 not found");
            System.exit(1);
        }

        if (! f2.exists()) {
            log.error("dir2 not found");
            System.exit(1);
        }

        return source;
    }
}
