package com.mbus.app.utils;

import com.badlogic.gdx.Gdx;

import java.util.function.DoubleSupplier;

/**
 * Relates three kinds of pixels: logical ones, in which sizes are given and which follow the display
 * scaling of the system; those of the window, in which the pointer is measured; and those of the screen.
 * On Windows the window is measured in screen pixels, on macOS in logical ones.
 */
public final class Display {

    private static final float MAX_SCALE = 4f;

    private static DoubleSupplier contentScale = () -> 1;

    private Display() {
    }

    /** Where to get the display scaling of the system, such as 1.5 for 150%. */
    public static void setContentScale(DoubleSupplier supplier) {
        contentScale = supplier;
    }

    /** Screen pixels per logical pixel. */
    public static float getDensity() {
        return density(contentScale.getAsDouble(), Gdx.graphics.getWidth(), Gdx.graphics.getBackBufferWidth());
    }

    /** Window pixels per logical pixel. */
    public static float getWindowScale() {
        return windowScale(contentScale.getAsDouble(), Gdx.graphics.getWidth(), Gdx.graphics.getBackBufferWidth());
    }

    static float density(double contentScale, int windowWidth, int screenWidth) {
        return windowScale(contentScale, windowWidth, screenWidth) * screenPerWindow(windowWidth, screenWidth);
    }

    static float windowScale(double contentScale, int windowWidth, int screenWidth) {
        float scale = (float) contentScale / screenPerWindow(windowWidth, screenWidth);
        return Math.max(1f, Math.min(MAX_SCALE, scale));
    }

    /** A minimized window has no size. */
    private static float screenPerWindow(int windowWidth, int screenWidth) {
        return windowWidth <= 0 || screenWidth <= 0 ? 1f : screenWidth / (float) windowWidth;
    }
}
