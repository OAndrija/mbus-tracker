package com.mbus.app.model;

import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One way a line is driven: a direction, the stops in order and the road between them. */
public class RoutePattern {

    public final BusLine line;
    private final List<BusStop> stops;
    private final List<Geolocation> path;
    private final List<Trip> trips = new ArrayList<>();

    public RoutePattern(BusLine line, List<BusStop> stops, List<Geolocation> path) {
        this.line = line;
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        line.addPattern(this);
    }

    public List<BusStop> getStops() {
        return stops;
    }

    public List<Geolocation> getPath() {
        return path;
    }

    public List<Trip> getTrips() {
        return Collections.unmodifiableList(trips);
    }

    public String getDestination() {
        return stops.get(stops.size() - 1).name;
    }

    /** Adds a trip and registers it at all its stops. */
    public Trip addTrip(Service service, int[] arrivals, int[] departures) {
        Trip trip = new Trip(this, service, arrivals, departures);
        trips.add(trip);
        for (int i = 0; i < stops.size() - 1; i++) {
            stops.get(i).addCall(new StopCall(trip, i));
        }
        return trip;
    }
}
