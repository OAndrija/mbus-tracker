package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Where the buses of one line are at a given time. */
public class LineMotion {

    private static final double DWELL_MINUTES = 8 / 60.0;
    private static final double MAX_DWELL_SHARE = 0.25;
    private static final double RAMP_MINUTES = 12 / 60.0;
    private static final double MAX_RAMP_SHARE = 0.35;
    private static final double FADE_MINUTES = 1.5 / 60.0;
    /** Remaining track shorter than this is not driven after the last timing point. */
    private static final double MIN_TAIL = 8;

    private final RouteTrack track;
    private final Map<Integer, Double> stopDistance = new HashMap<>();

    public LineMotion(BusLine line, RouteTrack track, WorldGeometry geometry) {
        this.track = track;
        double previous = 0;
        for (BusStop stop : line.getStops()) {
            Vector2 position = geometry.getStop(stop);
            if (position == null) position = geometry.project(stop.geo);
            // Stops are in route order, so a bus must never be sent backwards.
            previous = Math.max(previous, track.nearestTo(position.x, position.y));
            stopDistance.put(stop.idAvpost, previous);
        }
    }

    public RouteTrack getTrack() {
        return track;
    }

    /** The time a trip is over, including the way from its last timing point to the end of the route. */
    public double endOf(BusSchedule trip) {
        List<BusSchedule.StopTime> times = trip.getStopTimes();
        double arrival = times.get(times.size() - 1).arrivalTime;
        double tail = tailLength(times);
        if (tail <= 0) return arrival + DWELL_MINUTES;
        return arrival + DWELL_MINUTES + tailMinutes(times, tail);
    }

    public boolean isRunning(BusSchedule trip, double time) {
        return trip.getStopTimes().size() >= 2
            && time >= trip.getStopTimes().get(0).arrivalTime && time <= endOf(trip);
    }

    /** Distance along the track. Only meaningful while the trip is running. */
    public double distanceAt(BusSchedule trip, double time) {
        List<BusSchedule.StopTime> times = trip.getStopTimes();
        int last = times.size() - 1;

        for (int i = 0; i < last; i++) {
            double arrival = times.get(i).arrivalTime;
            double next = times.get(i + 1).arrivalTime;
            if (time >= next) continue;

            double departure = i == 0 ? arrival : arrival + dwell(next - arrival);
            double from = distanceOf(times.get(i));
            double to = distanceOf(times.get(i + 1));
            return from + (to - from) * ease(time - departure, next - departure);
        }

        double from = distanceOf(times.get(last));
        double tail = tailLength(times);
        if (tail <= 0) return from;
        double departure = times.get(last).arrivalTime + DWELL_MINUTES;
        return from + tail * ease(time - departure, tailMinutes(times, tail));
    }

    /** Fades a bus in at the start of its trip and out at the end. */
    public float alphaAt(BusSchedule trip, double time) {
        double start = trip.getStopTimes().get(0).arrivalTime;
        double alpha = Math.min((time - start) / FADE_MINUTES, (endOf(trip) - time) / FADE_MINUTES);
        return (float) Math.max(0, Math.min(1, alpha));
    }

    private double distanceOf(BusSchedule.StopTime stopTime) {
        Double distance = stopDistance.get(stopTime.stopId);
        return distance != null ? distance : 0;
    }

    private static double dwell(double segmentMinutes) {
        return Math.min(DWELL_MINUTES, segmentMinutes * MAX_DWELL_SHARE);
    }

    private double tailLength(List<BusSchedule.StopTime> times) {
        double tail = track.length() - distanceOf(times.get(times.size() - 1));
        return tail > MIN_TAIL ? tail : 0;
    }

    /** At the average speed of the trip so far. */
    private double tailMinutes(List<BusSchedule.StopTime> times, double tail) {
        BusSchedule.StopTime first = times.get(0);
        BusSchedule.StopTime last = times.get(times.size() - 1);
        double driven = distanceOf(last) - distanceOf(first);
        double minutes = last.arrivalTime - first.arrivalTime;
        return driven > 0 && minutes > 0 ? tail * minutes / driven : 1;
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
