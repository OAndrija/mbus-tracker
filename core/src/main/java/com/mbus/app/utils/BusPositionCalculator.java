package com.mbus.app.utils;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BusPositionCalculator {

    private static final float STOP_WAIT_TIME_MINUTES = 0.5f;

    public static class ActiveBusInfo {
        public final BusLine line;
        public final BusSchedule schedule;
        public final int currentStopIndex;
        public final int nextStopIndex;
        public final float segmentProgress;
        public final int minutesUntilNextStop;
        public final boolean isWaitingAtStop;

        public ActiveBusInfo(BusLine line, BusSchedule schedule,
                             int currentStopIndex, int nextStopIndex,
                             float segmentProgress, int minutesUntilNextStop,
                             boolean isWaitingAtStop) {
            this.line = line;
            this.schedule = schedule;
            this.currentStopIndex = currentStopIndex;
            this.nextStopIndex = nextStopIndex;
            this.segmentProgress = segmentProgress;
            this.minutesUntilNextStop = minutesUntilNextStop;
            this.isWaitingAtStop = isWaitingAtStop;
        }
    }

    public static int getCurrentTimeMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /**
     * Get current time with sub-minute precision (includes seconds)
     * @return Time in minutes as a float (e.g., 90.5 for 1:30:30)
     */
    public static float getCurrentTimeMinutesWithSeconds() {
        Calendar cal = Calendar.getInstance();
        int hours = cal.get(Calendar.HOUR_OF_DAY);
        int minutes = cal.get(Calendar.MINUTE);
        int seconds = cal.get(Calendar.SECOND);
        return hours * 60.0f + minutes + (seconds / 60.0f);
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

    /**
     * Get active buses using current real-time (with seconds precision)
     */
    public static List<ActiveBusInfo> getActiveBuses(List<BusLine> lines,
                                                     int currentTime,
                                                     int dayType) {
        float preciseTime = getCurrentTimeMinutesWithSeconds();
        return getActiveBusesAtTime(lines, preciseTime, dayType);
    }

    /**
     * Get active buses at a specific time (with sub-minute precision)
     */
    public static List<ActiveBusInfo> getActiveBusesAtTime(List<BusLine> lines,
                                                           float preciseTime,
                                                           int dayType) {
        List<ActiveBusInfo> activeBuses = new ArrayList<ActiveBusInfo>();

        for (BusLine line : lines) {
            for (BusSchedule schedule : line.getSchedules()) {
                if (schedule.dayType != dayType) continue;

                List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
                if (stopTimes.size() < 2) continue;

                float departureTime = (float) schedule.departureTime;
                int lastStopTime = stopTimes.get(stopTimes.size() - 1).arrivalTime;
                float finalArrival = lastStopTime + STOP_WAIT_TIME_MINUTES;

                if (preciseTime >= departureTime && preciseTime <= finalArrival) {
                    ActiveBusInfo activeBus = calculateBusSegment(line, schedule, preciseTime);
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
                                                     float preciseCurrentTime) {
        List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();
        if (stopTimes.size() < 2) return null;

        BusSchedule.StopTime firstStop = stopTimes.get(0);
        int firstStopArrival = firstStop.arrivalTime;

        if (preciseCurrentTime < firstStopArrival) {
            float departureTime = (float) schedule.departureTime;
            float totalTime = firstStopArrival - departureTime;
            float elapsedTime = preciseCurrentTime - departureTime;

            if (totalTime > 0) {
                float progress = elapsedTime / totalTime;
                progress = Math.max(0f, Math.min(1f, progress));
                int minutesUntilNext = (int) Math.ceil(firstStopArrival - preciseCurrentTime);

                return new ActiveBusInfo(line, schedule, -1, 0,
                    progress, minutesUntilNext, false);
            }
        }

        for (int i = 0; i < stopTimes.size(); i++) {
            BusSchedule.StopTime currentStop = stopTimes.get(i);
            float currentStopArrival = (float) currentStop.arrivalTime;
            float currentStopDeparture = currentStopArrival + STOP_WAIT_TIME_MINUTES;

            if (preciseCurrentTime >= currentStopArrival && preciseCurrentTime < currentStopDeparture) {
                float waitProgress = (preciseCurrentTime - currentStopArrival) / STOP_WAIT_TIME_MINUTES;
                waitProgress = Math.max(0f, Math.min(1f, waitProgress));

                return new ActiveBusInfo(line, schedule, i, i,
                    waitProgress, 0, true);
            }

            if (i < stopTimes.size() - 1) {
                BusSchedule.StopTime nextStop = stopTimes.get(i + 1);
                float nextStopArrival = (float) nextStop.arrivalTime;

                if (preciseCurrentTime >= currentStopDeparture && preciseCurrentTime < nextStopArrival) {
                    float travelTime = nextStopArrival - currentStopDeparture;
                    float elapsedTravelTime = preciseCurrentTime - currentStopDeparture;

                    float progress = travelTime > 0 ? elapsedTravelTime / travelTime : 1f;
                    progress = Math.max(0f, Math.min(1f, progress));

                    int minutesUntilNext = (int) Math.ceil(nextStopArrival - preciseCurrentTime);

                    return new ActiveBusInfo(line, schedule, i, i + 1,
                        progress, minutesUntilNext, false);
                }
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
