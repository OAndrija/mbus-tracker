package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusSchedule {

    public final int scheduleId;
    public final int lineId;
    public final int variantId;
    public final int direction;

    // 0 = workday, 1 = saturday, 2 = sunday/holiday
    public final int dayType;

    // 06:30 = 390 minutes, 14:45 = 885 minutes
    public final int departureTime;

    // Stops that drive the bus animation.
    private final List<StopTime> stopTimes;

    // stopId -> arrival minute, for every stop on the line (a superset of stopTimes).
    private final Map<Integer, Integer> arrivalByStop;

    public BusSchedule(int scheduleId,
                       int lineId,
                       int variantId,
                       int direction,
                       int dayType,
                       int departureTime,
                       List<StopTime> stopTimes) {
        this(scheduleId, lineId, variantId, direction, dayType, departureTime, stopTimes, null);
    }

    public BusSchedule(int scheduleId,
                       int lineId,
                       int variantId,
                       int direction,
                       int dayType,
                       int departureTime,
                       List<StopTime> stopTimes,
                       Map<Integer, Integer> passingTimes) {
        this.scheduleId = scheduleId;
        this.lineId = lineId;
        this.variantId = variantId;
        this.direction = direction;
        this.dayType = dayType;
        this.departureTime = departureTime;
        this.stopTimes = stopTimes != null
            ? Collections.unmodifiableList(new ArrayList<>(stopTimes))
            : Collections.unmodifiableList(new ArrayList<>());

        this.arrivalByStop = new HashMap<>();
        if (passingTimes != null) {
            arrivalByStop.putAll(passingTimes);
        }
        for (StopTime st : this.stopTimes) {
            arrivalByStop.put(st.stopId, st.arrivalTime);
        }
    }

    public List<StopTime> getStopTimes() {
        return stopTimes;
    }

    public int getArrivalTimeAtStop(int stopId) {
        Integer arrival = arrivalByStop.get(stopId);
        return arrival != null ? arrival : -1;
    }

    public static String formatTime(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    public static int parseTime(String timeStr) {
        String[] parts = timeStr.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    public String getDepartureTimeFormatted() {
        return formatTime(departureTime);
    }

    public String getDayTypeString() {
        switch (dayType) {
            case 0: return "Workday";
            case 1: return "Saturday";
            case 2: return "Sunday/Holiday";
            default: return "Unknown";
        }
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

    public static class StopTime {
        public final int stopId;
        public final int sequenceNumber;
        public final int arrivalTime;

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
