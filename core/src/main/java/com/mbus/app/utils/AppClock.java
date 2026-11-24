package com.mbus.app.utils;

import java.time.LocalDate;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * The date and time the app shows: normally now in Maribor, but it can be set to another time and run
 * faster. Advances on the monotonic timer, so that animations move evenly from frame to frame.
 */
public class AppClock {

    public static final int MINUTES_PER_DAY = 24 * 60;
    private static final double MAX_DRIFT_MINUTES = 0.5 / 60.0;

    private final DoubleSupplier wallMinutes;
    private final LongSupplier nanos;

    private double anchorMinutes;
    private long anchorNanos;
    private boolean live = true;
    private int speed = 1;
    private double now;

    public AppClock() {
        this(CityTime::totalMinutes, System::nanoTime);
    }

    /** Takes the minutes since the start of 1970 in Maribor, and a monotonic timer. */
    AppClock(DoubleSupplier wallMinutes, LongSupplier nanos) {
        this.wallMinutes = wallMinutes;
        this.nanos = nanos;
        goLive();
    }

    /** Reads the time once per frame, so that everything in a frame agrees on it. */
    public void update() {
        now = anchorMinutes + (nanos.getAsLong() - anchorNanos) / 60e9 * speed;
        // Also takes care of sleep and changes of the system clock
        if (live && Math.abs(now - wallMinutes.getAsDouble()) > MAX_DRIFT_MINUTES) {
            anchor(wallMinutes.getAsDouble());
        }
    }

    private void anchor(double minutes) {
        anchorMinutes = minutes;
        anchorNanos = nanos.getAsLong();
        now = minutes;
    }

    /** True if the clock shows the current time. */
    public boolean isLive() {
        return live;
    }

    public void goLive() {
        live = true;
        speed = 1;
        anchor(wallMinutes.getAsDouble());
    }

    public void travelTo(LocalDate date, double minutes) {
        live = false;
        double limited = Math.max(0, Math.min(MINUTES_PER_DAY - 0.01, minutes));
        anchor(date.toEpochDay() * (double) MINUTES_PER_DAY + limited);
    }

    public int getSpeed() {
        return speed;
    }

    /** A speed other than 1 makes the clock leave the current time. */
    public void setSpeed(int speed) {
        update();
        anchor(now);
        this.speed = speed;
        if (speed != 1) live = false;
    }

    public LocalDate getDate() {
        return LocalDate.ofEpochDay((long) Math.floor(now / MINUTES_PER_DAY));
    }

    /** Minutes since midnight, with fractions. */
    public double getMinutes() {
        return now - Math.floor(now / MINUTES_PER_DAY) * MINUTES_PER_DAY;
    }

    /** Whole minutes since midnight. */
    public int getMinute() {
        return (int) getMinutes();
    }

    /** The current date, whatever the clock shows. */
    public LocalDate getRealDate() {
        return LocalDate.ofEpochDay((long) Math.floor(wallMinutes.getAsDouble() / MINUTES_PER_DAY));
    }
}
