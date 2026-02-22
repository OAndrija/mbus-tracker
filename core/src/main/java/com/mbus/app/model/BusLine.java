package com.mbus.app.model;

import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusLine {

    public final int lineId;
    public final int variantId;
    public final int direction;  // -1 or 1
    public final double length;
    public final String name;
    public final String note;
    public final String providerName;
    public final String providerLink;

    private final List<Geolocation> path;

    private final List<double[]> originalCoordinates;

    private final List<BusStop> stops;

    private final List<BusSchedule> schedules;

    public BusLine(int lineId,
                   int variantId,
                   int direction,
                   double length,
                   String name,
                   String note,
                   String providerName,
                   String providerLink,
                   List<Geolocation> path,
                   List<double[]> originalCoordinates,
                   List<BusStop> stops,
                   List<BusSchedule> schedules) {

        this.lineId = lineId;
        this.variantId = variantId;
        this.direction = direction;
        this.length = length;
        this.name = name;
        this.note = note;
        this.providerName = providerName;
        this.providerLink = providerLink;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.originalCoordinates = originalCoordinates != null
            ? Collections.unmodifiableList(new ArrayList<>(originalCoordinates))
            : null;
        this.stops = stops != null
            ? Collections.unmodifiableList(new ArrayList<>(stops))
            : Collections.unmodifiableList(new ArrayList<>());
        this.schedules = schedules != null
            ? Collections.unmodifiableList(new ArrayList<>(schedules))
            : Collections.unmodifiableList(new ArrayList<>());
    }

    public BusLine(int lineId,
                   int variantId,
                   int direction,
                   double length,
                   String name,
                   String note,
                   String providerName,
                   String providerLink,
                   List<Geolocation> path,
                   List<double[]> originalCoordinates,
                   List<BusStop> stops) {
        this(lineId, variantId, direction, length, name, note, providerName,
            providerLink, path, originalCoordinates, stops, null);
    }

    public List<Geolocation> getPath() {
        return path;
    }

    public List<double[]> getOriginalCoordinates() {
        return originalCoordinates;
    }

    public List<BusStop> getStops() {
        return stops;
    }

    public List<BusSchedule> getSchedules() {
        return schedules;
    }

    public int getPointCount() {
        return path.size();
    }

    public int getStopCount() {
        return stops.size();
    }

    public int getScheduleCount() {
        return schedules.size();
    }

    @Override
    public String toString() {
        return "BusLine{" +
            "lineId=" + lineId +
            ", variantId=" + variantId +
            ", direction=" + direction +
            ", name='" + name + '\'' +
            ", points=" + path.size() +
            ", stops=" + stops.size() +
            ", schedules=" + schedules.size() +
            '}';
    }
}
