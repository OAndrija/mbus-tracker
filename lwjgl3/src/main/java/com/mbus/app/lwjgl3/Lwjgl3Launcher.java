package com.mbus.app.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.mbus.app.MBusTracker;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new MBusTracker(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("MBus");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        // The window size has to be known before the app starts, so it is kept apart from its settings.
        final Preferences window = new Lwjgl3Preferences("mbus-window", ".prefs/");
        configuration.setWindowedMode(window.getInteger("width", 1600), window.getInteger("height", 900));
        configuration.setMaximized(window.getBoolean("maximized", false));
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            private boolean maximized = window.getBoolean("maximized", false);

            @Override
            public void maximized(boolean isMaximized) {
                maximized = isMaximized;
            }

            @Override
            public boolean closeRequested() {
                window.putBoolean("maximized", maximized);
                if (!maximized) {
                    window.putInteger("width", Gdx.graphics.getWidth());
                    window.putInteger("height", Gdx.graphics.getHeight());
                }
                window.flush();
                return true;
            }
        });
        configuration.setWindowSizeLimits(720, 600, -1, -1);
        configuration.setWindowIcon("mbus128.png", "mbus64.png", "mbus32.png", "mbus16.png");
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);

        return configuration;
    }
}
