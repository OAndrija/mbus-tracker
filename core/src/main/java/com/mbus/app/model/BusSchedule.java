package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusSchedule {

    public final int scheduleId;
    public final int lineId;
    public final int variantId;
    public final int direction;

    // 0 = workday, 1 = saturday, 2 = sunday/holiday
    public final int dayType;

    // 06:30 = 390 minutes, 14:45 = 885 minutes
    public final int departureTime;

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

    public List<StopTime> getStopTimes() {
        return stopTimes;
    }

    public int getArrivalTimeAtStop(int stopId) {
        for (StopTime st : stopTimes) {
            if (st.stopId == stopId) {
                return st.arrivalTime;
            }
        }
        return -1;
    }

    public StopTime getStopTime(int stopId) {
        for (StopTime st : stopTimes) {
            if (st.stopId == stopId) {
                return st;
            }
        }
        return null;
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
