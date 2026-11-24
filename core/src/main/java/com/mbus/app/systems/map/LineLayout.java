package com.mbus.app.systems.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Finds out which lines share a street and gives each of them its own lane there. Routes on the same
 * street do not have the same points, so they are matched by distance and direction, and moved to the
 * center of the street, which is the route of its first line.
 */
final class LineLayout {

    /** A route of a line. Lines of a lower rank are further to the left. */
    static final class Road {
        final int rank;
        final float[] x, y;

        Road(int rank, float[] x, float[] y) {
            this.rank = rank;
            this.x = x;
            this.y = y;
        }
    }

    // World pixels; one is about 1.6 meters.
    private static final float STEP = 25f;
    private static final float STREET_WIDTH = 9f;
    private static final float CELL = 32f;
    /** Sine of the angle up to which two routes count as parallel. */
    private static final float PARALLEL = 0.35f;
    private static final int SMOOTHING_PASSES = 4;
    private static final float CORNER_RADIUS = 11f;
    private static final int CORNER_POINTS = 5;
    /** Cosine of the angle from which a bend counts as a corner. */
    private static final float CORNER_COSINE = 0.92f;

    private final List<Road> roads = new ArrayList<>();
    private final List<double[]> distances = new ArrayList<>();
    private final Map<Long, List<int[]>> grid = new HashMap<>();
    private final List<Places> places = new ArrayList<>();
    /** Whether lanes are counted from the left of a center road, or from its right. */
    private boolean[] fromRight;

    /** Where the points of a road are on their streets. */
    private static final class Places {
        final int[] center;
        final boolean[] sameDirection;
        final float[] fromLeft, laneCount, shiftX, shiftY;

        Places(int size) {
            center = new int[size];
            sameDirection = new boolean[size];
            fromLeft = new float[size];
            laneCount = new float[size];
            shiftX = new float[size];
            shiftY = new float[size];
        }
    }

    private LineLayout() {
    }

    /** One strand for every road. */
    static List<Strand> build(List<Road> input) {
        LineLayout layout = new LineLayout();
        for (Road road : input) layout.add(road);
        for (int i = 0; i < layout.roads.size(); i++) layout.places.add(layout.findPlaces(i));
        layout.orientStreets();
        List<Strand> strands = new ArrayList<>();
        for (int i = 0; i < layout.roads.size(); i++) strands.add(layout.createStrand(i));
        return strands;
    }

    /** Adds points to long segments, so that lanes can change close to where a line joins or leaves. */
    private void add(Road road) {
        int capacity = 1;
        for (int i = 1; i < road.x.length; i++) {
            capacity += pieces(road, i);
        }
        float[] x = new float[capacity];
        float[] y = new float[capacity];
        double[] along = new double[capacity];
        x[0] = road.x[0];
        y[0] = road.y[0];
        int n = 1;
        for (int i = 1; i < road.x.length; i++) {
            int pieces = pieces(road, i);
            double length = Math.hypot(road.x[i] - road.x[i - 1], road.y[i] - road.y[i - 1]);
            double start = along[n - 1];
            for (int p = 1; p <= pieces; p++) {
                float t = p / (float) pieces;
                x[n] = road.x[i - 1] + (road.x[i] - road.x[i - 1]) * t;
                y[n] = road.y[i - 1] + (road.y[i] - road.y[i - 1]) * t;
                along[n] = start + length * t;
                n++;
            }
        }

        int index = roads.size();
        roads.add(new Road(road.rank, x, y));
        distances.add(along);
        for (int i = 0; i + 1 < n; i++) {
            int[] edge = {index, i};
            int left = cell(Math.min(x[i], x[i + 1])), right = cell(Math.max(x[i], x[i + 1]));
            int bottom = cell(Math.min(y[i], y[i + 1])), top = cell(Math.max(y[i], y[i + 1]));
            for (int cx = left; cx <= right; cx++) {
                for (int cy = bottom; cy <= top; cy++) {
                    grid.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(edge);
                }
            }
        }
    }

    private static int pieces(Road road, int i) {
        double length = Math.hypot(road.x[i] - road.x[i - 1], road.y[i] - road.y[i - 1]);
        return Math.max(1, (int) Math.ceil(length / STEP));
    }

    private static int cell(float value) {
        return (int) Math.floor(value / CELL);
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xffffffffL);
    }

    private interface Match {
        void accept(int road, float pointX, float pointY, float directionX, float directionY, float distance);
    }

    /** Reports the closest point of every segment that runs along a point in the same or opposite direction. */
    private void findParallel(float px, float py, float dirX, float dirY, Match match) {
        int left = cell(px - STREET_WIDTH), right = cell(px + STREET_WIDTH);
        int bottom = cell(py - STREET_WIDTH), top = cell(py + STREET_WIDTH);
        for (int cx = left; cx <= right; cx++) {
            for (int cy = bottom; cy <= top; cy++) {
                List<int[]> edges = grid.get(key(cx, cy));
                if (edges == null) continue;
                for (int[] edge : edges) {
                    Road road = roads.get(edge[0]);
                    int i = edge[1];
                    float ex = road.x[i + 1] - road.x[i];
                    float ey = road.y[i + 1] - road.y[i];
                    float length = (float) Math.sqrt(ex * ex + ey * ey);
                    if (length < 1e-3f) continue;
                    if (Math.abs(dirX * ey - dirY * ex) / length > PARALLEL) continue;

                    float t = ((px - road.x[i]) * ex + (py - road.y[i]) * ey) / (length * length);
                    t = Math.max(0f, Math.min(1f, t));
                    float qx = road.x[i] + ex * t;
                    float qy = road.y[i] + ey * t;
                    float distance = (float) Math.hypot(px - qx, py - qy);
                    if (distance <= STREET_WIDTH) match.accept(edge[0], qx, qy, ex / length, ey / length, distance);
                }
            }
        }
    }

    private Places findPlaces(int index) {
        Road road = roads.get(index);
        int n = road.x.length;
        Places found = new Places(n);
        for (int i = 0; i < n; i++) {
            int before = Math.max(0, i - 1);
            int after = Math.min(n - 1, i + 1);
            float dirX = road.x[after] - road.x[before];
            float dirY = road.y[after] - road.y[before];
            float length = (float) Math.hypot(dirX, dirY);
            found.center[i] = -1;
            found.laneCount[i] = 1;
            if (length >= 1e-3f) {
                place(index, i, dirX / length, dirY / length, found);
            }
        }
        return found;
    }

    /**
     * Where a line goes on from one street to the next, its lane should stay on the same side. Counts
     * how often that asks for two streets to be counted from the same side or not, and follows the
     * strongest wishes first.
     */
    private void orientStreets() {
        int n = roads.size();
        fromRight = new boolean[n];
        Map<Long, Integer> wishes = new HashMap<>();
        for (Places place : places) {
            for (int i = 1; i < place.center.length; i++) {
                int a = place.center[i - 1];
                int b = place.center[i];
                if (a < 0 || b < 0 || a == b) continue;
                int wish = place.sameDirection[i - 1] == place.sameDirection[i] ? 1 : -1;
                wishes.merge(key(Math.min(a, b), Math.max(a, b)), wish, Integer::sum);
            }
        }

        List<Map.Entry<Long, Integer>> strongest = new ArrayList<>(wishes.entrySet());
        strongest.sort((a, b) -> {
            int byStrength = Integer.compare(Math.abs(b.getValue()), Math.abs(a.getValue()));
            return byStrength != 0 ? byStrength : Long.compare(a.getKey(), b.getKey());
        });
        int[] group = new int[n];
        for (int i = 0; i < n; i++) group[i] = i;
        for (Map.Entry<Long, Integer> wish : strongest) {
            int a = (int) (wish.getKey() >> 32);
            int b = (int) (long) wish.getKey();
            if (group[a] == group[b] || wish.getValue() == 0) continue;
            boolean flip = (fromRight[a] == fromRight[b]) != (wish.getValue() > 0);
            int from = group[b];
            int to = group[a];
            for (int i = 0; i < n; i++) {
                if (group[i] != from) continue;
                group[i] = to;
                if (flip) fromRight[i] = !fromRight[i];
            }
        }
    }

    private Strand createStrand(int index) {
        Road road = roads.get(index);
        Places place = places.get(index);
        int n = road.x.length;
        float[] lane = new float[n];
        float[] laneCount = place.laneCount.clone();
        float[] shiftX = place.shiftX.clone();
        float[] shiftY = place.shiftY.clone();
        for (int i = 0; i < n; i++) {
            if (place.center[i] < 0) continue;
            boolean mirrored = fromRight[place.center[i]] == place.sameDirection[i];
            lane[i] = mirrored ? place.fromLeft[i] : -place.fromLeft[i];
        }

        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            smooth(lane);
            smooth(laneCount);
            smooth(shiftX);
            smooth(shiftY);
        }
        return roundCorners(road, distances.get(index), lane, laneCount, shiftX, shiftY);
    }

    /** Replaces sharp corners by small arcs, as parallel lines would have spikes there. */
    private static Strand roundCorners(Road road, double[] along, float[] lane, float[] laneCount,
                                       float[] shiftX, float[] shiftY) {
        int n = road.x.length;
        int capacity = n * (CORNER_POINTS + 1);
        float[][] out = new float[6][capacity]; // x, y, lane, count, shift x, shift y
        double[] outAlong = new double[capacity];
        int size = 0;

        for (int i = 0; i < n; i++) {
            float x = road.x[i] + shiftX[i];
            float y = road.y[i] + shiftY[i];
            float inX = 0, inY = 0, outX = 0, outY = 0, inLength = 0, outLength = 0;
            if (i > 0 && i + 1 < n) {
                inX = x - (road.x[i - 1] + shiftX[i - 1]);
                inY = y - (road.y[i - 1] + shiftY[i - 1]);
                outX = road.x[i + 1] + shiftX[i + 1] - x;
                outY = road.y[i + 1] + shiftY[i + 1] - y;
                inLength = (float) Math.hypot(inX, inY);
                outLength = (float) Math.hypot(outX, outY);
            }
            boolean corner = inLength > 1f && outLength > 1f
                && (inX * outX + inY * outY) / (inLength * outLength) < CORNER_COSINE;
            if (!corner) {
                out[0][size] = x;
                out[1][size] = y;
                out[2][size] = lane[i];
                out[3][size] = laneCount[i];
                out[4][size] = shiftX[i];
                out[5][size] = shiftY[i];
                outAlong[size++] = along[i];
                continue;
            }

            float before = Math.min(CORNER_RADIUS, inLength * 0.45f) / inLength;
            float after = Math.min(CORNER_RADIUS, outLength * 0.45f) / outLength;
            for (int c = 0; c <= CORNER_POINTS; c++) {
                float t = c / (float) CORNER_POINTS;
                float a = (1 - t) * (1 - t);
                float b = t * t;
                float dx = -inX * before * a + outX * after * b;
                float dy = -inY * before * a + outY * after * b;
                // Position on the original route, relative to the corner
                int other = t < 0.5f ? i - 1 : i + 1;
                float share = t < 0.5f ? before * (1 - 2 * t) : after * (2 * t - 1);
                float rawX = road.x[i] + (road.x[other] - road.x[i]) * share;
                float rawY = road.y[i] + (road.y[other] - road.y[i]) * share;
                out[0][size] = x + dx;
                out[1][size] = y + dy;
                out[2][size] = lane[i] + (lane[other] - lane[i]) * share;
                out[3][size] = laneCount[i] + (laneCount[other] - laneCount[i]) * share;
                out[4][size] = x + dx - rawX;
                out[5][size] = y + dy - rawY;
                outAlong[size++] = along[i] + (along[other] - along[i]) * share;
            }
        }

        for (int k = 0; k < out.length; k++) out[k] = Arrays.copyOf(out[k], size);
        float[] normalX = new float[size];
        float[] normalY = new float[size];
        computeNormals(out[0], out[1], normalX, normalY);
        return new Strand(out[0], out[1], normalX, normalY, out[2], out[3],
            Arrays.copyOf(outAlong, size), out[4], out[5]);
    }

    private void place(int index, int i, float dirX, float dirY, Places found) {
        Road road = roads.get(index);

        // The center of the street: the first route of the first line that runs here.
        final int[] centerRoad = {-1};
        final float[] center = new float[5]; // point, direction, distance
        findParallel(road.x[i], road.y[i], dirX, dirY, (other, qx, qy, ex, ey, distance) -> {
            int current = centerRoad[0];
            boolean better = current < 0
                || roads.get(other).rank < roads.get(current).rank
                || (roads.get(other).rank == roads.get(current).rank && other < current)
                || (other == current && distance < center[4]);
            if (better) {
                centerRoad[0] = other;
                center[0] = qx;
                center[1] = qy;
                center[2] = ex;
                center[3] = ey;
                center[4] = distance;
            }
        });
        if (centerRoad[0] < 0) return;

        // Everyone on the street looks from its center, so all agree on the lanes.
        TreeSet<Integer> ranks = new TreeSet<>();
        ranks.add(road.rank);
        findParallel(center[0], center[1], center[2], center[3],
            (other, qx, qy, ex, ey, distance) -> ranks.add(roads.get(other).rank));

        found.center[i] = centerRoad[0];
        found.sameDirection[i] = dirX * center[2] + dirY * center[3] >= 0;
        found.fromLeft[i] = ranks.headSet(road.rank).size() - (ranks.size() - 1) / 2f;
        found.laneCount[i] = ranks.size();
        found.shiftX[i] = center[0] - road.x[i];
        found.shiftY[i] = center[1] - road.y[i];
    }

    private static void smooth(float[] values) {
        float[] copy = Arrays.copyOf(values, values.length);
        int last = values.length - 1;
        for (int i = 0; i <= last; i++) {
            values[i] = (copy[Math.max(0, i - 1)] + 2 * copy[i] + copy[Math.min(last, i + 1)]) / 4f;
        }
    }

    private static void computeNormals(float[] x, float[] y, float[] normalX, float[] normalY) {
        int n = x.length;
        float lastX = 0, lastY = 1;
        float[] segmentX = new float[n];
        float[] segmentY = new float[n];
        for (int i = 0; i + 1 < n; i++) {
            float dx = x[i + 1] - x[i];
            float dy = y[i + 1] - y[i];
            float length = (float) Math.hypot(dx, dy);
            if (length > 1e-3f) {
                lastX = -dy / length;
                lastY = dx / length;
            }
            segmentX[i] = lastX;
            segmentY[i] = lastY;
        }
        for (int i = 0; i < n; i++) {
            int before = Math.max(0, i - 1);
            int after = Math.min(n - 2, i);
            if (after < 0) after = 0;
            float nx = segmentX[before] + segmentX[after];
            float ny = segmentY[before] + segmentY[after];
            float length = (float) Math.hypot(nx, ny);
            if (length < 0.3f) { // turns around
                normalX[i] = segmentX[before];
                normalY[i] = segmentY[before];
                continue;
            }
            nx /= length;
            ny /= length;
            float miter = 1f / Math.max(0.6f, nx * segmentX[after] + ny * segmentY[after]);
            normalX[i] = nx * miter;
            normalY[i] = ny * miter;
        }
    }
}
