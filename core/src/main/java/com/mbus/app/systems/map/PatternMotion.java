package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Trip;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Where the buses that drive a pattern are at a given time. */
public class PatternMotion {

    private static final double DWELL_MINUTES = 8 / 60.0;
    private static final double MAX_DWELL_SHARE = 0.2;
    private static final double RAMP_MINUTES = 10 / 60.0;
    private static final double MAX_RAMP_SHARE = 0.35;
    private static final double FADE_MINUTES = 1.5 / 60.0;
    /** A bus listed for a minute is there before the next one. */
    private static final double MINUTE = 0.95;
    private static final int SMOOTHING_ROUNDS = 60;
    /** What stopping costs, in world pixels of driving. */
    private static final double STOPPING_DISTANCE = 60;

    private final RouteTrack track;
    private final double[] stopDistance;
    private final Map<Trip, double[]> arrivals = new IdentityHashMap<>();

    public PatternMotion(RoutePattern pattern, RouteTrack track, WorldGeometry geometry) {
        this.track = track;
        List<BusStop> stops = pattern.getStops();
        float[] x = new float[stops.size()];
        float[] y = new float[stops.size()];
        for (int i = 0; i < x.length; i++) {
            Vector2 position = geometry.getStop(stops.get(i));
            x[i] = position.x;
            y[i] = position.y;
        }
        stopDistance = track.placeInOrder(x, y);
    }

    public RouteTrack getTrack() {
        return track;
    }

    public double getStopDistance(int index) {
        return stopDistance[index];
    }

    public double startOf(Trip trip) {
        return trip.getStart();
    }

    public double endOf(Trip trip) {
        double[] times = arrivalsOf(trip);
        return times[times.length - 1] + DWELL_MINUTES + FADE_MINUTES;
    }

    public boolean isRunning(Trip trip, double time) {
        return time >= startOf(trip) && time <= endOf(trip);
    }

    /** Distance along the track. */
    public double distanceAt(Trip trip, double time) {
        int last = trip.getStopCount() - 1;
        if (time <= trip.getStart()) return stopDistance[0];

        double[] times = arrivalsOf(trip);
        for (int i = 0; i < last; i++) {
            double next = times[i + 1];
            if (time < next) {
                double departure = departure(trip, i, times[i], next);
                return stopDistance[i]
                    + (stopDistance[i + 1] - stopDistance[i]) * ease(time - departure, next - departure);
            }
        }
        return stopDistance[last];
    }

    private double departure(Trip trip, int index, double arrival, double next) {
        if (index == 0) return trip.getStart();
        double dwell = Math.min(DWELL_MINUTES, (next - arrival) * MAX_DWELL_SHARE);
        double scheduled = trip.getDeparture(index);
        // A longer stop in the timetable counts if it leaves time to drive.
        return scheduled > arrival + dwell && scheduled < next - dwell ? scheduled : arrival + dwell;
    }

    /**
     * Arrivals with fractions of a minute. The timetable only has whole minutes, so a bus may come at
     * any moment of its minute. The moments are chosen so that it drives as evenly as possible: every
     * stop is moved between its neighbors by distance, as far as its own minute allows.
     */
    private double[] arrivalsOf(Trip trip) {
        double[] times = arrivals.get(trip);
        if (times == null) {
            times = smoothArrivals(trip);
            arrivals.put(trip, times);
        }
        return times;
    }

    private double[] smoothArrivals(Trip trip) {
        int last = trip.getStopCount() - 1;
        double[] times = new double[last + 1];
        double[] hop = new double[last + 1]; // to the stop, including a share for stopping there
        double total = 0;
        for (int i = 0; i <= last; i++) {
            times[i] = trip.getArrival(i);
            if (i > 0) {
                hop[i] = stopDistance[i] - stopDistance[i - 1] + STOPPING_DISTANCE;
                total += hop[i];
            }
        }
        times[0] = trip.getStart();
        double pace = Math.max(1, trip.getEnd() - trip.getStart()) / total;

        for (int round = 0; round < SMOOTHING_ROUNDS; round++) {
            for (int i = 1; i <= last; i++) {
                double time = i < last
                    ? times[i - 1] + (times[i + 1] - times[i - 1]) * hop[i] / (hop[i] + hop[i + 1])
                    : times[i - 1] + hop[i] * pace;
                times[i] = Math.max(trip.getArrival(i), Math.min(trip.getArrival(i) + MINUTE, time));
            }
        }
        return times;
    }

    /** Fades a bus in at the start of its trip and out at the end. */
    public float alphaAt(Trip trip, double time) {
        double alpha = Math.min((time - startOf(trip)) / FADE_MINUTES, (endOf(trip) - time) / FADE_MINUTES);
        return (float) Math.max(0, Math.min(1, alpha));
    }

    /** Share of a segment covered after some time: pull away, cruise, brake. */
    static double ease(double elapsed, double duration) {
        if (duration <= 0 || elapsed >= duration) return 1;
        if (elapsed <= 0) return 0;

        double ramp = Math.min(MAX_RAMP_SHARE, RAMP_MINUTES / duration);
        double t = elapsed / duration;
        double cruise = 1 / (1 - ramp);
        if (t < ramp) return cruise * t * t / (2 * ramp);
        if (t > 1 - ramp) return 1 - cruise * (1 - t) * (1 - t) / (2 * ramp);
        return cruise * (t - ramp / 2);
    }
}
