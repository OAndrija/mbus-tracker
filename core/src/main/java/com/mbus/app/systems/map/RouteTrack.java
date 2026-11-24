package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;

/** A route as a track: converts between distance along the route and position. */
public class RouteTrack {

    private static final double IMPOSSIBLE = 1e30;

    private final float[] x;
    private final float[] y;
    private final double[] distance;

    public RouteTrack(float[] x, float[] y) {
        this.x = x;
        this.y = y;
        distance = new double[x.length];
        for (int i = 1; i < x.length; i++) {
            distance[i] = distance[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
    }

    public double length() {
        return distance.length == 0 ? 0 : distance[distance.length - 1];
    }

    /** The position at a distance from the start, limited to the ends of the route. */
    public Vector2 pointAt(double along, Vector2 out) {
        if (along <= 0) return out.set(x[0], y[0]);
        int last = x.length - 1;
        if (along >= distance[last]) return out.set(x[last], y[last]);

        int low = 0;
        int high = last;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (distance[middle] <= along) low = middle;
            else high = middle;
        }
        double t = (along - distance[low]) / (distance[high] - distance[low]);
        return out.set(
            (float) (x[low] + (x[high] - x[low]) * t),
            (float) (y[low] + (y[high] - y[low]) * t));
    }

    /**
     * Places points that are visited in order, such as the stops of a trip, on the track. A route may
     * use a road twice or visit a stop twice, so all of them are placed together: the distances never
     * decrease, and the points are as close to their places as possible.
     */
    public double[] placeInOrder(float[] px, float[] py) {
        int points = px.length;
        int segments = x.length - 1;
        double[][] cost = new double[points][segments];
        double[][] along = new double[points][segments];
        int[][] from = new int[points][segments];

        for (int p = 0; p < points; p++) {
            int bestBefore = -1;
            for (int s = 0; s < segments; s++) {
                double dx = x[s + 1] - x[s];
                double dy = y[s + 1] - y[s];
                double lengthSquared = dx * dx + dy * dy;
                double t = lengthSquared == 0 ? 0 : ((px[p] - x[s]) * dx + (py[p] - y[s]) * dy) / lengthSquared;
                t = Math.max(0, Math.min(1, t));
                double ex = px[p] - x[s] - t * dx;
                double ey = py[p] - y[s] - t * dy;
                along[p][s] = distance[s] + t * (distance[s + 1] - distance[s]);
                cost[p][s] = ex * ex + ey * ey;

                if (p > 0) {
                    // Either on an earlier segment, or on the same one but not behind
                    int before = bestBefore;
                    if (along[p - 1][s] <= along[p][s]
                        && (before < 0 || cost[p - 1][s] < cost[p - 1][before])) before = s;
                    if (before < 0) {
                        cost[p][s] = IMPOSSIBLE;
                        before = s;
                    } else {
                        cost[p][s] += cost[p - 1][before];
                    }
                    from[p][s] = before;
                    if (bestBefore < 0 || cost[p - 1][s] < cost[p - 1][bestBefore]) bestBefore = s;
                }
            }
        }

        int best = 0;
        for (int s = 1; s < segments; s++) {
            if (cost[points - 1][s] < cost[points - 1][best]) best = s;
        }
        double[] result = new double[points];
        for (int p = points - 1; p >= 0; p--) {
            result[p] = along[p][best];
            best = from[p][best];
        }
        for (int p = 1; p < points; p++) {
            result[p] = Math.max(result[p], result[p - 1]); // only if there was no way to order them
        }
        return result;
    }
}
