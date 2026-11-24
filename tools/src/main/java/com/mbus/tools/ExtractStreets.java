package com.mbus.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * Takes the streets one can walk on and the addresses of Maribor out of an OpenStreetMap file, such as
 * https://download.geofabrik.de/europe/slovenia-latest.osm.pbf
 */
public final class ExtractStreets {

    // Around all bus stops, with some space
    private static final double SOUTH = 46.49, WEST = 15.55, NORTH = 46.625, EAST = 15.77;

    private static final Set<String> WALKABLE = Set.of(
        "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
        "residential", "living_street", "service", "road", "track", "path", "footway", "cycleway", "pedestrian",
        "steps", "platform");
    private static final double TOLERANCE_METERS = 1.5;
    private static final double SAME_STREET_METERS = 500;
    private static final double METERS_PER_DEGREE = 111_320;
    private static final double LNG_SCALE = Math.cos(Math.toRadians(46.55));

    private final Map<Long, double[]> positions = new HashMap<>();
    private final List<long[]> ways = new ArrayList<>();
    /** Name to positions of addresses, and of streets. */
    private final Map<String, List<double[]>> addressesOf = new HashMap<>();
    private final Map<String, List<double[]>> streetsOf = new TreeMap<>();
    /** Text of an address to its line in the file; the first one wins. */
    private final Map<String, String> addresses = new TreeMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || args[0].isEmpty()) {
            System.err.println("Usage: ./gradlew tools:run -Posm=slovenia-latest.osm.pbf");
            System.exit(1);
        }
        ExtractStreets extract = new ExtractStreets();
        extract.read(Paths.get(args[0]));
        extract.write(Paths.get(args[1]));
    }

    private void read(Path file) throws IOException {
        PbfReader.read(file, new PbfReader.Handler() {
            @Override
            public void node(long id, double lat, double lng, Map<String, String> tags) {
                if (lat < SOUTH || lat > NORTH || lng < WEST || lng > EAST) return;
                double[] position = {lat, lng};
                positions.put(id, position);
                addAddress(tags, position);
            }

            @Override
            public void way(long id, long[] nodes, Map<String, String> tags) {
                double[] center = center(nodes);
                if (center == null) return;
                addAddress(tags, center);
                addStreet(nodes, tags);
                if (isWalkable(tags)) ways.add(nodes);
            }
        });
    }

    private double[] center(long[] nodes) {
        double lat = 0, lng = 0;
        int count = 0;
        for (long node : nodes) {
            double[] position = positions.get(node);
            if (position == null) continue;
            lat += position[0];
            lng += position[1];
            count++;
        }
        return count == 0 ? null : new double[]{lat / count, lng / count};
    }

    private static boolean isWalkable(Map<String, String> tags) {
        String highway = tags.get("highway");
        if (highway == null || !WALKABLE.contains(highway)) return false;
        String foot = tags.getOrDefault("foot", "");
        if (foot.equals("no") || foot.equals("private")) return false;
        boolean allowed = foot.equals("yes") || foot.equals("designated") || foot.equals("permissive");
        String access = tags.getOrDefault("access", "");
        if (!allowed && (access.equals("no") || access.equals("private"))) return false;
        return !"driveway".equals(tags.get("service")) && !"yes".equals(tags.get("indoor"));
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\s|]+", " ").trim();
    }

    private void addAddress(Map<String, String> tags, double[] position) {
        String number = clean(tags.get("addr:housenumber"));
        if (number.isEmpty() || number.length() > 12) return;
        String city = clean(tags.get("addr:city"));
        String street = clean(tags.get("addr:street"));
        if (street.isEmpty()) street = clean(tags.get("addr:place"));
        if (street.isEmpty()) street = city;
        if (street.isEmpty()) return;

        String key = street + "|" + number + "|" + Math.round(position[0] * 500) + "|" + Math.round(position[1] * 500);
        addresses.putIfAbsent(key, "A|" + street + "|" + number + "|" + city + "|" + coordinates(position));
        addressesOf.computeIfAbsent(street, s -> new ArrayList<>()).add(position);
    }

    private void addStreet(long[] nodes, Map<String, String> tags) {
        String name = clean(tags.get("name"));
        if (name.isEmpty() || !tags.containsKey("highway") || name.contains("slepi del")) return;
        // A point of the street rather than its center, which may be somewhere else.
        double[] middle = positions.get(nodes[nodes.length / 2]);
        if (middle != null) streetsOf.computeIfAbsent(name, n -> new ArrayList<>()).add(middle);
    }

    private static double meters(double[] a, double[] b) {
        return METERS_PER_DEGREE * Math.hypot(a[0] - b[0], (a[1] - b[1]) * LNG_SCALE);
    }

    private static String coordinates(double[] position) {
        return micro(position[0] - SOUTH) + " " + micro(position[1] - WEST);
    }

    private static long micro(double degrees) {
        return Math.round(degrees * 1e6);
    }

    // Network

    private static final class Edge {
        int a, b;
        List<double[]> points; // from a to b, with both

        Edge(int a, int b, List<double[]> points) {
            this.a = a;
            this.b = b;
            this.points = points;
        }

        List<double[]> pointsFrom(int node) {
            if (node == a) return points;
            List<double[]> reversed = new ArrayList<>(points);
            Collections.reverse(reversed);
            return reversed;
        }

        int other(int node) {
            return node == a ? b : a;
        }
    }

    private List<Edge> buildNetwork() {
        Map<Long, Integer> uses = new HashMap<>();
        for (long[] way : ways) {
            for (int i = 0; i < way.length; i++) {
                uses.merge(way[i], i == 0 || i == way.length - 1 ? 2 : 1, Integer::sum);
            }
        }
        Map<Long, Integer> numbers = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (long[] way : ways) {
            long start = -1;
            List<double[]> points = new ArrayList<>();
            for (long node : way) {
                double[] position = positions.get(node);
                if (position == null) { // outside
                    points = new ArrayList<>();
                    start = -1;
                    continue;
                }
                points.add(position);
                if (start < 0) {
                    start = node;
                } else if (uses.get(node) > 1) {
                    edges.add(new Edge(number(numbers, start), number(numbers, node), points));
                    points = new ArrayList<>(List.of(position));
                    start = node;
                }
            }
        }
        return joined(largestComponent(edges));
    }

    private static int number(Map<Long, Integer> numbers, long node) {
        return numbers.computeIfAbsent(node, n -> numbers.size());
    }

    private static Map<Integer, List<Edge>> byNode(List<Edge> edges) {
        Map<Integer, List<Edge>> result = new HashMap<>();
        for (Edge edge : edges) {
            result.computeIfAbsent(edge.a, n -> new ArrayList<>()).add(edge);
            if (edge.b != edge.a) result.computeIfAbsent(edge.b, n -> new ArrayList<>()).add(edge);
        }
        return result;
    }

    /** Leaves out parts one cannot get to. */
    private static List<Edge> largestComponent(List<Edge> edges) {
        Map<Integer, List<Edge>> byNode = byNode(edges);
        Set<Integer> seen = new HashSet<>();
        Set<Integer> largest = Set.of();
        for (Integer start : byNode.keySet()) {
            if (!seen.add(start)) continue;
            Set<Integer> component = new HashSet<>(List.of(start));
            ArrayDeque<Integer> open = new ArrayDeque<>(component);
            while (!open.isEmpty()) {
                int node = open.poll();
                for (Edge edge : byNode.get(node)) {
                    int other = edge.other(node);
                    if (component.add(other)) open.add(other);
                }
            }
            seen.addAll(component);
            if (component.size() > largest.size()) largest = component;
        }
        List<Edge> kept = new ArrayList<>();
        for (Edge edge : edges) if (largest.contains(edge.a)) kept.add(edge);
        return kept;
    }

    /** Joins edges where there is no choice of way. */
    private static List<Edge> joined(List<Edge> edges) {
        Map<Integer, List<Edge>> byNode = byNode(edges);
        Set<Edge> kept = new LinkedHashSet<>(edges);
        for (Map.Entry<Integer, List<Edge>> entry : byNode.entrySet()) {
            int node = entry.getKey();
            List<Edge> here = entry.getValue();
            if (here.size() != 2) continue;
            Edge first = here.get(0), second = here.get(1);
            int from = first.other(node), to = second.other(node);
            if (from == node || to == node || from == to) continue;

            List<double[]> points = new ArrayList<>(first.pointsFrom(from));
            List<double[]> rest = second.pointsFrom(node);
            points.addAll(rest.subList(1, rest.size()));
            Edge edge = new Edge(from, to, points);
            kept.remove(first);
            kept.remove(second);
            kept.add(edge);
            here.clear();
            byNode.get(from).remove(first);
            byNode.get(from).add(edge);
            byNode.get(to).remove(second);
            byNode.get(to).add(edge);
        }
        return new ArrayList<>(kept);
    }

    /** Leaves out points that make no visible difference (Douglas-Peucker). */
    static List<double[]> simplify(List<double[]> points, double tolerance) {
        if (points.size() < 3) return points;
        double[] first = points.get(0), last = points.get(points.size() - 1);
        int farthest = -1;
        double distance = tolerance;
        for (int i = 1; i < points.size() - 1; i++) {
            double d = segmentMeters(points.get(i), first, last);
            if (d > distance) {
                distance = d;
                farthest = i;
            }
        }
        if (farthest < 0) return new ArrayList<>(List.of(first, last));
        List<double[]> result = new ArrayList<>(simplify(points.subList(0, farthest + 1), tolerance));
        List<double[]> rest = simplify(points.subList(farthest, points.size()), tolerance);
        result.addAll(rest.subList(1, rest.size()));
        return result;
    }

    private static double segmentMeters(double[] p, double[] a, double[] b) {
        double dx = (b[1] - a[1]) * LNG_SCALE, dy = b[0] - a[0];
        double px = (p[1] - a[1]) * LNG_SCALE, py = p[0] - a[0];
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / length));
        return METERS_PER_DEGREE * Math.hypot(px - t * dx, py - t * dy);
    }

    // Output

    private void write(Path file) throws IOException {
        List<Edge> edges = buildNetwork();
        Map<Integer, Integer> numbers = new HashMap<>();
        int streets = 0, points = 0;
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
            new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8))) {
            out.write("# Streets and addresses of Maribor, (c) OpenStreetMap contributors.\n"
                + "# Made available under the Open Database License: https://opendatacommons.org/licenses/odbl/1-0/\n"
                + "# See https://www.openstreetmap.org/copyright\n"
                + "# O: origin. N: node. E: edge between two nodes, with the points between them.\n"
                + "# A: address (street, number, city). S: street without addresses. In millionths of a degree.\n");
            out.write("O|" + Math.round(SOUTH * 1e6) + " " + Math.round(WEST * 1e6) + "\n");

            for (Edge edge : edges) {
                edge.points = simplify(edge.points, TOLERANCE_METERS);
                for (int node : new int[]{edge.a, edge.b}) {
                    if (numbers.containsKey(node)) continue;
                    numbers.put(node, numbers.size());
                    out.write("N|" + coordinates(edge.pointsFrom(node).get(0)) + "\n");
                }
                StringBuilder line = new StringBuilder("E|").append(numbers.get(edge.a)).append(' ').append(numbers.get(edge.b));
                for (int i = 1; i < edge.points.size() - 1; i++) {
                    line.append(' ').append(coordinates(edge.points.get(i)));
                    points++;
                }
                out.write(line.append('\n').toString());
            }
            for (String address : addresses.values()) out.write(address + "\n");

            for (Map.Entry<String, List<double[]>> entry : streetsOf.entrySet()) {
                List<double[]> written = new ArrayList<>();
                for (double[] position : entry.getValue()) {
                    boolean known = false;
                    for (double[] other : written) known |= meters(position, other) < SAME_STREET_METERS;
                    for (double[] other : addressesOf.getOrDefault(entry.getKey(), List.of())) {
                        known |= meters(position, other) < SAME_STREET_METERS;
                    }
                    if (known) continue;
                    written.add(position);
                    out.write("S|" + entry.getKey() + "|" + coordinates(position) + "\n");
                    streets++;
                }
            }
        }
        System.out.println(numbers.size() + " nodes, " + edges.size() + " edges, " + points + " points, "
            + addresses.size() + " addresses, " + streets + " streets without addresses, " + Files.size(file) + " bytes");
    }
}
