package com.mbus.app.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All stops, lines and trips. */
public class Timetable {

    private final List<BusStop> stops;
    private final List<BusLine> lines;
    private final LocalDate validUntil;

    private LocalDate cachedDate;
    private List<Trip> cachedTrips = Collections.emptyList();

    /** Expects stops in display order. */
    public Timetable(List<BusStop> stops, List<BusLine> lines, LocalDate validUntil) {
        for (BusStop stop : stops) stop.sortCalls();
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
        List<BusLine> sorted = new ArrayList<>(lines);
        Collections.sort(sorted);
        this.lines = Collections.unmodifiableList(sorted);
        this.validUntil = validUntil;
    }

    public List<BusStop> getStops() {
        return stops;
    }

    public List<BusLine> getLines() {
        return lines;
    }

    /** The last day the timetable has trips for. */
    public LocalDate getValidUntil() {
        return validUntil;
    }

    public boolean isExpired(LocalDate date) {
        return date.isAfter(validUntil);
    }

    /** The trips of one day, ordered by start. */
    public List<Trip> getTrips(LocalDate date) {
        if (!date.equals(cachedDate)) {
            List<Trip> trips = new ArrayList<>();
            for (BusLine line : lines) {
                for (RoutePattern pattern : line.getPatterns()) {
                    for (Trip trip : pattern.getTrips()) {
                        if (trip.service.runsOn(date)) trips.add(trip);
                    }
                }
            }
            Collections.sort(trips, (a, b) -> Integer.compare(a.getStart(), b.getStart()));
            cachedTrips = Collections.unmodifiableList(trips);
            cachedDate = date;
        }
        return cachedTrips;
    }

    /** Departures from a stop on one day, at or after a time in minutes. */
    public List<StopCall> getDepartures(BusStop stop, LocalDate date, int fromTime, int max) {
        List<StopCall> result = new ArrayList<>();
        for (StopCall call : stop.getCalls()) {
            if (result.size() == max) break;
            if (call.getTime() >= fromTime && call.trip.service.runsOn(date)) result.add(call);
        }
        return result;
    }
}
