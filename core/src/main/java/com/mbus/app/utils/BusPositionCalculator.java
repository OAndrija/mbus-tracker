package com.mbus.app.utils;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.Geolocation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Utility class for calculating real-time bus positions and schedule information
 */
public class BusPositionCalculator {

    /**
     * Represents a bus currently in service
     */
    public static class ActiveBus {
        public final BusLine line;
        public final BusSchedule schedule;
        public final Geolocation currentPosition;
        public final float progressAlongRoute; // 0.0 to 1.0
        public final int nextStopIndex;
        public final int minutesUntilNextStop;

        public ActiveBus(BusLine line, BusSchedule schedule, Geolocation position,
                         float progress, int nextStopIndex, int minutesUntilNextStop) {
            this.line = line;
            this.schedule = schedule;
            this.currentPosition = position;
            this.progressAlongRoute = progress;
            this.nextStopIndex = nextStopIndex;
            this.minutesUntilNextStop = minutesUntilNextStop;
        }
    }

    /**
     * Get current time in minutes from midnight
     */
    public static int getCurrentTimeMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /**
     * Get current day type (0=workday, 1=saturday, 2=sunday/holiday)
     */
    public static int getCurrentDayType() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        // Check if it's a public holiday (simplified - you'd need a proper holiday calendar)
        // For now, just check day of week
        if (dayOfWeek == Calendar.SUNDAY) {
            return 2; // Sunday/Holiday
        } else if (dayOfWeek == Calendar.SATURDAY) {
            return 1; // Saturday
        } else {
            return 0; // Workday
        }
    }

    /**
     * Find all buses currently active on the network
     */
    public static List<ActiveBus> getActiveBuses(List<BusLine> lines,
                                                 int currentTime,
                                                 int dayType) {
        List<ActiveBus> activeBuses = new ArrayList<ActiveBus>();

        for (BusLine line : lines) {
            for (BusSchedule schedule : line.getSchedules()) {
                if (schedule.dayType != dayType) continue;

                // Check if this bus is currently running
                List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
                if (stopTimes.isEmpty()) continue;

                int firstStopTime = stopTimes.get(0).arrivalTime;
                int lastStopTime = stopTimes.get(stopTimes.size() - 1).arrivalTime;

                if (currentTime >= firstStopTime && currentTime <= lastStopTime) {
                    // Bus is active, calculate its position
                    ActiveBus activeBus = calculateBusPosition(line, schedule, currentTime);
                    if (activeBus != null) {
                        activeBuses.add(activeBus);
                    }
                }
            }
        }

        return activeBuses;
    }

    /**
     * Calculate the current position of a bus along its route
     */
    public static ActiveBus calculateBusPosition(BusLine line,
                                                 BusSchedule schedule,
                                                 int currentTime) {
        List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
        if (stopTimes.isEmpty()) return null;

        // Find which segment the bus is on
        int nextStopIndex = -1;
        BusSchedule.StopTime prevStop = null;
        BusSchedule.StopTime nextStop = null;

        for (int i = 0; i < stopTimes.size(); i++) {
            BusSchedule.StopTime stopTime = stopTimes.get(i);
            if (currentTime < stopTime.arrivalTime) {
                nextStopIndex = i;
                nextStop = stopTime;
                if (i > 0) {
                    prevStop = stopTimes.get(i - 1);
                }
                break;
            }
        }

        // If no next stop found, bus has completed its route
        if (nextStopIndex == -1) return null;

        // If at first stop, use first stop position
        if (prevStop == null) {
            prevStop = stopTimes.get(0);
            Geolocation position = findStopPosition(line, prevStop.stopId);
            float progress = (float) prevStop.sequenceNumber / stopTimes.size();
            return new ActiveBus(line, schedule, position, progress, 0,
                nextStop.arrivalTime - currentTime);
        }

        // Interpolate position between two stops
        Geolocation prevPos = findStopPosition(line, prevStop.stopId);
        Geolocation nextPos = findStopPosition(line, nextStop.stopId);

        if (prevPos == null || nextPos == null) return null;

        // Calculate time-based interpolation factor
        int totalTime = nextStop.arrivalTime - prevStop.arrivalTime;
        int elapsedTime = currentTime - prevStop.arrivalTime;
        float interpolation = totalTime > 0 ? (float) elapsedTime / totalTime : 0f;

        // Interpolate position
        double lat = prevPos.lat + (nextPos.lat - prevPos.lat) * interpolation;
        double lng = prevPos.lng + (nextPos.lng - prevPos.lng) * interpolation;
        Geolocation currentPos = new Geolocation(lat, lng);

        // Calculate overall progress
        float progress = (prevStop.sequenceNumber + interpolation) / stopTimes.size();

        int minutesUntilNext = nextStop.arrivalTime - currentTime;

        return new ActiveBus(line, schedule, currentPos, progress,
            nextStopIndex, minutesUntilNext);
    }

    /**
     * Find the geographic position of a stop on a line
     */
    private static Geolocation findStopPosition(BusLine line, int stopId) {
        for (int i = 0; i < line.getStops().size(); i++) {
            if (line.getStops().get(i).idAvpost == stopId) {
                return line.getStops().get(i).geo;
            }
        }
        return null;
    }

    /**
     * Calculate distance between two geolocations in meters
     */
    public static double calculateDistance(Geolocation loc1, Geolocation loc2) {
        final int R = 6371000; // Earth's radius in meters

        double lat1Rad = Math.toRadians(loc1.lat);
        double lat2Rad = Math.toRadians(loc2.lat);
        double deltaLat = Math.toRadians(loc2.lat - loc1.lat);
        double deltaLng = Math.toRadians(loc2.lng - loc1.lng);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Format time difference as a human-readable string
     */
    public static String formatTimeDifference(int minutes) {
        if (minutes < 0) return "Departed";
        if (minutes == 0) return "Now";
        if (minutes == 1) return "1 min";
        if (minutes < 60) return minutes + " min";

        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) {
            return hours + "h";
        } else {
            return hours + "h " + mins + "m";
        }
    }
}
