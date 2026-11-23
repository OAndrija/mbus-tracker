package com.mbus.app.utils;

/**
 * The time of day in Maribor, in minutes. Runs on the monotonic timer between corrections, so
 * animations based on it advance evenly from frame to frame.
 */
public final class CityClock {

    private static final double MAX_DRIFT_MINUTES = 0.5 / 60.0;

    private double anchorMinutes;
    private long anchorNanos;

    public CityClock() {
        anchor();
    }

    public double minutes() {
        double now = anchorMinutes + (System.nanoTime() - anchorNanos) / 60e9;
        // Also takes care of midnight, sleep and changes of the system clock
        if (Math.abs(now - BusPositionCalculator.getCurrentTimeMinutesPrecise()) > MAX_DRIFT_MINUTES) {
            anchor();
            return anchorMinutes;
        }
        return now;
    }

    private void anchor() {
        anchorMinutes = BusPositionCalculator.getCurrentTimeMinutesPrecise();
        anchorNanos = System.nanoTime();
    }
}
