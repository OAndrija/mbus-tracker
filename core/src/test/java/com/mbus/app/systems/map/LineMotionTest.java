package com.mbus.app.systems.map;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.data.ScheduleLoader;
import com.mbus.app.utils.BusLineStopRelationshipBuilder;
import com.mbus.app.utils.ZoomXY;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Follows one noon trip of every line through the bundled Marprom data, frame by frame. */
public class LineMotionTest {

    private static final double FRAME = 1 / 60.0 / 60.0; // minutes

    private static HeadlessApplication app;
    private static final double METERS_PER_PIXEL = 1.6425;
    private static final List<BusLine> lines = new ArrayList<>();
    private static final List<BusSchedule> trips = new ArrayList<>();
    private static final List<LineMotion> motions = new ArrayList<>();

    @BeforeClass
    public static void loadData() {
        app = new HeadlessApplication(new ApplicationAdapter() {});
        Gdx.app.setLogLevel(Application.LOG_ERROR);

        BusLineStopRelationshipBuilder.RelationshipResult result =
            BusLineStopRelationshipBuilder.buildRelationships(
                GeoJSONLoader.loadBusLinesFromFile("data/int_mob_marprom_linije.json"),
                GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json"),
                50);
        List<BusLine> all = ScheduleLoader.assignSchedulesToLines(
            result.lines, ScheduleLoader.generateExampleSchedules(result.lines));

        ZoomXY center = MapRasterTiles.getTileNumber(46.557314, 15.637771, 15);
        WorldGeometry geometry = new WorldGeometry(
            result.stops, all, new ZoomXY(15, center.x - 14, center.y - 14));

        for (BusLine line : all) {
            for (BusSchedule trip : line.getSchedules()) {
                if (trip.departureTime < 12 * 60) continue;
                WorldGeometry.LinePath path = geometry.getLine(line);
                lines.add(line);
                trips.add(trip);
                motions.add(new LineMotion(line, new RouteTrack(path.x, path.y), geometry));
                break;
            }
        }
    }

    @AfterClass
    public static void shutDown() {
        app.exit();
    }

    @Test
    public void busesNeverMoveBackwardsOrChangeSpeedAbruptly() {
        assertFalse(trips.isEmpty());
        for (int i = 0; i < trips.size(); i++) {
            BusSchedule trip = trips.get(i);
            LineMotion motion = motions.get(i);
            double average = motion.getTrack().length()
                / (motion.endOf(trip) - trip.getStopTimes().get(0).arrivalTime) * FRAME;

            double previous = -1;
            double previousStep = 0;
            for (double t = trip.departureTime; motion.isRunning(trip, t); t += FRAME) {
                double along = motion.distanceAt(trip, t);
                if (previous >= 0) {
                    double step = along - previous;
                    assertTrue("line " + trip.lineId + " backwards at " + t, step > -1e-6);
                    assertTrue("line " + trip.lineId + " jolts at " + t,
                        Math.abs(step - previousStep) < average * 0.05);
                    previousStep = step;
                }
                previous = along;
            }
        }
    }

    @Test
    public void busesKeepToCitySpeeds() {
        for (int i = 0; i < trips.size(); i++) {
            BusSchedule trip = trips.get(i);
            LineMotion motion = motions.get(i);
            for (double t = trip.departureTime; motion.isRunning(trip, t + 1 / 60.0); t += 1 / 60.0) {
                double meters = (motion.distanceAt(trip, t + 1 / 60.0) - motion.distanceAt(trip, t)) * METERS_PER_PIXEL;
                assertTrue("line " + trip.lineId + " drives " + meters * 3.6 + " km/h at " + t, meters * 3.6 < 70);
            }
        }
    }

    @Test
    public void noLineHasMoreThanTwoBuses() {
        for (int i = 0; i < lines.size(); i++) {
            for (double t = 0; t < 24 * 60; t++) {
                int running = 0;
                for (BusSchedule trip : lines.get(i).getSchedules()) {
                    if (trip.dayType == 0 && motions.get(i).isRunning(trip, t + 0.5)) running++;
                }
                assertTrue("line " + lines.get(i).lineId + " has " + running + " at " + t, running <= 2);
            }
        }
    }

    @Test
    public void busesEndAtTheEndOfTheRoute() {
        for (int i = 0; i < trips.size(); i++) {
            LineMotion motion = motions.get(i);
            assertEquals("line " + trips.get(i).lineId, motion.getTrack().length(),
                motion.distanceAt(trips.get(i), motion.endOf(trips.get(i))), 8.0);
        }
    }

    @Test
    public void busesFadeInAndOut() {
        for (int i = 0; i < trips.size(); i++) {
            BusSchedule trip = trips.get(i);
            LineMotion motion = motions.get(i);
            assertEquals(0f, motion.alphaAt(trip, trip.getStopTimes().get(0).arrivalTime), 1e-4f);
            assertEquals(1f, motion.alphaAt(trip, trip.getStopTimes().get(0).arrivalTime + 1), 1e-4f);
            assertEquals(0f, motion.alphaAt(trip, motion.endOf(trip)), 1e-4f);
        }
    }

    @Test
    public void easingCoversTheWholeSegmentAndStartsAndStopsGently() {
        assertEquals(0, LineMotion.ease(0, 2), 0);
        assertEquals(0.5, LineMotion.ease(1, 2), 1e-9);
        assertEquals(1, LineMotion.ease(2, 2), 0);
        assertTrue(LineMotion.ease(0.01, 2) < 0.001);
        assertTrue(LineMotion.ease(1.99, 2) > 0.999);
    }

    @Test
    public void trackFollowsTheRoute() {
        RouteTrack track = new RouteTrack(new float[]{0, 10, 10}, new float[]{0, 0, 20});
        assertEquals(30, track.length(), 1e-6);
        assertEquals(new Vector2(5, 0), track.pointAt(5, new Vector2()));
        assertEquals(new Vector2(10, 10), track.pointAt(20, new Vector2()));
        assertEquals(new Vector2(10, 20), track.pointAt(99, new Vector2()));
        assertEquals(20, track.nearestTo(13, 10), 1e-6);
    }

    @Test
    public void busesTurnTheShortWay() {
        assertEquals(20f, BusRenderer.turn(350, 10), 1e-4f);
        assertEquals(-20f, BusRenderer.turn(10, 350), 1e-4f);
        assertEquals(-20f, BusRenderer.turn(370, -10), 1e-4f);
        assertEquals(20f, BusRenderer.turn(-10, 370), 1e-4f);
    }
}
