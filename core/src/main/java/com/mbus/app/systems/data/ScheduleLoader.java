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

        for (BusLine line : lines) {
            if (line.getStops().isEmpty()) continue;

            boolean isUrbanLine = line.lineId < 100;

            int routeDuration = estimateRouteDuration(line.getStops().size());

            for (int dayType = 0; dayType <= 2; dayType++) {
                if (isUrbanLine) {
                    schedules.addAll(generateMultiTripSchedule(
                        line, dayType, scheduleIdCounter,
                        6 * 60, 24 * 60,
                        2, 60, routeDuration, random));
                    scheduleIdCounter += 40;
                } else {
                    schedules.addAll(generateMultiTripSchedule(
                        line, dayType, scheduleIdCounter,
                        6 * 60, 23 * 60,
                        2, 90, routeDuration, random));
                    scheduleIdCounter += 30;
                }
            }
        }

        Gdx.app.log(TAG, "Generated " + schedules.size() + " realistic schedules");
        return schedules;
    }

    private static List<BusSchedule> generateMultiTripSchedule(
        BusLine line, int dayType, int startScheduleId,
        int serviceStartTime, int serviceEndTime,
        int numBuses, int tripInterval, int routeDuration,
        Random random) {

        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleId = startScheduleId;

        for (int busNum = 0; busNum < numBuses; busNum++) {
            int initialDeparture = serviceStartTime + (busNum * (tripInterval / numBuses));

            int currentDeparture = initialDeparture;

            while (currentDeparture + routeDuration <= serviceEndTime) {
                List<BusSchedule.StopTime> stopTimes = calculateStopTimes(
                    line.getStops(), currentDeparture, random);

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
            }
        }

        Gdx.app.log(TAG, "Generated " + schedules.size() + " trips for line " +
            line.lineId + " on day type " + dayType);

        return schedules;
    }

    private static int estimateRouteDuration(int numStops) {
        return numStops;
    }

    private static List<BusSchedule.StopTime> calculateStopTimes(
        List<BusStop> stops, int departureTime, Random random) {

        List<BusSchedule.StopTime> stopTimes = new ArrayList<BusSchedule.StopTime>();
        int currentTime = departureTime;

        for (int i = 0; i < stops.size(); i++) {
            BusStop stop = stops.get(i);

            // Add travel time BEFORE arriving at each stop (including the first one!)
            if (i == 0) {
                // Travel from start of route to first stop
                int travelSeconds = 30 + random.nextInt(31);
                int travelMinutes = travelSeconds / 60;

                if (random.nextFloat() < 0.3f) {
                    travelMinutes += 1;
                }

                if (travelMinutes == 0) {
                    travelMinutes = 1;
                }

                currentTime += travelMinutes;
            }

            // Add the stop with current arrival time
            stopTimes.add(new BusSchedule.StopTime(stop.idAvpost, i, currentTime));

            // Add travel time to next stop (except for last stop)
            if (i < stops.size() - 1) {
                int travelSeconds = 30 + random.nextInt(31);
                int travelMinutes = travelSeconds / 60;

                if (random.nextFloat() < 0.3f) {
                    travelMinutes += 1;
                }

                if (travelMinutes == 0) {
                    travelMinutes = 1;
                }

                currentTime += travelMinutes;
            }
        }

        return stopTimes;
    }
}
