package com.mbus.app.systems.journey;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Finds journeys with RAPTOR (Delling, Pajor, Werneck: Round-Based Public Transit Routing, 2012): round
 * by round, it finds the earliest arrival everywhere with one bus, two buses and so on. This is repeated
 * for every minute at which it makes sense to leave, so that all good journeys of a period are found.
 * May be used from any thread.
 */
public final class JourneyPlanner {

    private static final int INFINITE = Walking.TOO_FAR;
    static final int MAX_RIDES = 4;
    /** To get from one bus to the next at the same stop, or after a walk to another stop. */
    static final int CHANGE_MINUTES = 1;
    static final int MAX_TRANSFER_WALK = 7;
    static final int MAX_ACCESS_WALK = 17;
    /** Used where there is no stop closer. */
    private static final int MAX_FAR_ACCESS_WALK = 45;
    private static final int FAR_ACCESS_STOPS = 3;

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int[] PERIODS = {90, 180, 360, MINUTES_PER_DAY};
    private static final int ENOUGH = 4;
    private static final int MAX_RESULTS = 5;
    /** Another change has to save more than this. */
    private static final int WORTH_A_CHANGE = 3;
    private static final int MUCH_LATER = 10;
    private static final int MAX_WALK_ALL_THE_WAY = 30;
    /** How far to look for the way on foot, to leave out journeys that are slower. */
    private static final int MAX_WALK = 120;
    private static final int CACHED_DAYS = 4;
    private static final int CACHED_PLACES = 8;

    private final Timetable timetable;
    private final Walking walking;
    private final List<BusStop> stops;
    private final Map<BusStop, Integer> stopIndex = new IdentityHashMap<>();
    /** Stops one can walk to from every stop, and the minutes. */
    private final int[][] neighbors, neighborMinutes;

    private final Map<LocalDate, Day> days = new LinkedHashMap<LocalDate, Day>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LocalDate, Day> eldest) {
            return size() > CACHED_DAYS;
        }
    };

    private final Map<Place, int[]> walksByPlace = new LinkedHashMap<Place, int[]>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Place, int[]> eldest) {
            return size() > CACHED_PLACES;
        }
    };

    /** Takes a moment, as it finds all walks between stops. */
    public JourneyPlanner(Timetable timetable, Walking walking) {
        this.timetable = timetable;
        this.walking = walking;
        this.stops = timetable.getStops();
        int n = stops.size();
        for (int i = 0; i < n; i++) stopIndex.put(stops.get(i), i);

        neighbors = new int[n][];
        neighborMinutes = new int[n][];
        for (int i = 0; i < n; i++) {
            int[] found = new int[n];
            int[] minutes = new int[n];
            int count = 0;
            int[] walks = walking.toStops(stops.get(i).geo, stops, MAX_TRANSFER_WALK);
            for (int j = 0; j < n; j++) {
                if (i == j || walks[j] == INFINITE) continue;
                found[count] = j;
                minutes[count++] = walks[j];
            }
            neighbors[i] = Arrays.copyOf(found, count);
            neighborMinutes[i] = Arrays.copyOf(minutes, count);
        }
    }

    // Search

    /** The next journeys from a time on, ordered by departure. Looks at the next day if there are none. */
    public List<Journey> leaveAt(Place from, Place to, LocalDate date, int time) {
        int walk = walkAllTheWay(from, to);
        List<Journey> found = Collections.emptyList();
        for (int period : PERIODS) {
            found = withoutWaste(walk, search(from, to, date, time, Math.min(MINUTES_PER_DAY, time + period)));
            if (found.size() >= ENOUGH) break;
        }
        if (found.isEmpty()) {
            for (int period : PERIODS) {
                found = withoutWaste(walk, search(from, to, date.plusDays(1), 0, period + 240));
                if (found.size() >= ENOUGH) break;
            }
        }
        List<Journey> result = new ArrayList<>(found.subList(0, Math.min(MAX_RESULTS, found.size())));
        addWalk(result, from, to, date, time, walk);
        addPaths(result);
        return result;
    }

    /** The last journeys that arrive by a time, the latest first. */
    public List<Journey> arriveBy(Place from, Place to, LocalDate date, int time) {
        int walk = walkAllTheWay(from, to);
        List<Journey> found = Collections.emptyList();
        for (int period : PERIODS) {
            List<Journey> inTime = new ArrayList<>();
            for (Journey journey : search(from, to, date, Math.max(0, time - period), time)) {
                if (journey.getArrival() <= time) inTime.add(journey);
            }
            found = withoutWaste(walk, inTime);
            if (found.size() >= ENOUGH) break;
        }
        List<Journey> result = new ArrayList<>(
            found.subList(Math.max(0, found.size() - MAX_RESULTS), found.size()));
        Collections.reverse(result);
        if (time - walk >= 0) addWalk(result, from, to, date, time - walk, walk);
        addPaths(result);
        return result;
    }

    private int walkAllTheWay(Place from, Place to) {
        return walking.between(from.geo, to.geo, MAX_WALK);
    }

    private void addWalk(List<Journey> journeys, Place from, Place to, LocalDate date, int start, int minutes) {
        if (minutes == 0 || minutes > MAX_WALK_ALL_THE_WAY) return;
        journeys.add(0, new Journey(date, from, to, Collections.singletonList(
            Journey.Leg.walk(null, from.geo, null, to.geo, start, minutes))));
    }

    private void addPaths(List<Journey> journeys) {
        for (Journey journey : journeys) {
            for (Journey.Leg leg : journey.getLegs()) {
                if (!leg.isRide()) leg.setPath(walking.path(leg.from, leg.to));
            }
        }
    }

    /**
     * Leaves out what nobody would do: be slower than on foot, change to save a minute or two, or leave a
     * minute or two later to arrive much later.
     */
    private static List<Journey> withoutWaste(int walk, List<Journey> journeys) {
        List<Journey> result = new ArrayList<>();
        for (Journey journey : journeys) {
            boolean waste = journey.getMinutes() >= walk;
            for (Journey other : journeys) {
                waste |= other.getRides() < journey.getRides()
                    && other.getDeparture() >= journey.getDeparture() - WORTH_A_CHANGE
                    && other.getArrival() <= journey.getArrival() + WORTH_A_CHANGE;
                waste |= other.getRides() <= journey.getRides()
                    && other.getDeparture() >= journey.getDeparture() - WORTH_A_CHANGE
                    && other.getArrival() <= journey.getArrival() - MUCH_LATER;
            }
            if (!waste) result.add(journey);
        }
        return result;
    }

    /**
     * All journeys that leave in a period and are not beaten by another one that leaves no earlier,
     * arrives no later and needs no more buses. Ordered by departure.
     */
    List<Journey> search(Place from, Place to, LocalDate date, int earliest, int latest) {
        Day day = getDay(date);
        int[] access = walksFrom(from);
        int[] egress = walksFrom(to);

        TreeSet<Integer> departures = new TreeSet<>();
        for (int stop = 0; stop < access.length; stop++) {
            if (access[stop] >= INFINITE) continue;
            for (int visit : day.visits[stop]) {
                Route route = day.routes.get(visit >> 8);
                int position = visit & 0xff;
                if (position == route.stops.length - 1) continue;
                for (int[] times : route.departures) {
                    int leave = times[position] - access[stop];
                    if (leave >= earliest && leave <= latest) departures.add(leave);
                }
            }
        }

        List<Journey> journeys = new ArrayList<>();
        Run run = new Run(day, access, egress);
        for (int time : departures.descendingSet()) {
            journeys.addAll(run.from(time, date, from, to));
        }
        return pareto(journeys);
    }

    private static List<Journey> pareto(List<Journey> journeys) {
        List<Journey> sorted = new ArrayList<>(journeys);
        sorted.sort((a, b) -> {
            if (a.getArrival() != b.getArrival()) return Integer.compare(a.getArrival(), b.getArrival());
            if (a.getDeparture() != b.getDeparture()) return Integer.compare(b.getDeparture(), a.getDeparture());
            if (a.getRides() != b.getRides()) return Integer.compare(a.getRides(), b.getRides());
            return Integer.compare(a.getWalkMinutes(), b.getWalkMinutes());
        });
        List<Journey> kept = new ArrayList<>();
        for (Journey journey : sorted) {
            boolean beaten = false;
            for (Journey other : kept) {
                beaten |= other.getDeparture() >= journey.getDeparture() && other.getRides() <= journey.getRides();
            }
            if (!beaten) kept.add(journey);
        }
        kept.sort((a, b) -> a.getDeparture() != b.getDeparture()
            ? Integer.compare(a.getDeparture(), b.getDeparture()) : Integer.compare(a.getArrival(), b.getArrival()));
        return kept;
    }

    /** Minutes from a place to every stop in walking distance, infinite for the others. */
    synchronized int[] walksFrom(Place place) {
        return walksByPlace.computeIfAbsent(place, this::findWalks);
    }

    private int[] findWalks(Place place) {
        int[] minutes = walking.toStops(place.geo, stops, MAX_ACCESS_WALK);
        boolean found = false;
        for (int i = 0; i < minutes.length; i++) {
            if (place.stops.contains(stops.get(i))) minutes[i] = 0;
            found |= minutes[i] < INFINITE;
        }
        if (found) return minutes;

        int[] far = walking.toStops(place.geo, stops, MAX_FAR_ACCESS_WALK);
        for (int count = 0; count < FAR_ACCESS_STOPS; count++) {
            int nearest = 0;
            for (int i = 1; i < far.length; i++) {
                if (far[i] < far[nearest]) nearest = i;
            }
            if (far[nearest] == INFINITE) break;
            minutes[nearest] = far[nearest];
            far[nearest] = INFINITE;
        }
        return minutes;
    }

    /** Minutes to walk from one stop to another to change, or infinite. */
    int getTransferWalk(int from, int to) {
        for (int i = 0; i < neighbors[from].length; i++) {
            if (neighbors[from][i] == to) return neighborMinutes[from][i];
        }
        return INFINITE;
    }

    // Trips of a day

    /** Trips with the same stops that never overtake each other, ordered by time. */
    private static final class Route {
        final int[] stops;
        final List<Trip> trips = new ArrayList<>();
        final List<int[]> arrivals = new ArrayList<>();
        final List<int[]> departures = new ArrayList<>();

        Route(int[] stops) {
            this.stops = stops;
        }

        boolean canFollow(int[] nextArrivals, int[] nextDepartures) {
            int last = trips.size() - 1;
            for (int i = 0; i < stops.length; i++) {
                if (nextArrivals[i] < arrivals.get(last)[i] || nextDepartures[i] < departures.get(last)[i]) return false;
            }
            return true;
        }

        /** Position of the first trip that leaves a stop at or after a time, or -1. */
        int firstFrom(int position, int time) {
            int low = 0;
            int high = trips.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (departures.get(middle)[position] >= time) high = middle;
                else low = middle + 1;
            }
            return low < trips.size() ? low : -1;
        }
    }

    private static final class Day {
        final List<Route> routes = new ArrayList<>();
        /** For every stop, where routes visit it: number of the route in the upper bits, position in the lower 8. */
        int[][] visits;
    }

    private synchronized Day getDay(LocalDate date) {
        return days.computeIfAbsent(date, this::createDay);
    }

    private Day createDay(LocalDate date) {
        Map<RoutePattern, List<Trip>> byPattern = new LinkedHashMap<>();
        for (BusLine line : timetable.getLines()) {
            for (RoutePattern pattern : line.getPatterns()) byPattern.put(pattern, new ArrayList<>());
        }
        for (Trip trip : timetable.getTrips(date)) byPattern.get(trip.pattern).add(trip); // ordered by start

        Day day = new Day();
        for (Map.Entry<RoutePattern, List<Trip>> entry : byPattern.entrySet()) {
            List<BusStop> patternStops = entry.getKey().getStops();
            int[] indices = new int[patternStops.size()];
            for (int i = 0; i < indices.length; i++) indices[i] = stopIndex.get(patternStops.get(i));

            List<Route> routes = new ArrayList<>();
            for (Trip trip : entry.getValue()) {
                int[] arrivals = new int[indices.length];
                int[] departures = new int[indices.length];
                for (int i = 0; i < indices.length; i++) {
                    arrivals[i] = trip.getArrival(i);
                    departures[i] = trip.getDeparture(i);
                }
                Route route = null;
                for (Route candidate : routes) {
                    if (candidate.canFollow(arrivals, departures)) {
                        route = candidate;
                        break;
                    }
                }
                if (route == null) {
                    route = new Route(indices);
                    routes.add(route);
                }
                route.trips.add(trip);
                route.arrivals.add(arrivals);
                route.departures.add(departures);
            }
            day.routes.addAll(routes);
        }

        List<List<Integer>> visits = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) visits.add(new ArrayList<>());
        for (int r = 0; r < day.routes.size(); r++) {
            int[] routeStops = day.routes.get(r).stops;
            if (routeStops.length > 0xff) throw new IllegalStateException("A trip has too many stops");
            for (int i = 0; i < routeStops.length; i++) visits.get(routeStops[i]).add(r << 8 | i);
        }
        day.visits = new int[stops.size()][];
        for (int i = 0; i < day.visits.length; i++) {
            day.visits[i] = visits.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        return day;
    }

    // RAPTOR

    private final class Run {
        private final Day day;
        private final int[] access, egress;
        private final int n = stops.size();

        /** By round: earliest arrival by bus, and by bus or after a walk from another stop. */
        private final int[][] ride = new int[MAX_RIDES + 1][n];
        private final int[][] ready = new int[MAX_RIDES + 1][n];
        private final Trip[][] rideTrip = new Trip[MAX_RIDES + 1][n];
        private final int[][] rideBoard = new int[MAX_RIDES + 1][n];
        private final int[][] rideAlight = new int[MAX_RIDES + 1][n];
        private final int[][] walkFrom = new int[MAX_RIDES + 1][n];
        private final int[] bestRide = new int[n];
        private final int[] bestReady = new int[n];
        private boolean[] marked = new boolean[n];
        private boolean[] nextMarked = new boolean[n];
        private final boolean[] reached = new boolean[n];
        private final int[] firstPosition;

        Run(Day day, int[] access, int[] egress) {
            this.day = day;
            this.access = access;
            this.egress = egress;
            firstPosition = new int[day.routes.size()];
        }

        /** The earliest arrival with every number of buses, if leaving at a time. */
        List<Journey> from(int time, LocalDate date, Place from, Place to) {
            for (int k = 0; k <= MAX_RIDES; k++) {
                Arrays.fill(ride[k], INFINITE);
                Arrays.fill(ready[k], INFINITE);
            }
            Arrays.fill(bestRide, INFINITE);
            Arrays.fill(bestReady, INFINITE);
            Arrays.fill(marked, false);
            boolean any = false;
            for (int s = 0; s < n; s++) {
                if (access[s] >= INFINITE) continue;
                ready[0][s] = bestReady[s] = time + access[s];
                marked[s] = any = true;
            }

            List<Journey> journeys = new ArrayList<>();
            int bestArrival = INFINITE;
            for (int k = 1; k <= MAX_RIDES && any; k++) {
                Arrays.fill(nextMarked, false);
                Arrays.fill(reached, false);
                scanRoutes(k, bestArrival);
                any = walk(k);

                int target = -1;
                for (int s = 0; s < n; s++) {
                    if (!reached[s] || egress[s] >= INFINITE) continue;
                    int arrival = ride[k][s] + egress[s];
                    if (arrival < bestArrival
                        || (target >= 0 && arrival == bestArrival && egress[s] < egress[target])) {
                        bestArrival = arrival;
                        target = s;
                    }
                }
                if (target >= 0) journeys.add(trace(k, target, date, from, to));

                boolean[] swap = marked;
                marked = nextMarked;
                nextMarked = swap;
            }
            return journeys;
        }

        private void scanRoutes(int k, int bestArrival) {
            Arrays.fill(firstPosition, Integer.MAX_VALUE);
            for (int s = 0; s < n; s++) {
                if (!marked[s]) continue;
                for (int visit : day.visits[s]) {
                    int route = visit >> 8;
                    firstPosition[route] = Math.min(firstPosition[route], visit & 0xff);
                }
            }

            int change = k == 1 ? 0 : CHANGE_MINUTES;
            for (int r = 0; r < firstPosition.length; r++) {
                if (firstPosition[r] == Integer.MAX_VALUE) continue;
                Route route = day.routes.get(r);
                int trip = -1;
                int board = -1;
                for (int i = firstPosition[r]; i < route.stops.length; i++) {
                    int s = route.stops[i];
                    if (trip >= 0) {
                        int arrival = route.arrivals.get(trip)[i];
                        // Not compared with arrivals on foot: only from a bus one may walk on.
                        if (arrival < bestRide[s] && arrival < bestArrival) {
                            ride[k][s] = bestRide[s] = arrival;
                            rideTrip[k][s] = route.trips.get(trip);
                            rideBoard[k][s] = board;
                            rideAlight[k][s] = i;
                            reached[s] = true;
                        }
                    }
                    if (!marked[s] || ready[k - 1][s] >= INFINITE) continue;
                    int boardable = route.firstFrom(i, ready[k - 1][s] + change);
                    if (boardable >= 0 && (trip < 0 || boardable < trip)) {
                        trip = boardable;
                        board = i;
                    }
                }
            }
        }

        /** Returns whether any stop can be left earlier than before. */
        private boolean walk(int k) {
            boolean any = false;
            for (int s = 0; s < n; s++) {
                if (!reached[s]) continue;
                any |= improve(k, s, ride[k][s], -1);
                for (int i = 0; i < neighbors[s].length; i++) {
                    any |= improve(k, neighbors[s][i], ride[k][s] + neighborMinutes[s][i], s);
                }
            }
            return any;
        }

        private boolean improve(int k, int stop, int time, int from) {
            if (time >= bestReady[stop]) return false;
            ready[k][stop] = bestReady[stop] = time;
            walkFrom[k][stop] = from;
            nextMarked[stop] = true;
            return true;
        }

        private Journey trace(int k, int target, LocalDate date, Place from, Place to) {
            List<Journey.Leg> legs = new ArrayList<>();
            BusStop last = stops.get(target);
            if (egress[target] > 0) {
                legs.add(Journey.Leg.walk(last, last.geo, null, to.geo, ride[k][target], egress[target]));
            }

            int stop = target;
            for (int round = k; round >= 1; round--) {
                Trip trip = rideTrip[round][stop];
                Journey.Leg leg = Journey.Leg.ride(trip, rideBoard[round][stop], rideAlight[round][stop]);
                legs.add(leg);
                int board = stopIndex.get(leg.fromStop);
                if (round == 1) {
                    if (access[board] > 0) {
                        // Not earlier than necessary
                        legs.add(Journey.Leg.walk(null, from.geo, leg.fromStop, leg.from,
                            leg.start - access[board], access[board]));
                    }
                    break;
                }
                int walkedFrom = walkFrom[round - 1][board];
                if (walkedFrom >= 0) {
                    BusStop start = stops.get(walkedFrom);
                    legs.add(Journey.Leg.walk(start, start.geo, leg.fromStop, leg.from, ride[round - 1][walkedFrom],
                        ready[round - 1][board] - ride[round - 1][walkedFrom]));
                    stop = walkedFrom;
                } else {
                    stop = board;
                }
            }
            Collections.reverse(legs);
            return new Journey(date, from, to, legs);
        }
    }
}
