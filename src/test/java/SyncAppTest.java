import org.danielpacker.SyncConfig;
import org.danielpacker.SyncTaskManager;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SyncAppTest {

    private static SyncConfig config = new SyncConfig();

    private Path dir1;
    private Path dir2;

    @Before
    public void init() throws IOException {
        Path base = Paths.get("src", "test", "temp");
        config.setDir1(Paths.get(base.toString(), "test1").toString());
        config.setDir2(Paths.get(base.toString(), "test2").toString());
        dir1 = Paths.get(config.getDir1());
        dir2 = Paths.get(config.getDir2());
        if (!Files.exists(base))
            Files.createDirectory(base);
    }

    @Test
    public void test0CleanDirs() throws IOException {

        if (Files.exists(dir1))
            Files.walk(dir1)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

        if (Files.exists(dir2))
            Files.walk(dir2)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

        Files.createDirectory(dir1);
        Files.createDirectory(dir2);
    }

    @Test
    public void test1ConfirmDirsEmpty() throws IOException {
        File[] files1 = dir1.toFile().listFiles();
        assertTrue(files1.length == 0);

        File[] files2 = dir1.toFile().listFiles();
        assertTrue(files2.length == 0);
    }

    @Test
    public void test2createFileStartup() throws IOException, InterruptedException {

        // Create 10 files in dir1
        for (int i = 0; i < 10; i++) {
            PrintWriter out = new PrintWriter(Paths.get(dir1.toString(), "file" + i + ".txt").toString());
            out.println("Contents of file " + i);
            out.close();
        }

        // Create 10 files in dir2
        for (int i = 11; i < 20; i++) {
            PrintWriter out = new PrintWriter(Paths.get(dir2.toString(), "file" + i + ".txt").toString());
            out.println("Contents of file " + i);
            out.close();
        }

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        // Scan recursively for changes and produce tasks
        taskMgr.recursiveScan();

        taskMgr.startDoerWorker();

        Thread.sleep(10 * 1000); // wait 10s

        // Check that all files are copied
        for (int i = 0; i < 10; i++)
            assertTrue(Files.exists(Paths.get(dir2.toString(), "file" + i + ".txt")));
        for (int i = 11; i < 20; i++)
            assertTrue(Files.exists(Paths.get(dir1.toString(), "file" + i + ".txt")));

        taskMgr.shutDown();
    }

    @Test
    public void test3modifyFileStartup() throws IOException, InterruptedException {

        PrintWriter out = new PrintWriter(new FileOutputStream(
                Paths.get(dir1.toString(), "file1.txt").toFile(), true));
        out.println("Some more content!");
        out.close();

        PrintWriter out2 = new PrintWriter(new FileOutputStream(
                Paths.get(dir2.toString(), "file11.txt").toFile(), true));
        out2.println("Some more content!");
        out2.close();

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        // Scan recursively for changes and produce tasks
        taskMgr.recursiveScan();

        taskMgr.startDoerWorker();

        Thread.sleep(5 * 1000); // wait 5s

        assertTrue(Paths.get(dir1.toString(), "file1.txt").toFile().length()
                == Paths.get(dir2.toString(), "file1.txt").toFile().length());

        assertTrue(Paths.get(dir1.toString(), "file11.txt").toFile().length()
                == Paths.get(dir2.toString(), "file11.txt").toFile().length());

        taskMgr.shutDown();
    }

    @Test
    public void test4mkdirStartup() throws IOException, InterruptedException {

        Path new1 = Paths.get(dir1.toString(), "new1");
        Path new1_copy = Paths.get(dir2.toString(), "new1");
        Path new2 = Paths.get(dir2.toString(), "new2");
        Path new2_copy = Paths.get(dir1.toString(), "new2");

        assertFalse(Files.exists(new1));
        assertFalse(Files.exists(new2));
        assertFalse(Files.exists(new1_copy));
        assertFalse(Files.exists(new2_copy));

        Files.createDirectory(new1);
        Files.createDirectory(new2);

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        // Scan recursively for changes and produce tasks
        taskMgr.recursiveScan();

        taskMgr.startDoerWorker();

        Thread.sleep(5 * 1000); // wait 5s

        assertTrue(Files.exists(new1_copy));
        assertTrue(Files.exists(new2_copy));

        taskMgr.shutDown();
    }


    @Test
    public void test5createFileWatch() throws IOException, InterruptedException {

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        taskMgr.startDoerWorker();

        taskMgr.startWatcherWorker();

        // Create 10 files in dir1
        for (int i = 0; i < 10; i++) {
            PrintWriter out = new PrintWriter(Paths.get(dir1.toString(), "watched" + i + ".txt").toString());
            out.println("Contents of file " + i);
            out.close();
        }

        // Create 10 files in dir2
        for (int i = 11; i < 20; i++) {
            PrintWriter out = new PrintWriter(Paths.get(dir2.toString(), "watched" + i + ".txt").toString());
            out.println("Contents of file " + i);
            out.close();
        }

        Thread.sleep(5 * 1000); // wait 10s

        // Check that all files are copied
        for (int i = 0; i < 10; i++)
            assertTrue(Files.exists(Paths.get(dir2.toString(), "watched" + i + ".txt")));
        for (int i = 11; i < 20; i++)
            assertTrue(Files.exists(Paths.get(dir1.toString(), "watched" + i + ".txt")));

        taskMgr.shutDown();
    }

    @Test
    public void test6modifyFileWatch() throws IOException, InterruptedException {

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        taskMgr.startDoerWorker();

        taskMgr.startWatcherWorker();

        PrintWriter out = new PrintWriter(new FileOutputStream(
                Paths.get(dir1.toString(), "watched1.txt").toFile(), true));
        out.println("Some more content!");
        out.close();

        PrintWriter out2 = new PrintWriter(new FileOutputStream(
                Paths.get(dir2.toString(), "watched11.txt").toFile(), true));
        out2.println("Some more content!");
        out2.close();

        Thread.sleep(5 * 1000); // wait 10s

        assertTrue(Paths.get(dir1.toString(), "watched1.txt").toFile().length()
                == Paths.get(dir2.toString(), "watched1.txt").toFile().length());

        assertTrue(Paths.get(dir1.toString(), "watched11.txt").toFile().length()
                == Paths.get(dir2.toString(), "watched11.txt").toFile().length());

        taskMgr.shutDown();
    }

    @Test
    public void test7mkdirWatched() throws IOException, InterruptedException {

        Path new1 = Paths.get(dir1.toString(), "watched1");
        Path new1_copy = Paths.get(dir2.toString(), "watched1");
        Path new2 = Paths.get(dir2.toString(), "watched2");
        Path new2_copy = Paths.get(dir1.toString(), "watched2");

        assertFalse(Files.exists(new1));
        assertFalse(Files.exists(new2));
        assertFalse(Files.exists(new1_copy));
        assertFalse(Files.exists(new2_copy));

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        taskMgr.startDoerWorker();

        taskMgr.startWatcherWorker();

        Files.createDirectory(new1);
        Files.createDirectory(new2);

        Thread.sleep(5 * 1000); // wait 5s

        assertTrue(Files.exists(new1_copy));
        assertTrue(Files.exists(new2_copy));

        taskMgr.shutDown();
    }

    @Test
    public void test8rmdirWatched() throws IOException, InterruptedException {

        Path new1 = Paths.get(dir1.toString(), "removeme1");
        Path new1_copy = Paths.get(dir2.toString(), "removeme1");
        Path new2 = Paths.get(dir2.toString(), "removeme2");
        Path new2_copy = Paths.get(dir1.toString(), "removeme2");

        Files.deleteIfExists(new1);
        Files.deleteIfExists(new2);
        Files.deleteIfExists(new1_copy);
        Files.deleteIfExists(new2_copy);

        Files.createDirectory(new1);
        Files.createDirectory(new2);

        // Task mgr is the high level interface for the app
        SyncTaskManager taskMgr = new SyncTaskManager(config);

        taskMgr.startDoerWorker();

        taskMgr.startWatcherWorker();

        Files.delete(new1);
        Files.delete(new2);

        Thread.sleep(10 * 1000); // wait 5s

        assertFalse(Files.exists(new1_copy));
        assertFalse(Files.exists(new2_copy));

        taskMgr.shutDown();
    }
}