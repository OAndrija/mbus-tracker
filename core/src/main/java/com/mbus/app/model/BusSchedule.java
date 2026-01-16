package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single scheduled trip for a bus line.
 * Contains departure times from the starting point and calculated arrival times at each stop.
 */
public class BusSchedule {

    public final int scheduleId;
    public final int lineId;
    public final int variantId;
    public final int direction;

    // Day type: 0 = workday, 1 = saturday, 2 = sunday/holiday
    public final int dayType;

    // Departure time from the first stop (in minutes from midnight)
    // Example: 06:30 = 390 minutes, 14:45 = 885 minutes
    public final int departureTime;

    // Ordered list of stop times (one for each stop on the line)
    private final List<StopTime> stopTimes;

    public BusSchedule(int scheduleId,
                       int lineId,
                       int variantId,
                       int direction,
                       int dayType,
                       int departureTime,
                       List<StopTime> stopTimes) {
        this.scheduleId = scheduleId;
        this.lineId = lineId;
        this.variantId = variantId;
        this.direction = direction;
        this.dayType = dayType;
        this.departureTime = departureTime;
        this.stopTimes = stopTimes != null
            ? Collections.unmodifiableList(new ArrayList<StopTime>(stopTimes))
            : Collections.unmodifiableList(new ArrayList<StopTime>());
    }

    /**
     * Get all stop times for this schedule
     */
    public List<StopTime> getStopTimes() {
        return stopTimes;
    }

    /**
     * Get the arrival time at a specific stop
     * @param stopId The ID of the bus stop
     * @return Time in minutes from midnight, or -1 if stop not found
     */
    public int getArrivalTimeAtStop(int stopId) {
        for (StopTime st : stopTimes) {
            if (st.stopId == stopId) {
                return st.arrivalTime;
            }
        }
        return -1;
    }

    /**
     * Get the StopTime for a specific stop
     */
    public StopTime getStopTime(int stopId) {
        for (StopTime st : stopTimes) {
            if (st.stopId == stopId) {
                return st;
            }
        }
        return null;
    }

    /**
     * Convert minutes from midnight to HH:MM format
     */
    public static String formatTime(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    /**
     * Parse HH:MM format to minutes from midnight
     */
    public static int parseTime(String timeStr) {
        String[] parts = timeStr.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /**
     * Get departure time as formatted string (HH:MM)
     */
    public String getDepartureTimeFormatted() {
        return formatTime(departureTime);
    }

    /**
     * Get day type as string
     */
    public String getDayTypeString() {
        switch (dayType) {
            case 0: return "Workday";
            case 1: return "Saturday";
            case 2: return "Sunday/Holiday";
            default: return "Unknown";
        }
    }

    /**
     * Check if this schedule runs on a given day type
     */
    public boolean runsOnDayType(int dayType) {
        return this.dayType == dayType;
    }

    @Override
    public String toString() {
        return "BusSchedule{" +
            "lineId=" + lineId +
            ", direction=" + direction +
            ", dayType=" + getDayTypeString() +
            ", departure=" + getDepartureTimeFormatted() +
            ", stops=" + stopTimes.size() +
            '}';
    }

    /**
     * Represents the arrival time at a specific stop in a schedule
     */
    public static class StopTime {
        public final int stopId;
        public final int sequenceNumber;  // Order in the route (0-based)
        public final int arrivalTime;     // Minutes from midnight

        public StopTime(int stopId, int sequenceNumber, int arrivalTime) {
            this.stopId = stopId;
            this.sequenceNumber = sequenceNumber;
            this.arrivalTime = arrivalTime;
        }

        public String getArrivalTimeFormatted() {
            return formatTime(arrivalTime);
        }

        @Override
        public String toString() {
            return "StopTime{" +
                "stopId=" + stopId +
                ", seq=" + sequenceNumber +
                ", time=" + getArrivalTimeFormatted() +
                '}';
        }
    }
}
