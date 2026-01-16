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

    // Path as a list of WGS84 coordinates
    private final List<Geolocation> path;

    // Optional: original D96/TM coordinates if needed for debugging
    private final List<double[]> originalCoordinates;

    // Bus stops along this line (ordered)
    private final List<BusStop> stops;

    // NEW: Schedules for this line
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

    // Backward compatibility constructor
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

    /**
     * Get the path as an unmodifiable list of WGS84 coordinates
     */
    public List<Geolocation> getPath() {
        return path;
    }

    /**
     * Get the original EPSG:3794 coordinates (for debugging)
     */
    public List<double[]> getOriginalCoordinates() {
        return originalCoordinates;
    }

    /**
     * Get the bus stops along this line (ordered)
     */
    public List<BusStop> getStops() {
        return stops;
    }

    /**
     * Get all schedules for this line
     */
    public List<BusSchedule> getSchedules() {
        return schedules;
    }

    /**
     * Get schedules for a specific day type
     * @param dayType 0=workday, 1=saturday, 2=sunday/holiday
     */
    public List<BusSchedule> getSchedulesForDayType(int dayType) {
        List<BusSchedule> result = new ArrayList<BusSchedule>();
        for (BusSchedule schedule : schedules) {
            if (schedule.dayType == dayType) {
                result.add(schedule);
            }
        }
        return result;
    }

    /**
     * Get the next departure time after a given time (in minutes from midnight)
     * @param currentTime Time in minutes from midnight
     * @param dayType Day type (0=workday, 1=saturday, 2=sunday/holiday)
     * @return Next schedule, or null if no more departures today
     */
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

    /**
     * Get the number of points in this line
     */
    public int getPointCount() {
        return path.size();
    }

    /**
     * Get the number of stops on this line
     */
    public int getStopCount() {
        return stops.size();
    }

    /**
     * Get the number of schedules for this line
     */
    public int getScheduleCount() {
        return schedules.size();
    }

    /**
     * Check if this line contains a specific stop
     */
    public boolean hasStop(BusStop stop) {
        return stops.contains(stop);
    }

    /**
     * Check if this line contains a stop with the given ID
     */
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
