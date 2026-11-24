package com.mbus.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DisplayTest {

    @Test
    public void withoutScalingAllPixelsAreTheSame() {
        assertEquals(1f, Display.density(1, 1600, 1600), 0f);
        assertEquals(1f, Display.windowScale(1, 1600, 1600), 0f);
    }

    @Test
    public void windowsMeasuresTheWindowInScreenPixels() {
        assertEquals(1.5f, Display.density(1.5, 2400, 2400), 1e-6f);
        assertEquals(1.5f, Display.windowScale(1.5, 2400, 2400), 1e-6f);
    }

    @Test
    public void macMeasuresTheWindowInLogicalPixels() {
        assertEquals(2f, Display.density(2, 1600, 3200), 1e-6f);
        assertEquals(1f, Display.windowScale(2, 1600, 3200), 1e-6f);
    }

    @Test
    public void survivesAMinimizedWindowAndOddValues() {
        assertEquals(1.5f, Display.density(1.5, 0, 0), 1e-6f);
        assertEquals(1f, Display.windowScale(0.5, 800, 800), 0f);
        assertEquals(4f, Display.windowScale(9, 800, 800), 0f);
    }
}
