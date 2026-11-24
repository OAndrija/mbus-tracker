package com.mbus.app.systems.data;

import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Settings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;

/** Looks for a newer timetable from time to time. A download is only kept if it is a complete timetable. */
public class TimetableUpdater {

    public static final String SOURCE = "https://gitlab.com/api/v4/projects/derp-si%2Fgtfs-generators"
        + "/packages/generic/marprom_official/latest/marprom_gtfs.zip";

    private static final int DAYS_BETWEEN_CHECKS = 7;
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BYTES = 30 * 1024 * 1024;
    private static final int MIN_LINES = 5;
    private static final int MIN_STOPS = 100;

    private final String source;
    private final TimetableFiles files;
    private final Settings settings;

    public TimetableUpdater(String source, TimetableFiles files, Settings settings) {
        this.source = source;
        this.files = files;
        this.settings = settings;
    }

    /** Once a week, or every day while the timetable in use has expired. */
    public boolean isDue(LocalDate today, boolean expired) {
        LocalDate last = settings.getLastUpdateCheck();
        return last == null || last.isAfter(today)
            || !last.plusDays(expired ? 1 : DAYS_BETWEEN_CHECKS).isAfter(today);
    }

    /** Returns true if a new timetable was saved. Takes a while, so do not call this on the main thread. */
    public boolean update(LocalDate today) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "MBus");
            String tag = settings.getTimetableTag();
            if (!tag.isEmpty()) connection.setRequestProperty("If-None-Match", tag);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                settings.setLastUpdateCheck(today);
                return false;
            }
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Status " + status);

            byte[] content;
            try (InputStream in = connection.getInputStream()) {
                content = read(in);
            }
            boolean isNew = !files.isKnown(content);
            if (isNew) {
                check(content, today);
                files.saveDownloaded(content);
            }
            String newTag = connection.getHeaderField("ETag");
            settings.setTimetableTag(newTag == null ? "" : newTag);
            settings.setLastUpdateCheck(today);
            return isNew;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("The download is too large");
        }
        return out.toByteArray();
    }

    private static void check(byte[] content, LocalDate today) throws IOException {
        Timetable timetable;
        try {
            timetable = GtfsLoader.load(new ByteArrayInputStream(content));
        } catch (RuntimeException e) {
            throw new IOException("The download is not a timetable", e);
        }
        if (timetable.getLines().size() < MIN_LINES || timetable.getStops().size() < MIN_STOPS) {
            throw new IOException("The download is not a complete timetable");
        }
        if (timetable.isExpired(today)) throw new IOException("The download has expired");
    }
}
