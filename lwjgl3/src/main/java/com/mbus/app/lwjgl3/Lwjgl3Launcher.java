package com.mbus.app.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.mbus.app.MBusTracker;
import com.mbus.app.utils.Display;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {

    // Logical pixels
    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;
    private static final int MIN_WIDTH = 720;
    private static final int MIN_HEIGHT = 600;
    private static final float MAX_SHARE_OF_SCREEN = 0.88f;

    private static final int BACKGROUND_FPS = 20;

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
        Graphics.DisplayMode screen = Lwjgl3ApplicationConfiguration.getDisplayMode();
        configuration.setForegroundFPS(screen.refreshRate + 1);

        Display.setContentScale(Lwjgl3Launcher::getWindowContentScale);
        float scale = getScreenScale();
        int minWidth = fit(MIN_WIDTH * scale, screen.width);
        int minHeight = fit(MIN_HEIGHT * scale, screen.height);
        configuration.setWindowSizeLimits(minWidth, minHeight, -1, -1);

        // The window size has to be known before the app starts, so it is kept apart from its settings.
        final Preferences window = new Lwjgl3Preferences("mbus-window", ".prefs/");
        // A saved size may be from a larger screen.
        int width = window.getInteger("width", fit(DEFAULT_WIDTH * scale, screen.width));
        int height = window.getInteger("height", fit(DEFAULT_HEIGHT * scale, screen.height));
        configuration.setWindowedMode(
            Math.max(minWidth, Math.min(width, screen.width)), Math.max(minHeight, Math.min(height, screen.height)));
        configuration.setMaximized(window.getBoolean("maximized", false));
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            private boolean maximized = window.getBoolean("maximized", false);

            @Override
            public void maximized(boolean isMaximized) {
                maximized = isMaximized;
            }

            @Override
            public void focusLost() {
                Gdx.graphics.setForegroundFPS(BACKGROUND_FPS);
            }

            @Override
            public void focusGained() {
                Gdx.graphics.setForegroundFPS(screen.refreshRate + 1);
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
        configuration.setWindowIcon("mbus128.png", "mbus64.png", "mbus32.png", "mbus16.png");
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);

        return configuration;
    }

    /** Leaves room for the taskbar and the title bar. */
    private static int fit(float size, int screenSize) {
        return Math.round(Math.min(size, screenSize * MAX_SHARE_OF_SCREEN));
    }

    /** Window pixels per logical pixel on the main screen. macOS measures windows in logical pixels. */
    private static float getScreenScale() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) return 1f;
        float[] scale = new float[1];
        GLFW.glfwGetMonitorContentScale(GLFW.glfwGetPrimaryMonitor(), scale, new float[1]);
        return Math.max(1f, scale[0]);
    }

    private static double getWindowContentScale() {
        float[] scale = new float[1];
        GLFW.glfwGetWindowContentScale(
            ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle(), scale, new float[1]);
        return scale[0];
    }
}
