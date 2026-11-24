package com.mbus.app.systems.map;

import com.badlogic.gdx.math.MathUtils;

/** How wide lines are drawn at the current zoom. Sizes are in logical screen pixels. */
public class LineStyle {

    private static final float WIDTH_FAR = 3f;
    private static final float WIDTH_NEAR = 6f;
    private static final float LANE_GAP = 0.6f;
    /** All lines of a street together, however many there are. */
    private static final float BAND_FAR = 15f;
    private static final float BAND_NEAR = 34f;

    /** World pixels per screen pixel. */
    float pixel = 1f;
    float width;
    float gap;
    float maxBand;

    void set(float worldPerPixel, float closeness) {
        pixel = worldPerPixel;
        width = MathUtils.lerp(WIDTH_FAR, WIDTH_NEAR, closeness);
        gap = width + LANE_GAP;
        maxBand = MathUtils.lerp(BAND_FAR, BAND_NEAR, closeness);
    }

    /** From one lane to the next. */
    float spacing(float laneCount) {
        return Math.min(gap, maxBand / Math.max(1f, laneCount));
    }
}
