package com.mbus.app.systems.journey;

import com.mbus.app.model.BusStop;
import com.mbus.app.utils.Geolocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Where a journey starts or ends: a stop with all its sides and platforms, an address, or any point of the map. */
public final class Place {

    /** Of the stop or the address, or of what is next to the point. */
    public final String name;
    public final Geolocation geo;
    /** Empty for a point. */
    public final Set<BusStop> stops;
    private final boolean point;

    private Place(String name, Geolocation geo, Set<BusStop> stops, boolean point) {
        this.point = point;
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
        return new Place(stops.iterator().next().name, new Geolocation(lat, lng), new HashSet<>(stops), false);
    }

    static Place ofPoint(Geolocation geo, String nearby) {
        return new Place(nearby, geo, Collections.emptySet(), true);
    }

    static Place ofAddress(Geolocation geo, String address) {
        return new Place(address, geo, Collections.emptySet(), false);
    }

    /** True if its name is that of something nearby. */
    public boolean isPoint() {
        return point;
    }
}
