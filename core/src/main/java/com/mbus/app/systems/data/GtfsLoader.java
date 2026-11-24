package com.mbus.app.systems.data;

import com.badlogic.gdx.graphics.Color;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Service;
import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Geolocation;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads a timetable from a GTFS zip file, see https://gtfs.org/documentation/schedule/reference/ */
public final class GtfsLoader {

    private static final String BUS = "3";
    private static final Color DEFAULT_COLOR = Color.valueOf("5f6b7a");

    private final Map<String, byte[]> files;

    private GtfsLoader(Map<String, byte[]> files) {
        this.files = files;
    }

    /** Closes the stream. */
    public static Timetable load(InputStream zip) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(zip)) {
            byte[] buffer = new byte[16384];
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int n = in.read(buffer); n > 0; n = in.read(buffer)) out.write(buffer, 0, n);
                files.put(entry.getName(), out.toByteArray());
            }
        }
        return new GtfsLoader(files).load();
    }

    private Timetable load() throws IOException {
        Map<String, BusStop> stops = loadStops();
        Map<String, BusLine> lines = loadLines();
        Map<String, Service> services = loadServices();
        Map<String, List<Geolocation>> shapes = loadShapes();
        Map<String, TreeMap<Integer, String[]>> stopTimes = loadStopTimes();
        Map<List<Object>, RoutePattern> patterns = new HashMap<>();

        read("trips.txt", true, row -> {
            BusLine line = lines.get(row.get("route_id"));
            Service service = services.get(row.get("service_id"));
            List<Geolocation> path = shapes.get(row.get("shape_id"));
            TreeMap<Integer, String[]> times = stopTimes.get(row.get("trip_id"));
            if (line == null || service == null || path == null || path.size() < 2
                || times == null || times.size() < 2) return;

            List<BusStop> tripStops = new ArrayList<>();
            int[] arrivals = new int[times.size()];
            int[] departures = new int[times.size()];
            int i = 0;
            for (String[] time : times.values()) {
                BusStop stop = stops.get(time[0]);
                if (stop == null) return;
                tripStops.add(stop);
                arrivals[i] = minutes(time[1]);
                departures[i] = minutes(time[2]);
                i++;
            }

            // The feed has a copy of every shape per service; the same road is the same pattern.
            List<Object> key = new ArrayList<>();
            key.add(line.id);
            key.add(tripStops);
            for (Geolocation point : path) {
                key.add(point.lat);
                key.add(point.lng);
            }
            RoutePattern pattern = patterns.get(key);
            if (pattern == null) {
                pattern = new RoutePattern(line, tripStops, path);
                patterns.put(key, pattern);
            }
            pattern.addTrip(service, arrivals, departures);
        });

        LocalDate validUntil = LocalDate.MIN;
        for (Service service : services.values()) {
            if (service.getEnd().isAfter(validUntil)) validUntil = service.getEnd();
        }

        List<BusStop> usedStops = new ArrayList<>();
        for (BusStop stop : stops.values()) usedStops.add(stop);
        Collator collator = Collator.getInstance(new Locale("sl", "SI"));
        Collections.sort(usedStops, (a, b) -> {
            int byName = collator.compare(a.name, b.name);
            return byName != 0 ? byName : a.id.compareTo(b.id);
        });

        List<BusLine> usedLines = new ArrayList<>();
        for (BusLine line : lines.values()) {
            if (!line.getPatterns().isEmpty()) usedLines.add(line);
        }
        return new Timetable(usedStops, usedLines, validUntil);
    }

    private Map<String, BusStop> loadStops() throws IOException {
        Map<String, BusStop> stops = new LinkedHashMap<>();
        read("stops.txt", true, row -> {
            // Anything but 0 or empty is a station, an entrance or the like.
            String type = row.get("location_type");
            if (!type.isEmpty() && !type.equals("0")) return;
            stops.put(row.get("stop_id"), new BusStop(row.get("stop_id"), row.get("stop_name").trim(),
                new Geolocation(Double.parseDouble(row.get("stop_lat")), Double.parseDouble(row.get("stop_lon")))));
        });
        return stops;
    }

    private Map<String, BusLine> loadLines() throws IOException {
        Map<String, BusLine> lines = new LinkedHashMap<>();
        read("routes.txt", true, row -> {
            if (!row.get("route_type").equals(BUS)) return;
            String color = row.get("route_color");
            lines.put(row.get("route_id"), new BusLine(row.get("route_id"), row.get("route_short_name").trim(),
                row.get("route_long_name").trim(),
                color.matches("[0-9A-Fa-f]{6}") ? Color.valueOf(color) : DEFAULT_COLOR));
        });
        return lines;
    }

    private Map<String, Service> loadServices() throws IOException {
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        Map<String, Service> services = new HashMap<>();
        read("calendar.txt", false, row -> {
            boolean[] weekdays = new boolean[days.length];
            for (int i = 0; i < days.length; i++) weekdays[i] = row.get(days[i]).equals("1");
            services.put(row.get("service_id"),
                new Service(weekdays, date(row.get("start_date")), date(row.get("end_date"))));
        });
        read("calendar_dates.txt", false, row -> {
            Service service = services.get(row.get("service_id"));
            if (service == null) {
                service = new Service();
                services.put(row.get("service_id"), service);
            }
            service.addException(date(row.get("date")), row.get("exception_type").equals("1"));
        });
        return services;
    }

    private Map<String, List<Geolocation>> loadShapes() throws IOException {
        Map<String, TreeMap<Integer, Geolocation>> points = new HashMap<>();
        read("shapes.txt", true, row -> points
            .computeIfAbsent(row.get("shape_id"), id -> new TreeMap<>())
            .put(Integer.parseInt(row.get("shape_pt_sequence")), new Geolocation(
                Double.parseDouble(row.get("shape_pt_lat")), Double.parseDouble(row.get("shape_pt_lon")))));

        Map<String, List<Geolocation>> shapes = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, Geolocation>> entry : points.entrySet()) {
            shapes.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return shapes;
    }

    /** Trip id to stop id, arrival and departure by sequence. */
    private Map<String, TreeMap<Integer, String[]>> loadStopTimes() throws IOException {
        Map<String, TreeMap<Integer, String[]>> stopTimes = new HashMap<>();
        read("stop_times.txt", true, row -> {
            String arrival = row.get("arrival_time");
            String departure = row.get("departure_time");
            if (arrival.isEmpty()) arrival = departure;
            if (departure.isEmpty()) departure = arrival;
            if (arrival.isEmpty()) return;
            stopTimes.computeIfAbsent(row.get("trip_id"), id -> new TreeMap<>())
                .put(Integer.parseInt(row.get("stop_sequence")),
                    new String[]{row.get("stop_id"), arrival, departure});
        });
        return stopTimes;
    }

    private static int minutes(String time) {
        String[] parts = time.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static LocalDate date(String date) {
        return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
    }

    // CSV

    private interface RowHandler {
        void handle(Row row);
    }

    private static class Row {
        private final Map<String, Integer> columns;
        private List<String> values;

        Row(List<String> header) {
            columns = new HashMap<>();
            for (int i = 0; i < header.size(); i++) columns.put(header.get(i).trim(), i);
        }

        /** The value of a column, empty if there is none. */
        String get(String column) {
            Integer index = columns.get(column);
            return index == null || index >= values.size() ? "" : values.get(index);
        }
    }

    private void read(String file, boolean required, RowHandler handler) throws IOException {
        byte[] data = files.get(file);
        if (data == null) {
            if (required) throw new IOException("The timetable has no " + file);
            return;
        }
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));
        String first = reader.readLine();
        if (first == null) return;
        if (first.startsWith("﻿")) first = first.substring(1);

        Row row = new Row(split(first));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.trim().isEmpty()) continue;
            row.values = split(line);
            handler.handle(row);
        }
    }

    static List<String> split(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    value.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        values.add(value.toString());
        return values;
    }
}
