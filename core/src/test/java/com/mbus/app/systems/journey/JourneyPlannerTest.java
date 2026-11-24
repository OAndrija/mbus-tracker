package com.mbus.app.systems.journey;

import com.badlogic.gdx.graphics.Color;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Service;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.systems.data.GtfsLoader;
import com.mbus.app.utils.Geolocation;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class JourneyPlannerTest {

    private static final int NEVER = 1_000_000;

    private static Timetable timetable;
    private static JourneyPlanner planner;
    private static Places places;
    private static LocalDate[] dates;

    @BeforeClass
    public static void load() throws IOException {
        timetable = GtfsLoader.load(new FileInputStream("data/marprom_gtfs.zip"));
        planner = new JourneyPlanner(timetable);
        places = new Places(timetable);
        LocalDate day = timetable.getValidUntil().minusWeeks(4);
        // A whole week, the last days with their holidays, and a day after expiry
        dates = new LocalDate[]{day, day.plusDays(1), day.plusDays(2), day.plusDays(3), day.plusDays(4),
            day.plusDays(5), day.plusDays(6), timetable.getValidUntil().minusDays(6),
            timetable.getValidUntil(), timetable.getValidUntil().plusDays(5)};
    }

    private static Place randomPlace(Random random) {
        if (random.nextInt(3) > 0) return places.getAll().get(random.nextInt(places.getAll().size()));
        return places.of(new Geolocation(46.51 + random.nextDouble() * 0.09, 15.58 + random.nextDouble() * 0.15));
    }

    /** Looks at every trip at every stop in every round, without any of the shortcuts of the planner. */
    private static int[] naiveArrivals(Place from, Place to, LocalDate date, int time) {
        List<BusStop> stops = timetable.getStops();
        Map<BusStop, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < stops.size(); i++) index.put(stops.get(i), i);
        int[] access = planner.walksFrom(from);
        int[] egress = planner.walksFrom(to);
        int[] ready = new int[stops.size()];
        for (int s = 0; s < ready.length; s++) ready[s] = access[s] < NEVER ? time + access[s] : NEVER;

        int[] best = new int[JourneyPlanner.MAX_RIDES + 1];
        Arrays.fill(best, NEVER);
        for (int k = 1; k <= JourneyPlanner.MAX_RIDES; k++) {
            int[] ride = new int[stops.size()];
            Arrays.fill(ride, NEVER);
            for (Trip trip : timetable.getTrips(date)) {
                boolean onBoard = false;
                List<BusStop> tripStops = trip.pattern.getStops();
                for (int i = 0; i < tripStops.size(); i++) {
                    int s = index.get(tripStops.get(i));
                    if (onBoard) ride[s] = Math.min(ride[s], trip.getArrival(i));
                    onBoard |= trip.getDeparture(i) >= ready[s] + (k == 1 ? 0 : JourneyPlanner.CHANGE_MINUTES);
                }
            }
            int[] next = ready.clone();
            best[k] = best[k - 1];
            for (int s = 0; s < next.length; s++) {
                if (ride[s] == NEVER) continue;
                if (egress[s] < NEVER) best[k] = Math.min(best[k], ride[s] + egress[s]);
                for (int other = 0; other < next.length; other++) {
                    double meters = Walking.meters(stops.get(s).geo, stops.get(other).geo);
                    if (s == other) next[s] = Math.min(next[s], ride[s]);
                    else if (meters <= JourneyPlanner.MAX_TRANSFER_METERS) {
                        next[other] = Math.min(next[other], ride[s] + Walking.minutes(meters));
                    }
                }
            }
            ready = next;
        }
        return best;
    }

    @Test
    public void findsTheSameBestArrivalsAsTheNaiveSearch() {
        Random random = new Random(7);
        int reachable = 0;
        int withChange = 0;
        for (int query = 0; query < 250; query++) {
            Place from = randomPlace(random);
            Place to = randomPlace(random);
            LocalDate date = dates[query % dates.length];
            int time = 4 * 60 + random.nextInt(19 * 60);

            int[] expected = naiveArrivals(from, to, date, time);
            List<Journey> journeys = planner.search(from, to, date, time, 24 * 60);
            for (int k = 1; k <= JourneyPlanner.MAX_RIDES; k++) {
                int arrival = NEVER;
                for (Journey journey : journeys) {
                    if (journey.getRides() <= k) arrival = Math.min(arrival, journey.getArrival());
                }
                assertEquals(from.name + " to " + to.name + " on " + date + " at " + time + " with " + k,
                    expected[k], arrival);
            }
            if (expected[JourneyPlanner.MAX_RIDES] < NEVER) reachable++;
            if (expected[JourneyPlanner.MAX_RIDES] < expected[1]) withChange++;
        }
        assertTrue("only " + reachable + " queries had an answer", reachable > 150);
        assertTrue("only " + withChange + " queries needed a change", withChange > 50);
    }

    @Test
    public void everyJourneyCanBeMade() {
        Random random = new Random(11);
        int checked = 0;
        for (int query = 0; query < 300; query++) {
            Place from = randomPlace(random);
            Place to = randomPlace(random);
            LocalDate date = dates[random.nextInt(dates.length)];
            int time = 4 * 60 + random.nextInt(20 * 60);
            boolean arriveBy = random.nextBoolean();
            List<Journey> journeys = arriveBy
                ? planner.arriveBy(from, to, date, time) : planner.leaveAt(from, to, date, time);
            for (Journey journey : journeys) {
                check(journey);
                checked++;
                if (arriveBy) {
                    assertEquals(date, journey.date);
                    assertTrue(journey.getArrival() <= time);
                } else {
                    assertTrue(journey.date.equals(date.plusDays(1))
                        || (journey.date.equals(date) && journey.getDeparture() >= time));
                }
            }
        }
        assertTrue(checked > 500);
    }

    private static void check(Journey journey) {
        assertSame(journey.from.geo, journey.getLegs().get(0).isRide() ? journey.from.geo : journey.getLegs().get(0).from);
        Journey.Leg previous = null;
        for (Journey.Leg leg : journey.getLegs()) {
            assertTrue(leg.end >= leg.start);
            if (leg.isRide()) {
                assertTrue("runs that day", timetable.getTrips(journey.date).contains(leg.trip));
                assertTrue(leg.boardIndex < leg.alightIndex);
                assertSame(leg.fromStop, leg.trip.pattern.getStops().get(leg.boardIndex));
                assertSame(leg.toStop, leg.trip.pattern.getStops().get(leg.alightIndex));
                assertEquals(leg.trip.getDeparture(leg.boardIndex), leg.start);
                assertEquals(leg.trip.getArrival(leg.alightIndex), leg.end);
            } else {
                assertTrue("not too fast", Walking.minutes(Walking.meters(leg.from, leg.to)) <= leg.getMinutes());
            }
            if (previous != null) {
                assertSame("starts where the last one ended", previous.toStop, leg.fromStop);
                assertFalse("two walks", !previous.isRide() && !leg.isRide());
                int rideBefore = journey.getLegs().indexOf(previous);
                boolean change = leg.isRide() && (previous.isRide() || rideBefore > 0);
                assertTrue("time to change", leg.start >= previous.end + (change ? JourneyPlanner.CHANGE_MINUTES : 0));
            }
            previous = leg;
        }
        Journey.Leg first = journey.getLegs().get(0);
        if (first.isRide()) assertTrue(journey.from.stops.contains(first.fromStop));
        if (previous.isRide()) assertTrue(journey.to.stops.contains(previous.toStop));
        else assertSame(journey.to.geo, previous.to);
    }

    @Test
    public void noJourneyIsBeatenByAnother() {
        Random random = new Random(3);
        for (int query = 0; query < 60; query++) {
            List<Journey> journeys = planner.search(randomPlace(random), randomPlace(random), dates[query % 7], 600, 800);
            for (Journey a : journeys) {
                for (Journey b : journeys) {
                    assertTrue(a == b || a.getDeparture() > b.getDeparture()
                        || a.getArrival() < b.getArrival() || a.getRides() < b.getRides());
                }
            }
        }
    }

    @Test
    public void arriveByFindsTheLatestPossibleDeparture() {
        Random random = new Random(5);
        for (int query = 0; query < 40; query++) {
            Place from = randomPlace(random);
            Place to = randomPlace(random);
            int time = 9 * 60 + random.nextInt(8 * 60);
            int latest = -1;
            for (Journey journey : planner.search(from, to, dates[0], 0, time)) {
                if (journey.getArrival() <= time) latest = Math.max(latest, journey.getDeparture());
            }
            int found = -1;
            for (Journey journey : planner.arriveBy(from, to, dates[0], time)) {
                if (journey.getRides() > 0) found = Math.max(found, journey.getDeparture());
            }
            // Journeys slower than walking or with a useless change are left out, the rest is the same.
            assertTrue(found <= latest);
            if (found >= 0) assertTrue(from.name + " to " + to.name, found >= latest - 60);
        }
    }

    @Test
    public void looksAtTheNextDayAtNight() {
        Place station = places.of(timetable.getStops().get(0));
        Place other = places.getAll().get(places.getAll().size() - 1);
        List<Journey> journeys = planner.leaveAt(station, other, dates[0], 23 * 60 + 55);
        assertFalse(journeys.isEmpty());
        assertEquals(dates[0].plusDays(1), journeys.get(journeys.size() - 1).date);
    }

    @Test
    public void isFastEnoughToUseWhileDraggingTheTime() {
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) planner.leaveAt(randomPlace(random), randomPlace(random), dates[0], 700);
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) planner.leaveAt(randomPlace(random), randomPlace(random), dates[0], 700);
        double milliseconds = (System.nanoTime() - start) / 50e6;
        System.out.println("journey search: " + milliseconds + " ms");
        assertTrue(milliseconds + " ms", milliseconds < 500);
    }

    // Small made-up timetables

    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

    private static BusStop stop(String id, double meters) {
        return new BusStop(id, "Stop " + id, new Geolocation(46.55 + meters / 111_320, 15.64));
    }

    private static RoutePattern pattern(String line, BusStop... stops) {
        List<Geolocation> path = new ArrayList<>();
        for (BusStop stop : stops) path.add(stop.geo);
        return new RoutePattern(new BusLine(line, line, "", Color.RED), Arrays.asList(stops), path);
    }

    private static Service daily() {
        return new Service(new boolean[]{true, true, true, true, true, true, true}, DAY.minusDays(9), DAY.plusDays(9));
    }

    private static Timetable timetable(List<BusStop> stops, RoutePattern... patterns) {
        List<BusLine> lines = new ArrayList<>();
        for (RoutePattern pattern : patterns) if (!lines.contains(pattern.line)) lines.add(pattern.line);
        return new Timetable(stops, lines, DAY.plusDays(9));
    }

    @Test
    public void walksBetweenStopsToChange() {
        BusStop a = stop("A", 0), b = stop("B", 3000), c = stop("C", 3150), d = stop("D", 9000);
        RoutePattern first = pattern("1", a, b);
        RoutePattern second = pattern("2", c, d);
        first.addTrip(daily(), new int[]{600, 610}, new int[]{600, 610});
        // 150 m are 3 minutes, and one more to change
        second.addTrip(daily(), new int[]{613, 630}, new int[]{613, 630});
        second.addTrip(daily(), new int[]{614, 631}, new int[]{614, 631});
        Timetable small = timetable(Arrays.asList(a, b, c, d), first, second);
        Places smallPlaces = new Places(small);

        List<Journey> found = new JourneyPlanner(small).leaveAt(smallPlaces.of(a), smallPlaces.of(d), DAY, 590);
        assertEquals(1, found.size());
        Journey journey = found.get(0);
        assertEquals(631, journey.getArrival());
        assertEquals(3, journey.getLegs().size());
        assertEquals(3, journey.getLegs().get(1).getMinutes());
        assertEquals(1, journey.getChanges());
    }

    @Test
    public void takesABusThatOvertakesAnother() {
        BusStop a = stop("A", 0), b = stop("B", 5000);
        RoutePattern pattern = pattern("1", a, b);
        pattern.addTrip(daily(), new int[]{600, 660}, new int[]{600, 660});
        pattern.addTrip(daily(), new int[]{605, 620}, new int[]{605, 620});
        Timetable small = timetable(Arrays.asList(a, b), pattern);
        Places smallPlaces = new Places(small);

        List<Journey> found = new JourneyPlanner(small).search(smallPlaces.of(a), smallPlaces.of(b), DAY, 590, 700);
        assertEquals(1, found.size());
        assertEquals(620, found.get(0).getArrival());
        assertEquals(605, found.get(0).getDeparture());
    }

    @Test
    public void suggestsWalkingInsteadOfASlowerBus() {
        BusStop a = stop("A", 0), b = stop("B", 600);
        RoutePattern pattern = pattern("1", a, b);
        pattern.addTrip(daily(), new int[]{600, 640}, new int[]{600, 640});
        Timetable small = timetable(Arrays.asList(a, b), pattern);
        Places smallPlaces = new Places(small);

        List<Journey> found = new JourneyPlanner(small).leaveAt(smallPlaces.of(a), smallPlaces.of(b), DAY, 590);
        assertEquals(1, found.size());
        assertEquals(0, found.get(0).getRides());
        assertEquals(11, found.get(0).getMinutes());
    }

    @Test
    public void bothSidesOfAStopAreOnePlace() {
        BusStop left = new BusStop("1", "Same", new Geolocation(46.55, 15.64));
        BusStop right = new BusStop("2", "Same", new Geolocation(46.5502, 15.64));
        BusStop far = new BusStop("3", "Same", new Geolocation(46.56, 15.64));
        Places named = new Places(new Timetable(Arrays.asList(left, right, far), new ArrayList<>(), DAY));
        assertSame(named.of(left), named.of(right));
        assertNotSame(named.of(left), named.of(far));
        assertEquals(2, named.getAll().size());
        assertTrue(named.of(new Geolocation(46.551, 15.64)).isPoint());
    }
}
