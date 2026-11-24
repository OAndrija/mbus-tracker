package com.mbus.app.systems.journey;

import com.mbus.app.model.BusStop;
import com.mbus.app.model.Trip;
import com.mbus.app.utils.Geolocation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One way to get from a place to another. Times are minutes since midnight of its date. */
public final class Journey {

    /** A part of a journey: a walk, or a ride on one bus. */
    public static final class Leg {
        /** Null for a walk. */
        public final Trip trip;
        /** Positions in the trip where one gets on and off. */
        public final int boardIndex, alightIndex;
        /** The stops a walk leads from and to. Null at the start and the end of the journey. */
        public final BusStop fromStop, toStop;
        public final Geolocation from, to;
        public final int start, end;

        private Leg(Trip trip, int boardIndex, int alightIndex, BusStop fromStop, BusStop toStop,
                    Geolocation from, Geolocation to, int start, int end) {
            this.trip = trip;
            this.boardIndex = boardIndex;
            this.alightIndex = alightIndex;
            this.fromStop = fromStop;
            this.toStop = toStop;
            this.from = from;
            this.to = to;
            this.start = start;
            this.end = end;
        }

        static Leg ride(Trip trip, int boardIndex, int alightIndex) {
            BusStop board = trip.pattern.getStops().get(boardIndex);
            BusStop alight = trip.pattern.getStops().get(alightIndex);
            return new Leg(trip, boardIndex, alightIndex, board, alight, board.geo, alight.geo,
                trip.getDeparture(boardIndex), trip.getArrival(alightIndex));
        }

        static Leg walk(BusStop fromStop, Geolocation from, BusStop toStop, Geolocation to, int start, int minutes) {
            return new Leg(null, -1, -1, fromStop, toStop, from, to, start, start + minutes);
        }

        public boolean isRide() {
            return trip != null;
        }

        public int getMinutes() {
            return end - start;
        }
    }

    public final LocalDate date;
    public final Place from, to;
    private final List<Leg> legs;
    private final int rides;
    private final int walkMinutes;

    Journey(LocalDate date, Place from, Place to, List<Leg> legs) {
        this.date = date;
        this.from = from;
        this.to = to;
        this.legs = Collections.unmodifiableList(new ArrayList<>(legs));
        int rideCount = 0;
        int walking = 0;
        for (Leg leg : legs) {
            if (leg.isRide()) rideCount++;
            else walking += leg.getMinutes();
        }
        rides = rideCount;
        walkMinutes = walking;
    }

    public List<Leg> getLegs() {
        return legs;
    }

    public int getDeparture() {
        return legs.get(0).start;
    }

    public int getArrival() {
        return legs.get(legs.size() - 1).end;
    }

    public int getMinutes() {
        return getArrival() - getDeparture();
    }

    public int getRides() {
        return rides;
    }

    public int getChanges() {
        return Math.max(0, rides - 1);
    }

    public int getWalkMinutes() {
        return walkMinutes;
    }

    /** True if both use the same buses between the same stops. */
    public boolean isSameAs(Journey other) {
        if (other == null || !date.equals(other.date) || legs.size() != other.legs.size()) return false;
        for (int i = 0; i < legs.size(); i++) {
            Leg a = legs.get(i);
            Leg b = other.legs.get(i);
            if (a.trip != b.trip || a.boardIndex != b.boardIndex || a.alightIndex != b.alightIndex
                || a.start != b.start || a.end != b.end) return false;
        }
        return true;
    }
}
