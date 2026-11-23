package com.mbus.app.systems.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleLoader {

    private static final String TAG = "ScheduleLoader";
    private static final double MIN_STOP_DISTANCE = 0.003;

    // Average between stops, so that a bus that stops everywhere still drives at city speed
    private static final double TRAVEL_SPEED_KMH = 40;
    private static final double KM_PER_DEGREE = 111.32;

    // 0 = workday, 1 = saturday, 2 = sunday/holiday
    private static final int[] DAY_TYPES = {0, 1, 2};

    public static List<BusSchedule> loadSchedulesFromFile(String filePath) {
        List<BusSchedule> schedules = new ArrayList<>();

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

            List<BusSchedule.StopTime> stopTimes = new ArrayList<>();
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
        Map<String, List<BusSchedule>> schedulesByLine = new HashMap<>();

        for (BusSchedule schedule : schedules) {
            String key = getLineKey(schedule.lineId, schedule.variantId, schedule.direction);
            if (!schedulesByLine.containsKey(key)) {
                schedulesByLine.put(key, new ArrayList<>());
            }
            schedulesByLine.get(key).add(schedule);
        }

        List<BusLine> updatedLines = new ArrayList<>();
        for (BusLine line : lines) {
            String key = getLineKey(line.lineId, line.variantId, line.direction);
            List<BusSchedule> lineSchedules = schedulesByLine.get(key);

            if (lineSchedules == null) {
                lineSchedules = new ArrayList<>();
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
        List<BusSchedule> schedules = new ArrayList<>();
        int scheduleIdCounter = 1;

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

            Gdx.app.log(TAG, "Processing line " + line.lineId + " (variant=" + line.variantId +
                ", dir=" + line.direction + ", stops=" + line.getStops().size() +
                ", scheduled=" + scheduledStops.size() +
                ", duration=" + routeDuration + " min)");

            int beforeCount = schedules.size();
            int tripInterval = isUrbanLine ? routeDuration : routeDuration * 2;

            for (int dayType : DAY_TYPES) {
                List<BusSchedule> daySchedules = generateMultiTripSchedule(
                    line, scheduledStops, dayType, scheduleIdCounter,
                    0, 24 * 60,
                    2,
                    tripInterval,
                    routeDuration);
                schedules.addAll(daySchedules);
                scheduleIdCounter += daySchedules.size();
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

        List<BusStop> filtered = new ArrayList<>();

        for (BusStop current : stops) {
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
        int numBuses, int tripInterval, int routeDuration) {

        List<BusSchedule> schedules = new ArrayList<>();
        int scheduleId = startScheduleId;

        double[] pathPositions = cumulativeDistances(line.getPath());
        Map<Integer, Double> stopPositions = new HashMap<>();
        for (BusStop stop : line.getStops()) {
            stopPositions.put(stop.idAvpost, positionOnPath(stop.geo, line.getPath(), pathPositions));
        }

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
                    scheduledStops, currentDeparture, stopPositions);

                schedules.add(new BusSchedule(
                    scheduleId++,
                    line.lineId,
                    line.variantId,
                    line.direction,
                    dayType,
                    currentDeparture,
                    stopTimes,
                    calculatePassingTimes(line.getStops(), stopTimes, stopPositions)
                ));

                currentDeparture += tripInterval;
                tripsForThisBus++;
            }

            Gdx.app.log(TAG, "    Bus #" + (busNum + 1) + " completed " + tripsForThisBus + " trips");
        }

        Gdx.app.log(TAG, "  Total schedules created for this line: " + schedules.size());

        return schedules;
    }

    /**
     * Only well-spaced stops become timing points (see {@link #filterNearbyStops}), so the rest get
     * the time the bus passes them, interpolated between the timing points around them.
     */
    static Map<Integer, Integer> calculatePassingTimes(List<BusStop> stops,
                                                       List<BusSchedule.StopTime> timingPoints,
                                                       Map<Integer, Double> stopPositions) {
        Map<Integer, Integer> times = new HashMap<>();
        if (timingPoints.isEmpty()) return times;

        for (BusStop stop : stops) {
            double position = stopPositions.get(stop.idAvpost);
            BusSchedule.StopTime before = null;
            BusSchedule.StopTime after = null;

            for (BusSchedule.StopTime point : timingPoints) {
                if (stopPositions.get(point.stopId) <= position) {
                    before = point;
                } else {
                    after = point;
                    break;
                }
            }

            if (before == null) {
                times.put(stop.idAvpost, after.arrivalTime);
            } else if (after == null) {
                times.put(stop.idAvpost, before.arrivalTime);
            } else {
                double from = stopPositions.get(before.stopId);
                double to = stopPositions.get(after.stopId);
                double t = (position - from) / (to - from);
                times.put(stop.idAvpost, (int) Math.round(
                    before.arrivalTime + t * (after.arrivalTime - before.arrivalTime)));
            }
        }

        return times;
    }

    /** In kilometers. */
    private static double[] cumulativeDistances(List<Geolocation> path) {
        double[] result = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            Geolocation from = path.get(i - 1);
            Geolocation to = path.get(i);
            double lngScale = Math.cos(Math.toRadians(from.lat));
            result[i] = result[i - 1]
                + KM_PER_DEGREE * Math.hypot(to.lat - from.lat, (to.lng - from.lng) * lngScale);
        }
        return result;
    }

    private static double positionOnPath(Geolocation geo, List<Geolocation> path, double[] positions) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        double lngScale = Math.cos(Math.toRadians(geo.lat));
        for (int i = 0; i < path.size(); i++) {
            // In metric proportions, so this agrees with the order the line's stops are sorted in.
            double dLat = path.get(i).lat - geo.lat;
            double dLng = (path.get(i).lng - geo.lng) * lngScale;
            double distance = dLat * dLat + dLng * dLng;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return positions.length == 0 ? 0 : positions[nearest];
    }

    private static int estimateRouteDuration(List<BusStop> stops, boolean isUrbanLine) {
        int numStops = stops.size();

        if (isUrbanLine) {
            return 5 + (int)(numStops * 1.5f);
        } else {
            return 8 + (int)(numStops * 2.0f);
        }
    }

    /** Travel times follow the road, which can be much longer than the straight line between two stops. */
    private static List<BusSchedule.StopTime> calculateStopTimes(
        List<BusStop> stops, int departureTime, Map<Integer, Double> stopPositions) {

        List<BusSchedule.StopTime> stopTimes = new ArrayList<>();
        int currentTime = departureTime;

        for (int i = 0; i < stops.size(); i++) {
            BusStop stop = stops.get(i);
            stopTimes.add(new BusSchedule.StopTime(stop.idAvpost, i, currentTime));

            if (i < stops.size() - 1) {
                double km = stopPositions.get(stops.get(i + 1).idAvpost) - stopPositions.get(stop.idAvpost);
                currentTime += (int) Math.max(1, Math.ceil(km / TRAVEL_SPEED_KMH * 60.0));
            }
        }

        return stopTimes;
    }
}
