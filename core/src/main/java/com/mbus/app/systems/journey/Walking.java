package com.mbus.app.systems.journey;

import com.mbus.app.model.BusStop;
import com.mbus.app.systems.streets.WalkNetwork;
import com.mbus.app.utils.Geolocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * How long walks take and where they lead, on the streets if they are known. Where they are not, a walk is
 * the straight line and takes a little longer than it would.
 */
public final class Walking {

    public static final int TOO_FAR = Integer.MAX_VALUE / 2;

    private static final double METERS_PER_MINUTE = 75; // 4.5 km/h
    /** How much longer the way is than the straight line, where the streets are not known. */
    private static final double DETOUR = 1.3;
    private static final double METERS_PER_DEGREE = 111_320;
    private static final double MAX_TO_STREET = 250;

    private final WalkNetwork network;
    private final Map<BusStop, WalkNetwork.Access> stopAccess = new IdentityHashMap<>();

    /** The network may be null. */
    public Walking(WalkNetwork network, List<BusStop> stops) {
        this.network = network;
        for (BusStop stop : stops) stopAccess.put(stop, findAccess(stop.geo));
    }

    private WalkNetwork.Access findAccess(Geolocation geo) {
        return network == null ? null : network.getAccess(geo, MAX_TO_STREET);
    }

    /** As the crow flies. */
    static double meters(Geolocation a, Geolocation b) {
        double lngScale = Math.cos(Math.toRadians((a.lat + b.lat) / 2));
        return METERS_PER_DEGREE * Math.hypot(a.lat - b.lat, (a.lng - b.lng) * lngScale);
    }

    static int minutes(double meters) {
        return meters >= TOO_FAR ? TOO_FAR : (int) Math.ceil(meters / METERS_PER_MINUTE - 1e-9);
    }

    /** Minutes from a point to every stop, or too far if it is more than a number of minutes. */
    int[] toStops(Geolocation from, List<BusStop> stops, int maxMinutes) {
        double limit = maxMinutes * METERS_PER_MINUTE;
        WalkNetwork.Access start = findAccess(from);
        WalkNetwork.Ways ways = start == null ? null : network.waysFrom(start, limit);
        int[] result = new int[stops.size()];
        for (int i = 0; i < result.length; i++) {
            BusStop stop = stops.get(i);
            double straight = meters(from, stop.geo);
            double meters = straight > limit ? TOO_FAR
                : through(ways, start, stopAccess.get(stop), straight);
            result[i] = minutes(meters) <= maxMinutes ? minutes(meters) : TOO_FAR;
        }
        return result;
    }

    /** Never shorter than the straight line, which is measured a tiny bit differently. */
    private static double through(WalkNetwork.Ways ways, WalkNetwork.Access start, WalkNetwork.Access end, double straight) {
        if (ways == null || end == null) return straight * DETOUR;
        double meters = ways.to(end);
        return meters == Double.POSITIVE_INFINITY ? TOO_FAR : Math.max(straight, start.distance + meters + end.distance);
    }

    /** Minutes between two points, or too far if it is more than a number of minutes. */
    int between(Geolocation from, Geolocation to, int maxMinutes) {
        double limit = maxMinutes * METERS_PER_MINUTE;
        double straight = meters(from, to);
        if (straight > limit) return TOO_FAR;
        WalkNetwork.Access start = findAccess(from), end = findAccess(to);
        WalkNetwork.Ways ways = start == null || end == null ? null : network.wayBetween(start, end, limit);
        int result = minutes(through(ways, start, end, straight));
        return result <= maxMinutes ? result : TOO_FAR;
    }

    /** The points of the way between two points, with both. */
    List<Geolocation> path(Geolocation from, Geolocation to) {
        WalkNetwork.Access start = findAccess(from), end = findAccess(to);
        if (start == null || end == null) return Arrays.asList(from, to);
        List<Geolocation> way = new ArrayList<>();
        way.add(from);
        way.addAll(network.wayBetween(start, end, Double.MAX_VALUE).pathTo(end));
        way.add(to);
        return way;
    }
}
