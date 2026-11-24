package com.mbus.app.systems.journey;

import com.mbus.app.model.BusStop;
import com.mbus.app.utils.Geolocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Where a journey starts or ends: a stop with all its sides and platforms, or any point of the map. */
public final class Place {

    /** Name of the stop, or of the stop next to the point. */
    public final String name;
    public final Geolocation geo;
    /** Empty for a point. */
    public final Set<BusStop> stops;

    private Place(String name, Geolocation geo, Set<BusStop> stops) {
        this.name = name;
        this.geo = geo;
        this.stops = Collections.unmodifiableSet(stops);
    }

    static Place ofStops(Set<BusStop> stops) {
        double lat = 0;
        double lng = 0;
        for (BusStop stop : stops) {
            lat += stop.geo.lat / stops.size();
            lng += stop.geo.lng / stops.size();
        }
        return new Place(stops.iterator().next().name, new Geolocation(lat, lng), new HashSet<>(stops));
    }

    static Place ofPoint(Geolocation geo, String nearestStop) {
        return new Place(nearestStop, geo, Collections.emptySet());
    }

    public boolean isPoint() {
        return stops.isEmpty();
    }
}
