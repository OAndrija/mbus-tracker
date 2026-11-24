package com.mbus.app.utils;

import com.badlogic.gdx.Preferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the app remembers between runs. May be used from any thread. */
public class Settings {

    private static final String FAVORITES = "favorites";
    private static final String HIDDEN_LINES = "hiddenLines";
    private static final String SHOW_STOPS = "showStops";
    private static final String DARK = "dark";
    private static final String LANGUAGE = "language";
    private static final String LAST_UPDATE_CHECK = "lastUpdateCheck";
    private static final String TIMETABLE_TAG = "timetableTag";
    private static final String CAMERA_X = "cameraX";
    private static final String CAMERA_Y = "cameraY";
    private static final String CAMERA_ZOOM = "cameraZoom";

    private final Preferences preferences;
    private final Set<String> favorites;

    public Settings(Preferences preferences) {
        this.preferences = preferences;
        this.favorites = split(preferences.getString(FAVORITES, ""));
    }

    private static Set<String> split(String joined) {
        Set<String> ids = new LinkedHashSet<>();
        if (!joined.isEmpty()) ids.addAll(Arrays.asList(joined.split(",")));
        return ids;
    }

    /** Ids of the favorite stops, in the order they were added. */
    public synchronized Set<String> getFavorites() {
        return Collections.unmodifiableSet(favorites);
    }

    public synchronized boolean isFavorite(String stopId) {
        return favorites.contains(stopId);
    }

    public synchronized void toggleFavorite(String stopId) {
        if (!favorites.remove(stopId)) favorites.add(stopId);
        preferences.putString(FAVORITES, String.join(",", favorites));
        preferences.flush();
    }

    /** Ids of the lines that are switched off. */
    public synchronized Set<String> getHiddenLines() {
        return split(preferences.getString(HIDDEN_LINES, ""));
    }

    public synchronized void setHiddenLines(Collection<String> lineIds) {
        preferences.putString(HIDDEN_LINES, String.join(",", lineIds));
        preferences.flush();
    }

    public synchronized boolean isShowStops() {
        return preferences.getBoolean(SHOW_STOPS, true);
    }

    public synchronized void setShowStops(boolean show) {
        preferences.putBoolean(SHOW_STOPS, show);
        preferences.flush();
    }

    public synchronized boolean isDark() {
        return preferences.getBoolean(DARK, false);
    }

    public synchronized void setDark(boolean dark) {
        preferences.putBoolean(DARK, dark);
        preferences.flush();
    }

    /** Language code, empty if none was chosen. */
    public synchronized String getLanguage() {
        return preferences.getString(LANGUAGE, "");
    }

    public synchronized void setLanguage(String code) {
        preferences.putString(LANGUAGE, code);
        preferences.flush();
    }

    /** The last day a newer timetable was looked for, or null. */
    public synchronized LocalDate getLastUpdateCheck() {
        try {
            return LocalDate.parse(preferences.getString(LAST_UPDATE_CHECK, ""));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public synchronized void setLastUpdateCheck(LocalDate date) {
        preferences.putString(LAST_UPDATE_CHECK, date.toString());
        preferences.flush();
    }

    /** What the server called the last timetable it sent, so that it need not send it again. */
    public synchronized String getTimetableTag() {
        return preferences.getString(TIMETABLE_TAG, "");
    }

    public synchronized void setTimetableTag(String tag) {
        preferences.putString(TIMETABLE_TAG, tag);
        preferences.flush();
    }

    public synchronized boolean hasCamera() {
        return preferences.contains(CAMERA_ZOOM);
    }

    public synchronized float getCameraX() {
        return preferences.getFloat(CAMERA_X);
    }

    public synchronized float getCameraY() {
        return preferences.getFloat(CAMERA_Y);
    }

    public synchronized float getCameraZoom() {
        return preferences.getFloat(CAMERA_ZOOM);
    }

    public synchronized void setCamera(float x, float y, float zoom) {
        preferences.putFloat(CAMERA_X, x);
        preferences.putFloat(CAMERA_Y, y);
        preferences.putFloat(CAMERA_ZOOM, zoom);
        preferences.flush();
    }
}
