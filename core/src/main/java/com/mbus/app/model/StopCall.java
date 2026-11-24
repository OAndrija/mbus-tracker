package com.mbus.app.model;

/** One trip stopping at one stop. */
public class StopCall {

    public final Trip trip;
    /** Position of the stop within the trip; a trip may visit a stop twice. */
    public final int index;

    public StopCall(Trip trip, int index) {
        this.trip = trip;
        this.index = index;
    }

    /** Departure in minutes since midnight. */
    public int getTime() {
        return trip.getDeparture(index);
    }
}
