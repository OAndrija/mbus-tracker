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

/**
 * Loads bus schedules from JSON files.
 * Expected format:
 * {
 *   "schedules": [
 *     {
 *       "scheduleId": 1,
 *       "lineId": 1,
 *       "variantId": 0,
 *       "direction": 1,
 *       "dayType": 0,
 *       "departureTime": "06:30",
 *       "stopTimes": [
 *         {"stopId": 123, "sequence": 0, "arrivalTime": "06:30"},
 *         {"stopId": 124, "sequence": 1, "arrivalTime": "06:35"}
 *       ]
 *     }
 *   ]
 * }
 */
public class ScheduleLoader {

    private static final String TAG = "ScheduleLoader";

    /**
     * Load schedules from a JSON file
     */
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

    /**
     * Assign schedules to bus lines
     * This modifies the lines list by creating new BusLine objects with schedules
     */
    public static List<BusLine> assignSchedulesToLines(List<BusLine> lines,
                                                       List<BusSchedule> schedules) {
        // Group schedules by line key (lineId_variantId_direction)
        Map<String, List<BusSchedule>> schedulesByLine = new HashMap<String, List<BusSchedule>>();

        for (BusSchedule schedule : schedules) {
            String key = getLineKey(schedule.lineId, schedule.variantId, schedule.direction);
            if (!schedulesByLine.containsKey(key)) {
                schedulesByLine.put(key, new ArrayList<BusSchedule>());
            }
            schedulesByLine.get(key).add(schedule);
        }

        // Create new BusLine objects with schedules
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

    /**
     * Generate example schedule data for testing
     * Creates schedules with departures every 15-30 minutes during operating hours
     */
    public static List<BusSchedule> generateExampleSchedules(List<BusLine> lines) {
        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleIdCounter = 1;

        for (BusLine line : lines) {
            if (line.getStops().isEmpty()) continue;

            // Generate schedules for workdays (dayType = 0)
            schedules.addAll(generateSchedulesForLine(
                line, 0, scheduleIdCounter, 6 * 60, 22 * 60, 20));
            scheduleIdCounter += 40;

            // Generate schedules for saturdays (dayType = 1)
            schedules.addAll(generateSchedulesForLine(
                line, 1, scheduleIdCounter, 7 * 60, 21 * 60, 30));
            scheduleIdCounter += 30;

            // Generate schedules for sundays (dayType = 2)
            schedules.addAll(generateSchedulesForLine(
                line, 2, scheduleIdCounter, 8 * 60, 20 * 60, 40));
            scheduleIdCounter += 20;
        }

        Gdx.app.log(TAG, "Generated " + schedules.size() + " example schedules");
        return schedules;
    }

    private static List<BusSchedule> generateSchedulesForLine(BusLine line, int dayType,
                                                              int startScheduleId,
                                                              int startTime, int endTime,
                                                              int interval) {
        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleId = startScheduleId;

        for (int time = startTime; time < endTime; time += interval) {
            List<BusSchedule.StopTime> stopTimes = new ArrayList<BusSchedule.StopTime>();

            // Assume 2 minutes between stops on average
            int currentTime = time;
            List<BusStop> stops = line.getStops();

            for (int i = 0; i < stops.size(); i++) {
                BusStop stop = stops.get(i);
                stopTimes.add(new BusSchedule.StopTime(stop.idAvpost, i, currentTime));
                currentTime += 2; // 2 minutes to next stop
            }

            schedules.add(new BusSchedule(
                scheduleId++,
                line.lineId,
                line.variantId,
                line.direction,
                dayType,
                time,
                stopTimes
            ));
        }

        return schedules;
    }
}
