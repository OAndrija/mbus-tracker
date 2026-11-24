package com.mbus.app.systems.data;

import com.badlogic.gdx.files.FileHandle;
import com.mbus.app.model.Timetable;

import java.io.IOException;

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

    /** True if this is the content of one of the two files. */
    boolean isKnown(byte[] content) {
        return hasContent(bundled, content) || (downloaded.exists() && hasContent(downloaded, content));
    }

    private static boolean hasContent(FileHandle file, byte[] content) {
        return java.util.Arrays.equals(file.readBytes(), content);
    }

    /** Replaces the downloaded timetable in one step, so that it is never half written. */
    void saveDownloaded(byte[] content) {
        FileHandle temporary = downloaded.sibling(downloaded.name() + ".part");
        temporary.writeBytes(content, false);
        temporary.moveTo(downloaded);
    }
}
