package com.mbus.app.utils;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppClockTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private double wall;
    private long nanos;
    private AppClock clock;

    @Before
    public void createClock() {
        wall = TODAY.toEpochDay() * 1440.0 + 14 * 60;
        nanos = 5_000_000_000L;
        clock = new AppClock(() -> wall, () -> nanos);
    }

    private void pass(double minutes) {
        wall += minutes;
        nanos += (long) (minutes * 60e9);
        clock.update();
    }

    @Test
    public void followsTheRealTime() {
        pass(1.5);
        assertTrue(clock.isLive());
        assertEquals(TODAY, clock.getDate());
        assertEquals(14 * 60 + 1.5, clock.getMinutes(), 1e-6);
    }

    @Test
    public void catchesUpAfterSleep() {
        wall += 300; // the timer stood still
        clock.update();
        assertEquals(19 * 60, clock.getMinute());
    }

    @Test
    public void goesOnFromAnotherTime() {
        clock.travelTo(TODAY.plusDays(3), 8 * 60);
        assertFalse(clock.isLive());
        pass(2);
        assertEquals(TODAY.plusDays(3), clock.getDate());
        assertEquals(8 * 60 + 2, clock.getMinutes(), 1e-6);
        assertEquals(TODAY, clock.getRealDate());
    }

    @Test
    public void runsFasterAndAcrossMidnight() {
        clock.travelTo(TODAY, 23 * 60 + 50);
        clock.setSpeed(60);
        pass(0.5);
        assertEquals(TODAY.plusDays(1), clock.getDate());
        assertEquals(20, clock.getMinute());
    }

    @Test
    public void changingTheSpeedKeepsTheTime() {
        clock.travelTo(TODAY, 600);
        clock.setSpeed(10);
        pass(1);
        clock.setSpeed(1);
        assertEquals(610, clock.getMinutes(), 1e-6);
        pass(1);
        assertEquals(611, clock.getMinutes(), 1e-6);
    }

    @Test
    public void returnsToNow() {
        clock.travelTo(TODAY.plusDays(1), 0);
        clock.setSpeed(60);
        pass(10);
        clock.goLive();
        assertTrue(clock.isLive());
        assertEquals(1, clock.getSpeed());
        assertEquals(TODAY, clock.getDate());
        assertEquals(14 * 60 + 10, clock.getMinute());
    }

    @Test
    public void staysWithinTheDay() {
        clock.travelTo(TODAY, 24 * 60);
        assertEquals(TODAY, clock.getDate());
        clock.travelTo(TODAY, -5);
        assertEquals(0, clock.getMinute());
    }
}
