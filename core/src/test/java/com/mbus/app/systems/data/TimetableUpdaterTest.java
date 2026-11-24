package com.mbus.app.systems.data;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Settings;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Downloads from a web server of its own. */
public class TimetableUpdaterTest {

    private static final FileHandle BUNDLED = new FileHandle("data/marprom_gtfs.zip");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, Object> stored = new HashMap<>();
    private HttpServer server;
    private byte[] served;
    private int status = 200;
    private int requests = 0;

    private Settings settings;
    private FileHandle downloaded;
    private TimetableFiles files;
    private TimetableUpdater updater;
    private LocalDate today;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/timetable.zip", exchange -> {
            requests++;
            exchange.getResponseHeaders().set("ETag", "\"v1\"");
            if ("\"v1\"".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                exchange.sendResponseHeaders(status, served.length);
                exchange.getResponseBody().write(served);
            }
            exchange.close();
        });
        server.start();

        settings = new Settings((Preferences) Proxy.newProxyInstance(
            Preferences.class.getClassLoader(), new Class[]{Preferences.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("put")) stored.put((String) args[0], args[1]);
                if (method.getName().startsWith("get")) return stored.getOrDefault((String) args[0], args[1]);
                return null;
            }));
        downloaded = new FileHandle(folder.getRoot().toPath().resolve("data/marprom_gtfs.zip").toFile());
        files = new TimetableFiles(BUNDLED, downloaded);
        updater = new TimetableUpdater(
            "http://localhost:" + server.getAddress().getPort() + "/timetable.zip", files, settings);
        today = GtfsLoader.load(new FileInputStream(BUNDLED.file())).getValidUntil().minusDays(10);
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    /** The bundled timetable with another year in its calendar. */
    private static byte[] bundledInYear(int year) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(BUNDLED.read());
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                byte[] content = in.readAllBytes();
                if (entry.getName().startsWith("calendar")) {
                    content = new String(content, StandardCharsets.UTF_8)
                        .replaceAll("\\b20\\d\\d(\\d{4})\\b", year + "$1").getBytes(StandardCharsets.UTF_8);
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(content);
            }
        }
        return bytes.toByteArray();
    }

    @Test
    public void savesAndUsesANewTimetable() throws IOException {
        int nextYear = today.getYear() + 1;
        served = bundledInYear(nextYear);

        assertTrue(updater.update(today));
        Timetable timetable = files.load();
        assertEquals(nextYear, timetable.getValidUntil().getYear());
        assertEquals(today, settings.getLastUpdateCheck());
    }

    @Test
    public void doesNotDownloadTheSameFileTwice() throws IOException {
        served = bundledInYear(today.getYear() + 1);
        assertTrue(updater.update(today));
        assertFalse(updater.update(today));
        assertEquals(2, requests);
        assertEquals("\"v1\"", settings.getTimetableTag());
    }

    @Test
    public void ignoresTheTimetableTheAppAlreadyHas() throws IOException {
        served = Files.readAllBytes(Paths.get(BUNDLED.path()));
        assertFalse(updater.update(today));
        assertFalse(downloaded.exists());
    }

    @Test
    public void ignoresTheSameTimetableInANewZipFile() throws IOException {
        served = bundledInYear(today.getYear()); // rebuilt, but every date stays the same
        assertFalse(java.util.Arrays.equals(served, BUNDLED.readBytes()));
        assertFalse(updater.update(today));
        assertFalse(downloaded.exists());
    }

    @Test
    public void keepsTheOldTimetableIfTheNewOneIsBroken() throws IOException {
        served = bundledInYear(today.getYear() + 1);
        assertTrue(updater.update(today));
        byte[] good = downloaded.readBytes();
        stored.remove("timetableTag");

        served = "<html>Sign in</html>".getBytes(StandardCharsets.UTF_8);
        assertFails();
        served = bundledInYear(today.getYear() - 3); // long expired
        assertFails();
        served = new byte[31 * 1024 * 1024];
        assertFails();
        status = 500;
        assertFails();

        assertEquals(good.length, downloaded.readBytes().length);
        assertEquals(today.getYear() + 1, files.load().getValidUntil().getYear());
    }

    private void assertFails() {
        try {
            updater.update(today);
            fail("expected an error");
        } catch (IOException expected) {
            assertTrue(stored.getOrDefault("timetableTag", "").equals(""));
        }
    }

    @Test
    public void startsEvenIfTheSavedFileIsDamaged() throws IOException {
        downloaded.writeBytes(new byte[]{1, 2, 3}, false);
        assertTrue(files.load().getLines().size() > 10);
    }

    @Test
    public void usesTheBundledTimetableIfItIsNewer() throws IOException {
        downloaded.writeBytes(bundledInYear(today.getYear() - 1), false);
        assertEquals(today.getYear(), files.load().getValidUntil().getYear());
    }

    @Test
    public void looksOnceAWeekOrDailyAfterExpiry() {
        assertTrue(updater.isDue(today, false));
        settings.setLastUpdateCheck(today);
        assertFalse(updater.isDue(today, false));
        assertFalse(updater.isDue(today.plusDays(6), false));
        assertTrue(updater.isDue(today.plusDays(7), false));
        assertFalse(updater.isDue(today, true));
        assertTrue(updater.isDue(today.plusDays(1), true));
        assertTrue("clock was wrong", updater.isDue(today.minusDays(30), false));
    }
}
