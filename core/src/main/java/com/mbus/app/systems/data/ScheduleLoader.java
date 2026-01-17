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

            if (isUrbanLine) {
                schedules.addAll(generateRealisticSchedule(
                    line, 0, scheduleIdCounter, 5 * 60, 23 * 60,
                    10, 15, 20, 30, random));
                scheduleIdCounter += 60;
            } else {
                schedules.addAll(generateRealisticSchedule(
                    line, 0, scheduleIdCounter, 6 * 60, 21 * 60,
                    15, 20, 30, 45, random));
                scheduleIdCounter += 40;
            }

            if (isUrbanLine) {
                schedules.addAll(generateRealisticSchedule(
                    line, 1, scheduleIdCounter, 6 * 60, 22 * 60,
                    15, 20, 25, 35, random));
                scheduleIdCounter += 45;
            } else {
                schedules.addAll(generateRealisticSchedule(
                    line, 1, scheduleIdCounter, 7 * 60, 20 * 60,
                    20, 30, 40, 60, random));
                scheduleIdCounter += 25;
            }

            if (isUrbanLine) {
                schedules.addAll(generateRealisticSchedule(
                    line, 2, scheduleIdCounter, 7 * 60, 21 * 60,
                    20, 25, 30, 40, random));
                scheduleIdCounter += 35;
            } else {
                schedules.addAll(generateRealisticSchedule(
                    line, 2, scheduleIdCounter, 8 * 60, 19 * 60,
                    30, 45, 60, 90, random));
                scheduleIdCounter += 20;
            }
        }

        Gdx.app.log(TAG, "Generated " + schedules.size() + " realistic schedules");
        return schedules;
    }

    private static List<BusSchedule> generateRealisticSchedule(
        BusLine line, int dayType, int startScheduleId,
        int startTime, int endTime,
        int peakIntervalMin, int peakIntervalMax,
        int offPeakIntervalMin, int offPeakIntervalMax,
        Random random) {

        List<BusSchedule> schedules = new ArrayList<BusSchedule>();
        int scheduleId = startScheduleId;

        boolean isPeakHour;
        int currentTime = startTime;

        while (currentTime < endTime) {
            int hour = currentTime / 60;
            if (dayType == 0) {
                isPeakHour = (hour >= 6 && hour < 9) || (hour >= 15 && hour < 18);
            } else {
                isPeakHour = false;
            }

            List<BusSchedule.StopTime> stopTimes = calculateStopTimes(
                line.getStops(), currentTime, random);

            schedules.add(new BusSchedule(
                scheduleId++,
                line.lineId,
                line.variantId,
                line.direction,
                dayType,
                currentTime,
                stopTimes
            ));

            int intervalMin, intervalMax;
            if (isPeakHour) {
                intervalMin = peakIntervalMin;
                intervalMax = peakIntervalMax;
            } else {
                intervalMin = offPeakIntervalMin;
                intervalMax = offPeakIntervalMax;
            }

            int interval = intervalMin + random.nextInt(intervalMax - intervalMin + 1);
            currentTime += interval;
        }

        return schedules;
    }

    private static List<BusSchedule.StopTime> calculateStopTimes(List<BusStop> stops, int departureTime, Random random) {

        List<BusSchedule.StopTime> stopTimes = new ArrayList<BusSchedule.StopTime>();
        int currentTime = departureTime;

        for (int i = 0; i < stops.size(); i++) {
            BusStop stop = stops.get(i);
            stopTimes.add(new BusSchedule.StopTime(stop.idAvpost, i, currentTime));

            if (i < stops.size() - 1) {
                int baseTime = 1 + random.nextInt(3);

                int trafficDelay = random.nextInt(3);

                currentTime += baseTime + trafficDelay;
            }
        }

        return stopTimes;
    }
}
