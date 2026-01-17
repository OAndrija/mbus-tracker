package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusLine {

    public final int lineId;
    public final int variantId;
    public final int direction;  // -1 or 1
    public final double length;
    public final String name;
    public final String note;
    public final String providerName;
    public final String providerLink;

    private final List<Geolocation> path;

    private final List<double[]> originalCoordinates;

    private final List<BusStop> stops;

    private final List<BusSchedule> schedules;

    public BusLine(int lineId,
                   int variantId,
                   int direction,
                   double length,
                   String name,
                   String note,
                   String providerName,
                   String providerLink,
                   List<Geolocation> path,
                   List<double[]> originalCoordinates,
                   List<BusStop> stops,
                   List<BusSchedule> schedules) {

        this.lineId = lineId;
        this.variantId = variantId;
        this.direction = direction;
        this.length = length;
        this.name = name;
        this.note = note;
        this.providerName = providerName;
        this.providerLink = providerLink;
        this.path = Collections.unmodifiableList(new ArrayList<Geolocation>(path));
        this.originalCoordinates = originalCoordinates != null
            ? Collections.unmodifiableList(new ArrayList<double[]>(originalCoordinates))
            : null;
        this.stops = stops != null
            ? Collections.unmodifiableList(new ArrayList<BusStop>(stops))
            : Collections.unmodifiableList(new ArrayList<BusStop>());
        this.schedules = schedules != null
            ? Collections.unmodifiableList(new ArrayList<BusSchedule>(schedules))
            : Collections.unmodifiableList(new ArrayList<BusSchedule>());
    }

    public BusLine(int lineId,
                   int variantId,
                   int direction,
                   double length,
                   String name,
                   String note,
                   String providerName,
                   String providerLink,
                   List<Geolocation> path,
                   List<double[]> originalCoordinates,
                   List<BusStop> stops) {
        this(lineId, variantId, direction, length, name, note, providerName,
            providerLink, path, originalCoordinates, stops, null);
    }

    public List<Geolocation> getPath() {
        return path;
    }

    public List<double[]> getOriginalCoordinates() {
        return originalCoordinates;
    }

    public List<BusStop> getStops() {
        return stops;
    }

    public List<BusSchedule> getSchedules() {
        return schedules;
    }

    public List<BusSchedule> getSchedulesForDayType(int dayType) {
        List<BusSchedule> result = new ArrayList<BusSchedule>();
        for (BusSchedule schedule : schedules) {
            if (schedule.dayType == dayType) {
                result.add(schedule);
            }
        }
        return result;
    }

    public BusSchedule getNextDeparture(int currentTime, int dayType) {
        BusSchedule nextSchedule = null;
        int minTimeDiff = Integer.MAX_VALUE;

        for (BusSchedule schedule : schedules) {
            if (schedule.dayType == dayType && schedule.departureTime >= currentTime) {
                int timeDiff = schedule.departureTime - currentTime;
                if (timeDiff < minTimeDiff) {
                    minTimeDiff = timeDiff;
                    nextSchedule = schedule;
                }
            }
        }

        return nextSchedule;
    }

    public int getPointCount() {
        return path.size();
    }

    public int getStopCount() {
        return stops.size();
    }

    public int getScheduleCount() {
        return schedules.size();
    }

    public boolean hasStop(BusStop stop) {
        return stops.contains(stop);
    }

    public boolean hasStop(int stopId) {
        for (BusStop stop : stops) {
            if (stop.idAvpost == stopId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "BusLine{" +
            "lineId=" + lineId +
            ", variantId=" + variantId +
            ", direction=" + direction +
            ", name='" + name + '\'' +
            ", points=" + path.size() +
            ", stops=" + stops.size() +
            ", schedules=" + schedules.size() +
            '}';
    }
}
