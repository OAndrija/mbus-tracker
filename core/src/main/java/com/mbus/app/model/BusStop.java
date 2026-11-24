package com.mbus.app.model;

import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A stop on one side of the road. */
public class BusStop {

    public final String id;
    public final String name;
    public final Geolocation geo;

    private final List<BusLine> lines = new ArrayList<>();
    private final List<StopCall> calls = new ArrayList<>();

    public BusStop(String id, String name, Geolocation geo) {
        this.id = id;
        this.name = name;
        this.geo = geo;
    }

    /** The lines that stop here, in display order. */
    public List<BusLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Every departure from this stop on any day, ordered by time. Arrivals at a terminus are not included. */
    public List<StopCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    void addCall(StopCall call) {
        calls.add(call);
        if (!lines.contains(call.trip.pattern.line)) {
            lines.add(call.trip.pattern.line);
        }
    }

    void sortCalls() {
        Collections.sort(calls, (a, b) -> Integer.compare(a.getTime(), b.getTime()));
        Collections.sort(lines);
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
