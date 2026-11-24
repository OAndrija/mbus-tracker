package com.mbus.app.systems.streets;

import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Finds the shortest ways on foot. Distances are in meters. May be used from any thread. */
public final class WalkNetwork {

    private static final double METERS_PER_DEGREE = 111_320;
    private static final double CELL_METERS = 120;

    /** A point of the network close to another point. */
    public static final class Access {
        final int edge;
        /** From the start of the edge. */
        final double along;
        /** From the point to the network, as the crow flies. */
        public final double distance;

        Access(int edge, double along, double distance) {
            this.edge = edge;
            this.along = along;
            this.distance = distance;
        }
    }

    /** Shortest ways from one point. */
    public final class Ways {
        private final Access from;
        private final double limit;
        private final double[] distance;
        private final int[] cameBy; // edge, or -1 at the start

        private Ways(Access from, double limit, Access target) {
            this.from = from;
            this.limit = limit;
            distance = new double[nodeX.length];
            cameBy = new int[nodeX.length];
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            Arrays.fill(cameBy, -1);

            PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
            reach(open, edgeFrom[from.edge], from.along, -1);
            reach(open, edgeTo[from.edge], edgeLength[from.edge] - from.along, -1);
            while (!open.isEmpty()) {
                double[] next = open.poll();
                int node = (int) next[1];
                if (next[0] > distance[node]) continue;
                // No way to the target can be shorter than what is left to look at.
                if (target != null && next[0] >= to(target)) break;
                for (int i = firstEdge[node]; i < firstEdge[node + 1]; i++) {
                    int edge = edgesOfNodes[i];
                    int other = edgeFrom[edge] == node ? edgeTo[edge] : edgeFrom[edge];
                    reach(open, other, next[0] + edgeLength[edge], edge);
                }
            }
        }

        private void reach(PriorityQueue<double[]> open, int node, double meters, int edge) {
            if (meters >= distance[node] || meters > limit) return;
            distance[node] = meters;
            cameBy[node] = edge;
            open.add(new double[]{meters, node});
        }

        /** Meters to another point of the network, infinite if it is beyond the limit. */
        public double to(Access target) {
            double best = Math.min(
                distance[edgeFrom[target.edge]] + target.along,
                distance[edgeTo[target.edge]] + edgeLength[target.edge] - target.along);
            if (target.edge == from.edge) best = Math.min(best, Math.abs(target.along - from.along));
            return best;
        }

        /** The points of the way to another point of the network, empty if there is none. */
        public List<Geolocation> pathTo(Access target) {
            double viaStart = distance[edgeFrom[target.edge]] + target.along;
            double viaEnd = distance[edgeTo[target.edge]] + edgeLength[target.edge] - target.along;
            double direct = target.edge == from.edge ? Math.abs(target.along - from.along) : Double.POSITIVE_INFINITY;
            if (Math.min(direct, Math.min(viaStart, viaEnd)) == Double.POSITIVE_INFINITY) return Collections.emptyList();

            List<double[]> backwards = new ArrayList<>();
            if (direct <= viaStart && direct <= viaEnd) {
                addPart(backwards, target.edge, target.along, from.along);
            } else {
                int node = viaStart <= viaEnd ? edgeFrom[target.edge] : edgeTo[target.edge];
                addPart(backwards, target.edge, target.along, viaStart <= viaEnd ? 0 : edgeLength[target.edge]);
                while (cameBy[node] >= 0) {
                    int edge = cameBy[node];
                    boolean atEnd = edgeTo[edge] == node && Math.abs(
                        distance[edgeFrom[edge]] + edgeLength[edge] - distance[node]) < 1e-6;
                    addPart(backwards, edge, atEnd ? edgeLength[edge] : 0, atEnd ? 0 : edgeLength[edge]);
                    node = atEnd ? edgeFrom[edge] : edgeTo[edge];
                }
                boolean leftAtStart = edgeFrom[from.edge] == node && Math.abs(distance[node] - from.along) < 1e-6;
                addPart(backwards, from.edge, leftAtStart ? 0 : edgeLength[from.edge], from.along);
            }

            List<Geolocation> path = new ArrayList<>();
            for (int i = backwards.size() - 1; i >= 0; i--) path.add(geolocation(backwards.get(i)));
            return path;
        }

        /** Adds the points of an edge between two distances along it, in that order. */
        private void addPart(List<double[]> points, int edge, double start, double end) {
            points.add(pointAt(edge, start));
            double[] px = edgeX[edge], py = edgeY[edge], lengths = edgeAlong[edge];
            if (start <= end) {
                for (int i = 0; i < px.length; i++) {
                    if (lengths[i] > start && lengths[i] < end) points.add(new double[]{px[i], py[i]});
                }
            } else {
                for (int i = px.length - 1; i >= 0; i--) {
                    if (lengths[i] < start && lengths[i] > end) points.add(new double[]{px[i], py[i]});
                }
            }
            points.add(pointAt(edge, end));
        }
    }

    // In meters around a point in the middle
    private final double originLat, originLng, lngScale;
    private final double[] nodeX, nodeY;
    private final int[] edgeFrom, edgeTo;
    private final double[] edgeLength;
    private final double[][] edgeX, edgeY, edgeAlong;
    private final int[] firstEdge, edgesOfNodes;
    private final Map<Long, List<Integer>> grid = new HashMap<>();

    static final class Builder {
        private final List<Geolocation> nodes = new ArrayList<>();
        private final List<int[]> ends = new ArrayList<>();
        private final List<List<Geolocation>> between = new ArrayList<>();

        void addNode(Geolocation position) {
            nodes.add(position);
        }

        void addEdge(int from, int to, List<Geolocation> points) {
            ends.add(new int[]{from, to});
            between.add(points);
        }

        WalkNetwork build() {
            return new WalkNetwork(this);
        }
    }

    private WalkNetwork(Builder builder) {
        int n = builder.nodes.size();
        int m = builder.ends.size();
        if (m == 0) throw new IllegalArgumentException("No streets");
        originLat = builder.nodes.get(0).lat;
        originLng = builder.nodes.get(0).lng;
        lngScale = Math.cos(Math.toRadians(originLat));
        nodeX = new double[n];
        nodeY = new double[n];
        for (int i = 0; i < n; i++) {
            nodeX[i] = x(builder.nodes.get(i));
            nodeY[i] = y(builder.nodes.get(i));
        }

        edgeFrom = new int[m];
        edgeTo = new int[m];
        edgeLength = new double[m];
        edgeX = new double[m][];
        edgeY = new double[m][];
        edgeAlong = new double[m][];
        firstEdge = new int[n + 1];
        for (int e = 0; e < m; e++) {
            edgeFrom[e] = builder.ends.get(e)[0];
            edgeTo[e] = builder.ends.get(e)[1];
            firstEdge[edgeFrom[e] + 1]++;
            if (edgeTo[e] != edgeFrom[e]) firstEdge[edgeTo[e] + 1]++;

            List<Geolocation> points = builder.between.get(e);
            int size = points.size() + 2;
            double[] px = new double[size], py = new double[size], along = new double[size];
            px[0] = nodeX[edgeFrom[e]];
            py[0] = nodeY[edgeFrom[e]];
            for (int i = 1; i < size; i++) {
                px[i] = i == size - 1 ? nodeX[edgeTo[e]] : x(points.get(i - 1));
                py[i] = i == size - 1 ? nodeY[edgeTo[e]] : y(points.get(i - 1));
                along[i] = along[i - 1] + Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
            }
            edgeX[e] = px;
            edgeY[e] = py;
            edgeAlong[e] = along;
            edgeLength[e] = along[size - 1];
            addToGrid(e);
        }
        for (int i = 0; i < n; i++) firstEdge[i + 1] += firstEdge[i];
        edgesOfNodes = new int[firstEdge[n]];
        int[] next = Arrays.copyOf(firstEdge, n);
        for (int e = 0; e < m; e++) {
            edgesOfNodes[next[edgeFrom[e]]++] = e;
            if (edgeTo[e] != edgeFrom[e]) edgesOfNodes[next[edgeTo[e]]++] = e;
        }
    }

    private double x(Geolocation geo) {
        return (geo.lng - originLng) * lngScale * METERS_PER_DEGREE;
    }

    private double y(Geolocation geo) {
        return (geo.lat - originLat) * METERS_PER_DEGREE;
    }

    private Geolocation geolocation(double[] point) {
        return new Geolocation(originLat + point[1] / METERS_PER_DEGREE,
            originLng + point[0] / METERS_PER_DEGREE / lngScale);
    }

    private static int cell(double meters) {
        return (int) Math.floor(meters / CELL_METERS);
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xffffffffL);
    }

    private void addToGrid(int edge) {
        double[] px = edgeX[edge], py = edgeY[edge];
        for (int i = 1; i < px.length; i++) {
            for (int cx = cell(Math.min(px[i - 1], px[i])); cx <= cell(Math.max(px[i - 1], px[i])); cx++) {
                for (int cy = cell(Math.min(py[i - 1], py[i])); cy <= cell(Math.max(py[i - 1], py[i])); cy++) {
                    List<Integer> edges = grid.computeIfAbsent(key(cx, cy), k -> new ArrayList<>());
                    if (edges.isEmpty() || edges.get(edges.size() - 1) != edge) edges.add(edge);
                }
            }
        }
    }

    /** The closest point of the network, or null if there is none within a distance. */
    public Access getAccess(Geolocation geo, double maxMeters) {
        double px = x(geo), py = y(geo);
        Access best = null;
        int rings = (int) Math.ceil(maxMeters / CELL_METERS) + 1;
        for (int ring = 0; ring <= rings; ring++) {
            // Nothing in this ring can be closer than its inner edge.
            if (best != null && best.distance < (ring - 1) * CELL_METERS) break;
            for (int cx = cell(px) - ring; cx <= cell(px) + ring; cx++) {
                for (int cy = cell(py) - ring; cy <= cell(py) + ring; cy++) {
                    if (Math.max(Math.abs(cx - cell(px)), Math.abs(cy - cell(py))) != ring) continue;
                    for (int edge : grid.getOrDefault(key(cx, cy), Collections.emptyList())) {
                        best = closer(best, edge, px, py, maxMeters);
                    }
                }
            }
        }
        return best;
    }

    private Access closer(Access best, int edge, double px, double py, double maxMeters) {
        double[] ex = edgeX[edge], ey = edgeY[edge];
        double limit = best == null ? maxMeters : best.distance;
        for (int i = 1; i < ex.length; i++) {
            double dx = ex[i] - ex[i - 1], dy = ey[i] - ey[i - 1];
            double length = dx * dx + dy * dy;
            double t = length == 0 ? 0 : ((px - ex[i - 1]) * dx + (py - ey[i - 1]) * dy) / length;
            t = Math.max(0, Math.min(1, t));
            double distance = Math.hypot(px - ex[i - 1] - t * dx, py - ey[i - 1] - t * dy);
            if (distance < limit) {
                limit = distance;
                best = new Access(edge, edgeAlong[edge][i - 1] + t * Math.sqrt(length), distance);
            }
        }
        return best;
    }

    private double[] pointAt(int edge, double along) {
        double[] lengths = edgeAlong[edge];
        int i = 1;
        while (i < lengths.length - 1 && lengths[i] < along) i++;
        double part = lengths[i] - lengths[i - 1];
        double t = part == 0 ? 0 : Math.max(0, Math.min(1, (along - lengths[i - 1]) / part));
        return new double[]{
            edgeX[edge][i - 1] + (edgeX[edge][i] - edgeX[edge][i - 1]) * t,
            edgeY[edge][i - 1] + (edgeY[edge][i] - edgeY[edge][i - 1]) * t};
    }

    /** The shortest ways to everywhere within a distance. */
    public Ways waysFrom(Access from, double maxMeters) {
        return new Ways(from, maxMeters, null);
    }

    /** Only certain to have the shortest way to the target, but faster. */
    public Ways wayBetween(Access from, Access target, double maxMeters) {
        return new Ways(from, maxMeters, target);
    }
}
