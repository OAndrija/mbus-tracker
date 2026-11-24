package com.mbus.app.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All stops, lines and trips. */
public class Timetable {

    private final List<BusStop> stops;
    private final List<BusLine> lines;
    private final LocalDate validUntil;

    private static final int CACHED_DAYS = 8;

    private final Map<LocalDate, List<Trip>> tripsByDate = new LinkedHashMap<LocalDate, List<Trip>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, List<Trip>> eldest) {
            return size() > CACHED_DAYS;
        }
    };

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
        return tripsByDate.computeIfAbsent(date, this::collectTrips);
    }

    private List<Trip> collectTrips(LocalDate date) {
        List<Trip> trips = new ArrayList<>();
        for (BusLine line : lines) {
            for (RoutePattern pattern : line.getPatterns()) {
                for (Trip trip : pattern.getTrips()) {
                    if (trip.service.runsOn(date)) trips.add(trip);
                }
            }
        }
        Collections.sort(trips, (a, b) -> Integer.compare(a.getStart(), b.getStart()));
        return Collections.unmodifiableList(trips);
    }

    /** How many buses are on the road at the start of each slot of a day. */
    public int[] countBuses(LocalDate date, int slots) {
        int[] counts = new int[slots];
        for (Trip trip : getTrips(date)) {
            for (int i = 0; i < slots; i++) {
                double time = i * 24 * 60 / (double) slots;
                if (time >= trip.getStart() && time <= trip.getEnd()) counts[i]++;
            }
        }
        return counts;
    }

    /** Departures from a stop on one day, at or after a time in minutes. All lines if the line is null. */
    public List<StopCall> getDepartures(BusStop stop, LocalDate date, int fromTime, BusLine line, int max) {
        List<StopCall> result = new ArrayList<>();
        for (StopCall call : stop.getCalls()) {
            if (result.size() == max) break;
            if (line != null && call.trip.pattern.line != line) continue;
            if (call.getTime() >= fromTime && call.trip.service.runsOn(date)) result.add(call);
        }
        return result;
    }
}
