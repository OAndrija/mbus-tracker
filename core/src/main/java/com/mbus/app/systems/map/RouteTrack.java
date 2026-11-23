package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;

/** A route as a track: converts between distance along the route and position. */
public class RouteTrack {

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
     * The distance along the route at which it passes closest to a point. Routes may use a road twice,
     * so this starts from the closest route point, which is also what the order of stops is based on,
     * and only looks at the two segments around it.
     */
    public double nearestTo(float px, float py) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            double dx = px - x[i];
            double dy = py - y[i];
            double d = dx * dx + dy * dy;
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = i;
            }
        }

        double best = nearestDistance;
        double result = distance[nearest];
        for (int i = Math.max(0, nearest - 1); i <= nearest && i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i];
            double dy = y[i + 1] - y[i];
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0 ? 0 : ((px - x[i]) * dx + (py - y[i]) * dy) / lengthSquared;
            t = Math.max(0, Math.min(1, t));
            double ex = px - x[i] - t * dx;
            double ey = py - y[i] - t * dy;
            double d = ex * ex + ey * ey;
            if (d < best) {
                best = d;
                result = distance[i] + t * (distance[i + 1] - distance[i]);
            }
        }
        return result;
    }
}
