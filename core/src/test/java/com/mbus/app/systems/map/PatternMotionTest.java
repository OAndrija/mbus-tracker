package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.systems.data.GtfsLoader;
import com.mbus.app.systems.data.MapTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Follows the first trip of every pattern of the bundled timetable, frame by frame. */
public class PatternMotionTest {

    private static final double METERS_PER_PIXEL = 1.6425;
    private static final double FRAME = 1 / 60.0 / 60.0; // minutes

    private static final List<Trip> trips = new ArrayList<>();
    private static final List<PatternMotion> motions = new ArrayList<>();
    private static WorldGeometry geometry;

    @BeforeClass
    public static void load() throws IOException {
        Timetable timetable = GtfsLoader.load(new FileInputStream("data/marprom_gtfs.zip"));
        geometry = new WorldGeometry(timetable, MapTestSupport.beginTile());
        for (BusLine line : timetable.getLines()) {
            for (RoutePattern pattern : line.getPatterns()) {
                WorldGeometry.Path path = geometry.getPattern(pattern);
                trips.add(pattern.getTrips().get(0));
                motions.add(new PatternMotion(pattern, new RouteTrack(path.x, path.y), geometry));
            }
        }
    }

    @Test
    public void stopsAreOnTheRouteInOrder() {
        Vector2 point = new Vector2();
        for (int i = 0; i < trips.size(); i++) {
            RoutePattern pattern = trips.get(i).pattern;
            double previous = 0;
            for (int s = 0; s < pattern.getStops().size(); s++) {
                double along = motions.get(i).getStopDistance(s);
                assertTrue(along >= previous);
                motions.get(i).getTrack().pointAt(along, point);
                double meters = point.dst(geometry.getStop(pattern.getStops().get(s))) * METERS_PER_PIXEL;
                assertTrue(pattern.line + " " + pattern.getStops().get(s) + " is off by " + meters, meters < 100);
                previous = along;
            }
        }
    }

    @Test
    public void busesNeverMoveBackwardsOrJump() {
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            double previous = motions.get(i).distanceAt(trip, trip.getStart());
            for (double t = trip.getStart(); motions.get(i).isRunning(trip, t); t += FRAME) {
                double along = motions.get(i).distanceAt(trip, t);
                assertTrue(trip.pattern.line + " backwards", along >= previous - 1e-6);
                double kmh = (along - previous) * METERS_PER_PIXEL / (FRAME * 60) * 3.6;
                assertTrue(trip.pattern.line + " drives " + kmh + " km/h at " + t + " to " + trip.pattern.getDestination(), kmh < 110);
                previous = along;
            }
        }
    }

    @Test
    public void busesComeInTheMinuteOfTheTimetable() {
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            PatternMotion motion = motions.get(i);
            for (int s = 1; s < trip.getStopCount(); s++) {
                double stop = motion.getStopDistance(s);
                String where = trip.pattern.line + " at " + trip.pattern.getStops().get(s);
                assertTrue(where + " early", motion.distanceAt(trip, trip.getArrival(s) - 0.001) <= stop + 1e-6);
                assertTrue(where + " late", motion.distanceAt(trip, trip.getArrival(s) + 1) >= stop - 1e-6);
            }
        }
    }

    @Test
    public void busesFadeInAndOut() {
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            PatternMotion motion = motions.get(i);
            assertEquals(0f, motion.alphaAt(trip, motion.startOf(trip)), 1e-4f);
            assertEquals(1f, motion.alphaAt(trip, motion.startOf(trip) + 0.5), 1e-4f);
            assertEquals(0f, motion.alphaAt(trip, motion.endOf(trip)), 1e-4f);
        }
    }

    @Test
    public void easingCoversTheWholeSegmentAndStartsAndStopsGently() {
        assertEquals(0, PatternMotion.ease(0, 2), 0);
        assertEquals(0.5, PatternMotion.ease(1, 2), 1e-9);
        assertEquals(1, PatternMotion.ease(2, 2), 0);
        assertTrue(PatternMotion.ease(0.01, 2) < 0.001);
        assertTrue(PatternMotion.ease(1.99, 2) > 0.999);
    }

    @Test
    public void trackFollowsTheRoute() {
        RouteTrack track = new RouteTrack(new float[]{0, 10, 10}, new float[]{0, 0, 20});
        assertEquals(30, track.length(), 1e-6);
        assertEquals(new Vector2(5, 0), track.pointAt(5, new Vector2()));
        assertEquals(new Vector2(10, 10), track.pointAt(20, new Vector2()));
        assertEquals(new Vector2(10, 20), track.pointAt(99, new Vector2()));
    }

    @Test
    public void placesStopsOfARouteThatComesBackOnTheSameRoad() {
        RouteTrack track = new RouteTrack(new float[]{0, 100, 0}, new float[]{0, 0, 0});
        assertArrayEquals(new double[]{0, 50, 100, 150, 200},
            track.placeInOrder(new float[]{0, 50, 100, 50, 0}, new float[]{0, 1, 0, -1, 0}), 1e-6);
    }

    @Test
    public void busesTurnTheShortWay() {
        assertEquals(20f, BusRenderer.turn(350, 10), 1e-4f);
        assertEquals(-20f, BusRenderer.turn(10, 350), 1e-4f);
    }
}
