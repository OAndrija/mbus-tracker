package com.mbus.app.systems.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ScheduleLoader {

    private static final String TAG = "ScheduleLoader";
    private static final double MIN_STOP_DISTANCE = 0.003;

    public static List<BusSchedule> loadSchedulesFromFile(String filePath) {
        List<BusSchedule> schedules = new ArrayList<BusSchedule>();

        try {
            FileHandle file = Gdx.files.internal(filePath);
            if (!file.exists()) {
                Gdx.app.error(TAG, "Schedule file not found: " + filePath);
                return schedules;
            }

            String jsonString = file.readString();
            JsonReader jsonReader = new JsonReader();
            JsonValue root = jsonReader.parse(jsonString);

            JsonValue schedulesArray = root.get("schedules");
            if (schedulesArray == null) {
                Gdx.app.error(TAG, "No 'schedules' array in JSON");
                return schedules;
            }

            for (JsonValue scheduleJson : schedulesArray) {
                BusSchedule schedule = parseSchedule(scheduleJson);
                if (schedule != null) {
                    schedules.add(schedule);
                }
            }

            Gdx.app.log(TAG, "Loaded " + schedules.size() + " schedules from " + filePath);

        } catch (Exception e) {
            Gdx.app.error(TAG, "Error loading schedules from " + filePath, e);
        }

        return schedules;
    }

    private static BusSchedule parseSchedule(JsonValue json) {
        try {
            int scheduleId = json.getInt("scheduleId");
            int lineId = json.getInt("lineId");
            int variantId = json.getInt("variantId", 0);
            int direction = json.getInt("direction", 1);
            int dayType = json.getInt("dayType", 0);

            String departureTimeStr = json.getString("departureTime");
            int departureTime = BusSchedule.parseTime(departureTimeStr);

            List<BusSchedule.StopTime> stopTimes = new ArrayList<BusSchedule.StopTime>();
            JsonValue stopTimesArray = json.get("stopTimes");

            if (stopTimesArray != null) {
                for (JsonValue stopTimeJson : stopTimesArray) {
                    int stopId = stopTimeJson.getInt("stopId");
                    int sequence = stopTimeJson.getInt("sequence");
                    String arrivalTimeStr = stopTimeJson.getString("arrivalTime");
                    int arrivalTime = BusSchedule.parseTime(arrivalTimeStr);

                    stopTimes.add(new BusSchedule.StopTime(stopId, sequence, arrivalTime));
                }
            }

            return new BusSchedule(scheduleId, lineId, variantId, direction,
                dayType, departureTime, stopTimes);

        } catch (Exception e) {
            Gdx.app.error(TAG, "Error parsing schedule", e);
            return null;
        }
    }

    public static List<BusLine> assignSchedulesToLines(List<BusLine> lines,
                                                       List<BusSchedule> schedules) {
        Map<String, List<BusSchedule>> schedulesByLine = new HashMap<String, List<BusSchedule>>();

        for (BusSchedule schedule : schedules) {
            String key = getLineKey(schedule.lineId, schedule.variantId, schedule.direction);
            if (!schedulesByLine.containsKey(key)) {
                schedulesByLine.put(key, new ArrayList<BusSchedule>());
            }
            schedulesByLine.get(key).add(schedule);
        }

        List<BusLine> updatedLines = new ArrayList<BusLine>();
        for (BusLine line : lines) {
            String key = getLineKey(line.lineId, line.variantId, line.direction);
            List<BusSchedule> lineSchedules = schedulesByLine.get(key);

            if (lineSchedules == null) {
                lineSchedules = new ArrayList<BusSchedule>();
            }

            BusLine updatedLine = new BusLine(
                line.lineId,
                line.variantId,
                line.direction,
                line.length,
                line.name,
                line.note,
                line.providerName,
                line.providerLink,
                line.getPath(),
                line.getOriginalCoordinates(),
                line.getStops(),
                lineSchedules
            );

            updatedLines.add(updatedLine);
        }

        Gdx.app.log(TAG, "Assigned schedules to " + updatedLines.size() + " lines");
        return updatedLines;
    }

    private static String getLineKey(int lineId, int variantId, int direction) {
        return lineId + "_" + variantId + "_" + direction;
    }

    public static List<BusSchedule> generateExampleSchedules(List<BusLine> lines) {
        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleIdCounter = 1;
        Random random = new Random(12345);

        Gdx.app.log(TAG, "=== STARTING SCHEDULE GENERATION ===");
        Gdx.app.log(TAG, "Total lines to process: " + lines.size());

        for (BusLine line : lines) {
            if (line.getStops().isEmpty()) {
                Gdx.app.log(TAG, "SKIPPING line " + line.lineId + " - no stops");
                continue;
            }

            boolean isUrbanLine = line.lineId < 100;

            List<BusStop> scheduledStops = filterNearbyStops(line.getStops());

            int routeDuration = estimateRouteDuration(scheduledStops, isUrbanLine);

            int dayType = 2;

            Gdx.app.log(TAG, "Processing line " + line.lineId + " (variant=" + line.variantId +
                ", dir=" + line.direction + ", stops=" + line.getStops().size() +
                ", scheduled=" + scheduledStops.size() +
                ", duration=" + routeDuration + " min)");

            int beforeCount = schedules.size();

            if (isUrbanLine) {
                schedules.addAll(generateMultiTripSchedule(
                    line, scheduledStops, dayType, scheduleIdCounter,
                    0, 24 * 60,
                    2,
                    routeDuration,
                    routeDuration, random));
                scheduleIdCounter += 50;
            } else {
                schedules.addAll(generateMultiTripSchedule(
                    line, scheduledStops, dayType, scheduleIdCounter,
                    0, 24 * 60,
                    2,
                    routeDuration * 2,
                    routeDuration, random));
                scheduleIdCounter += 50;
            }

            int afterCount = schedules.size();
            Gdx.app.log(TAG, "Line " + line.lineId + " generated " + (afterCount - beforeCount) + " schedules");
        }

        Gdx.app.log(TAG, "=== SCHEDULE GENERATION COMPLETE ===");
        Gdx.app.log(TAG, "Total schedules generated: " + schedules.size());
        return schedules;
    }

    private static List<BusStop> filterNearbyStops(List<BusStop> stops) {
        if (stops.isEmpty()) return stops;

        List<BusStop> filtered = new ArrayList<BusStop>();

        for (int i = 0; i < stops.size(); i++) {
            BusStop current = stops.get(i);
            boolean shouldSkip = false;

            for (BusStop existing : filtered) {
                double distance = calculateDistance(existing.geo.lat, existing.geo.lng,
                    current.geo.lat, current.geo.lng);

                if (distance < MIN_STOP_DISTANCE) {
                    shouldSkip = true;
                    break;
                }
            }

            if (!shouldSkip) {
                filtered.add(current);
            }
        }

        return filtered;
    }

    private static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat2 - lat1;
        double dLng = lng2 - lng1;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    private static List<BusSchedule> generateMultiTripSchedule(
        BusLine line, List<BusStop> scheduledStops, int dayType, int startScheduleId,
        int serviceStartTime, int serviceEndTime,
        int numBuses, int tripInterval, int routeDuration,
        Random random) {

        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleId = startScheduleId;

        int totalServiceTime = serviceEndTime - serviceStartTime;
        int maxTripsPerBus = (totalServiceTime - routeDuration) / tripInterval + 1;

        int actualBuses = Math.min(numBuses, 2);

        Gdx.app.log(TAG, "  generateMultiTripSchedule - actualBuses=" + actualBuses +
            ", tripInterval=" + tripInterval + ", routeDuration=" + routeDuration);

        for (int busNum = 0; busNum < actualBuses; busNum++) {
            int initialDeparture = serviceStartTime + (busNum * (tripInterval / actualBuses));

            int currentDeparture = initialDeparture;
            int tripsForThisBus = 0;

            Gdx.app.log(TAG, "    Bus #" + (busNum + 1) + " starting at " +
                BusSchedule.formatTime(initialDeparture));

            while (currentDeparture + routeDuration <= serviceEndTime && tripsForThisBus < maxTripsPerBus) {
                List<BusSchedule.StopTime> stopTimes = calculateStopTimes(
                    scheduledStops, currentDeparture, random);

                schedules.add(new BusSchedule(
                    scheduleId++,
                    line.lineId,
                    line.variantId,
                    line.direction,
                    dayType,
                    currentDeparture,
                    stopTimes
                ));

                currentDeparture += tripInterval;
                tripsForThisBus++;
            }

            Gdx.app.log(TAG, "    Bus #" + (busNum + 1) + " completed " + tripsForThisBus + " trips");
        }

        Gdx.app.log(TAG, "  Total schedules created for this line: " + schedules.size());

        return schedules;
    }

    private static int estimateRouteDuration(List<BusStop> stops, boolean isUrbanLine) {
        int numStops = stops.size();

        if (isUrbanLine) {
            return 5 + (int)(numStops * 1.5f);
        } else {
            return 8 + (int)(numStops * 2.0f);
        }
    }

    private static List<BusSchedule.StopTime> calculateStopTimes(
        List<BusStop> stops, int departureTime, Random random) {

        List<BusSchedule.StopTime> stopTimes = new ArrayList<BusSchedule.StopTime>();
        int currentTime = departureTime;

        for (int i = 0; i < stops.size(); i++) {
            BusStop stop = stops.get(i);

            stopTimes.add(new BusSchedule.StopTime(stop.idAvpost, i, currentTime));

            if (i < stops.size() - 1) {
                BusStop nextStop = stops.get(i + 1);
                double distanceInDegrees = calculateDistance(
                    stop.geo.lat, stop.geo.lng,
                    nextStop.geo.lat, nextStop.geo.lng
                );

                double distanceInKm = distanceInDegrees * 111.0;

                double travelTimeHours = distanceInKm / 50.0;
                double travelTimeMinutes = travelTimeHours * 60.0;

                if (distanceInKm < 1) {
                    travelTimeMinutes = 0.5 + (random.nextDouble() * 0.2);
                } else if (distanceInKm < 1.5) {
                    travelTimeMinutes = Math.max(0.5, travelTimeMinutes);
                } else {
                    travelTimeMinutes = Math.max(1.0, travelTimeMinutes);
                }

                int travelTimeMinutesInt = (int) Math.max(1, Math.round(travelTimeMinutes));

                currentTime += travelTimeMinutesInt;
            }
        }

        return stopTimes;
    }
}
