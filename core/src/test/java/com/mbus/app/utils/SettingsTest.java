package com.mbus.app.utils;

import com.badlogic.gdx.Preferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsTest {

    private final Map<String, Object> stored = new HashMap<>();

    private Settings open() {
        return new Settings((Preferences) Proxy.newProxyInstance(
            Preferences.class.getClassLoader(), new Class[]{Preferences.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("put")) stored.put((String) args[0], args[1]);
                if (method.getName().equals("contains")) return stored.containsKey((String) args[0]);
                if (method.getName().startsWith("get") && args.length == 2) return stored.getOrDefault((String) args[0], args[1]);
                if (method.getName().equals("getFloat")) return stored.getOrDefault((String) args[0], 0f);
                return null;
            }));
    }

    @Test
    public void startsEmpty() {
        Settings settings = open();
        assertTrue(settings.getFavorites().isEmpty());
        assertTrue(settings.getHiddenLines().isEmpty());
        assertTrue(settings.isShowStops());
        assertFalse(settings.hasCamera());
    }

    @Test
    public void remembersFavoritesInOrder() {
        Settings settings = open();
        settings.toggleFavorite("180");
        settings.toggleFavorite("53");
        settings.toggleFavorite("7");
        settings.toggleFavorite("53");

        assertEquals(Arrays.asList("180", "7"), new ArrayList<>(open().getFavorites()));
        assertTrue(open().isFavorite("7"));
        assertFalse(open().isFavorite("53"));
    }

    @Test
    public void forgetsTheLastFavorite() {
        open().toggleFavorite("180");
        open().toggleFavorite("180");
        assertTrue(open().getFavorites().isEmpty());
    }

    @Test
    public void remembersTheRest() {
        Settings settings = open();
        settings.setHiddenLines(Arrays.asList("65", "71"));
        settings.setShowStops(false);
        settings.setCamera(1f, 2f, 0.3f);

        assertTrue(open().getHiddenLines().contains("71"));
        assertFalse(open().isShowStops());
        assertTrue(open().hasCamera());
        assertEquals(0.3f, open().getCameraZoom(), 0f);
    }
}
