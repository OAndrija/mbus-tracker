package com.mbus.app.model;

/** One bus driving a pattern at a certain time of day. Times are minutes since midnight. */
public class Trip {

    public final RoutePattern pattern;
    public final Service service;
    private final int[] arrivals;
    private final int[] departures;

    Trip(RoutePattern pattern, Service service, int[] arrivals, int[] departures) {
        if (arrivals.length != pattern.getStops().size() || departures.length != arrivals.length) {
            throw new IllegalArgumentException("A trip needs a time for every stop");
        }
        this.pattern = pattern;
        this.service = service;
        this.arrivals = arrivals;
        this.departures = departures;
    }

    public int getStopCount() {
        return arrivals.length;
    }

    public int getArrival(int index) {
        return arrivals[index];
    }

    public int getDeparture(int index) {
        return departures[index];
    }

    public int getStart() {
        return departures[0];
    }

    public int getEnd() {
        return arrivals[arrivals.length - 1];
    }
}
