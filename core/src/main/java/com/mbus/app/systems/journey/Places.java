package com.mbus.app.systems.journey;

import com.mbus.app.model.BusStop;
import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All places that have a name. Stops of the same name are one place if they are close to each other. */
public final class Places {

    private static final double SAME_PLACE_METERS = 250;

    private final List<Place> places = new ArrayList<>();
    private final Map<BusStop, Place> byStop = new IdentityHashMap<>();

    public Places(Timetable timetable) {
        List<Set<BusStop>> groups = new ArrayList<>();
        for (BusStop stop : timetable.getStops()) { // ordered by name
            Set<BusStop> group = groups.isEmpty() ? null : find(groups, stop);
            if (group == null) {
                group = new LinkedHashSet<>();
                groups.add(group);
            }
            group.add(stop);
        }
        for (Set<BusStop> group : groups) {
            Place place = Place.ofStops(group);
            places.add(place);
            for (BusStop stop : group) byStop.put(stop, place);
        }
    }

    private static Set<BusStop> find(List<Set<BusStop>> groups, BusStop stop) {
        for (int i = groups.size() - 1; i >= 0; i--) {
            Set<BusStop> group = groups.get(i);
            if (!group.iterator().next().name.equals(stop.name)) return null;
            boolean near = true;
            for (BusStop other : group) near &= Walking.meters(other.geo, stop.geo) <= SAME_PLACE_METERS;
            if (near) return group;
        }
        return null;
    }

    /** Ordered by name. */
    public List<Place> getAll() {
        return Collections.unmodifiableList(places);
    }

    public Place of(BusStop stop) {
        return byStop.get(stop);
    }

    public Place of(Geolocation point) {
        Place nearest = places.get(0);
        for (Place place : places) {
            if (Walking.meters(place.geo, point) < Walking.meters(nearest.geo, point)) nearest = place;
        }
        return Place.ofPoint(point, nearest.name);
    }
}
