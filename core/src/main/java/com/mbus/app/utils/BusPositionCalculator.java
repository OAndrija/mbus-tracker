package com.mbus.app.utils;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BusPositionCalculator {

    // Bus waits at each stop for 5 seconds (converted to minutes)
    private static final float STOP_WAIT_TIME_MINUTES = 5f / 60f; // 5 seconds

    public static class ActiveBusInfo {
        public final BusLine line;
        public final BusSchedule schedule;
        public final int currentStopIndex;
        public final int nextStopIndex;
        public final float segmentProgress;
        public final int minutesUntilNextStop;

        public ActiveBusInfo(BusLine line, BusSchedule schedule,
                             int currentStopIndex, int nextStopIndex,
                             float segmentProgress, int minutesUntilNextStop) {
            this.line = line;
            this.schedule = schedule;
            this.currentStopIndex = currentStopIndex;
            this.nextStopIndex = nextStopIndex;
            this.segmentProgress = segmentProgress;
            this.minutesUntilNextStop = minutesUntilNextStop;
        }
    }

    public static int getCurrentTimeMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    public static int getCurrentDayType() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        if (dayOfWeek == Calendar.SUNDAY) {
            return 2;
        } else if (dayOfWeek == Calendar.SATURDAY) {
            return 1;
        } else {
            return 0;
        }
    }

    public static List<ActiveBusInfo> getActiveBuses(List<BusLine> lines,
                                                     int currentTime,
                                                     int dayType) {
        List<ActiveBusInfo> activeBuses = new ArrayList<ActiveBusInfo>();

        for (BusLine line : lines) {
            for (BusSchedule schedule : line.getSchedules()) {
                if (schedule.dayType != dayType) continue;

                List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
                if (stopTimes.size() < 2) continue;

                int departureTime = schedule.departureTime;
                int lastStopTime = stopTimes.get(stopTimes.size() - 1).arrivalTime;

                // Bus is active from departure until it reaches the final stop
                if (currentTime >= departureTime && currentTime <= lastStopTime) {
                    ActiveBusInfo activeBus = calculateBusSegment(line, schedule, currentTime);
                    if (activeBus != null) {
                        activeBuses.add(activeBus);
                    }
                }
            }
        }

        return activeBuses;
    }

    private static ActiveBusInfo calculateBusSegment(BusLine line,
                                                     BusSchedule schedule,
                                                     int currentTime) {
        List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
        if (stopTimes.size() < 2) return null;

        // Check if bus is traveling from departure point to first stop
        BusSchedule.StopTime firstStop = stopTimes.get(0);
        int firstStopArrival = firstStop.arrivalTime;

        if (currentTime < firstStopArrival) {
            // Bus is en route to first stop
            int departureTime = schedule.departureTime;
            int totalTime = firstStopArrival - departureTime;
            int elapsedTime = currentTime - departureTime;

            if (totalTime > 0) {
                float progress = (float) elapsedTime / totalTime;
                progress = Math.max(0f, Math.min(1f, progress));
                int minutesUntilNext = firstStopArrival - currentTime;

                return new ActiveBusInfo(line, schedule, 0, 0,
                    progress, minutesUntilNext);
            }
        }

        // Check each segment between stops
        for (int i = 0; i < stopTimes.size() - 1; i++) {
            BusSchedule.StopTime currentStop = stopTimes.get(i);
            BusSchedule.StopTime nextStop = stopTimes.get(i + 1);

            int currentStopArrival = currentStop.arrivalTime;
            int nextStopArrival = nextStop.arrivalTime;

            // Bus travels between stops - NO WAITING, start moving immediately
            if (currentTime >= currentStopArrival && currentTime <= nextStopArrival) {
                int travelTime = nextStopArrival - currentStopArrival;
                int elapsedTravelTime = currentTime - currentStopArrival;

                float progress = travelTime > 0 ? (float) elapsedTravelTime / travelTime : 1f;
                progress = Math.max(0f, Math.min(1f, progress));

                int minutesUntilNext = nextStopArrival - currentTime;

                return new ActiveBusInfo(line, schedule, i, i + 1, progress, minutesUntilNext);
            }
        }

        return null;
    }

    public static String formatTime(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }
}
