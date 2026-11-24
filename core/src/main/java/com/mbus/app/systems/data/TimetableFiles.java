package com.mbus.app.systems.data;

import com.badlogic.gdx.files.FileHandle;
import com.mbus.app.model.Timetable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** The timetable that comes with the app, and a newer one if it was downloaded. */
public class TimetableFiles {

    private final FileHandle bundled;
    private final FileHandle downloaded;

    public TimetableFiles(FileHandle bundled, FileHandle downloaded) {
        this.bundled = bundled;
        this.downloaded = downloaded;
    }

    FileHandle getDownloaded() {
        return downloaded;
    }

    /** Loads whichever of the two is valid for longer. A broken download is ignored. */
    public Timetable load() throws IOException {
        Timetable own = GtfsLoader.load(bundled.read(8192));
        if (!downloaded.exists()) return own;
        try {
            Timetable newer = GtfsLoader.load(downloaded.read(8192));
            return newer.getValidUntil().isBefore(own.getValidUntil()) ? own : newer;
        } catch (IOException | RuntimeException e) {
            return own;
        }
    }

    /**
     * True if one of the two files has the same content. The source is built anew every night, so the
     * bytes of the zip file differ even if nothing has changed.
     */
    boolean isKnown(byte[] content) throws IOException {
        Map<String, Long> sums = checksums(new ByteArrayInputStream(content));
        if (sums.equals(checksums(bundled.read()))) return true;
        try {
            return downloaded.exists() && sums.equals(checksums(downloaded.read()));
        } catch (IOException e) {
            return false; // damaged, so it is worth replacing
        }
    }

    /** Of every file in a zip file. Closes the stream. */
    private static Map<String, Long> checksums(InputStream zip) throws IOException {
        Map<String, Long> sums = new HashMap<>();
        byte[] buffer = new byte[16384];
        try (ZipInputStream in = new ZipInputStream(zip)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                CRC32 sum = new CRC32();
                for (int n = in.read(buffer); n > 0; n = in.read(buffer)) sum.update(buffer, 0, n);
                sums.put(entry.getName(), sum.getValue());
            }
        }
        return sums;
    }

    /** Replaces the downloaded timetable in one step, so that it is never half written. */
    void saveDownloaded(byte[] content) {
        FileHandle temporary = downloaded.sibling(downloaded.name() + ".part");
        temporary.writeBytes(content, false);
        temporary.moveTo(downloaded);
    }
}
