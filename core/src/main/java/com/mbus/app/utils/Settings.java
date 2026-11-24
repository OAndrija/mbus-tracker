package com.mbus.app.utils;

import com.badlogic.gdx.Preferences;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the app remembers between runs. */
public class Settings {

    private static final String FAVORITES = "favorites";
    private static final String HIDDEN_LINES = "hiddenLines";
    private static final String SHOW_STOPS = "showStops";
    private static final String DARK = "dark";
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
    public Set<String> getFavorites() {
        return Collections.unmodifiableSet(favorites);
    }

    public boolean isFavorite(String stopId) {
        return favorites.contains(stopId);
    }

    public void toggleFavorite(String stopId) {
        if (!favorites.remove(stopId)) favorites.add(stopId);
        preferences.putString(FAVORITES, String.join(",", favorites));
        preferences.flush();
    }

    /** Ids of the lines that are switched off. */
    public Set<String> getHiddenLines() {
        return split(preferences.getString(HIDDEN_LINES, ""));
    }

    public void setHiddenLines(Collection<String> lineIds) {
        preferences.putString(HIDDEN_LINES, String.join(",", lineIds));
        preferences.flush();
    }

    public boolean isShowStops() {
        return preferences.getBoolean(SHOW_STOPS, true);
    }

    public void setShowStops(boolean show) {
        preferences.putBoolean(SHOW_STOPS, show);
        preferences.flush();
    }

    public boolean isDark() {
        return preferences.getBoolean(DARK, false);
    }

    public void setDark(boolean dark) {
        preferences.putBoolean(DARK, dark);
        preferences.flush();
    }

    public boolean hasCamera() {
        return preferences.contains(CAMERA_ZOOM);
    }

    public float getCameraX() {
        return preferences.getFloat(CAMERA_X);
    }

    public float getCameraY() {
        return preferences.getFloat(CAMERA_Y);
    }

    public float getCameraZoom() {
        return preferences.getFloat(CAMERA_ZOOM);
    }

    public void setCamera(float x, float y, float zoom) {
        preferences.putFloat(CAMERA_X, x);
        preferences.putFloat(CAMERA_Y, y);
        preferences.putFloat(CAMERA_ZOOM, zoom);
        preferences.flush();
    }
}
