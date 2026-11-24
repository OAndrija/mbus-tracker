package com.mbus.app.systems.journey;

import com.mbus.app.utils.Geolocation;

/** Estimates of walking times, as there are no footpaths in the timetable. */
final class Walking {

    private static final double METERS_PER_MINUTE = 75; // 4.5 km/h
    /** How much longer the way is than the straight line. */
    private static final double DETOUR = 1.3;
    private static final double METERS_PER_DEGREE = 111_320;

    private Walking() {
    }

    /** As the crow flies. */
    static double meters(Geolocation a, Geolocation b) {
        double lngScale = Math.cos(Math.toRadians((a.lat + b.lat) / 2));
        return METERS_PER_DEGREE * Math.hypot(a.lat - b.lat, (a.lng - b.lng) * lngScale);
    }

    static int minutes(double meters) {
        return (int) Math.ceil(meters * DETOUR / METERS_PER_MINUTE);
    }
}
