package com.mbus.app.systems.data;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.BusLineStopRelationshipBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Runs against the bundled Marprom data. */
public class ScheduleLoaderTest {

    private static HeadlessApplication app;
    private static List<BusStop> stops;
    private static List<BusLine> lines;

    @BeforeClass
    public static void loadData() {
        app = new HeadlessApplication(new ApplicationAdapter() {});
        Gdx.app.setLogLevel(Application.LOG_ERROR);

        BusLineStopRelationshipBuilder.RelationshipResult result =
            BusLineStopRelationshipBuilder.buildRelationships(
                GeoJSONLoader.loadBusLinesFromFile("data/int_mob_marprom_linije.json"),
                GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json"),
                50);
        stops = result.stops;
        lines = ScheduleLoader.assignSchedulesToLines(
            result.lines, ScheduleLoader.generateExampleSchedules(result.lines));
    }

    @AfterClass
    public static void shutDown() {
        app.exit();
    }

    @Test
    public void busesRunOnEveryDayType() {
        for (int dayType = 0; dayType <= 2; dayType++) {
            boolean running = false;
            for (BusLine line : lines) {
                for (BusSchedule trip : line.getSchedules()) {
                    List<BusSchedule.StopTime> times = trip.getStopTimes();
                    running |= trip.dayType == dayType && trip.departureTime <= 12 * 60
                        && times.get(times.size() - 1).arrivalTime >= 12 * 60;
                }
            }
            assertTrue("no buses at noon on day type " + dayType, running);
        }
    }

    @Test
    public void everyLineOfAStopArrivesThere() {
        assertFalse(stops.isEmpty());
        for (BusStop stop : stops) {
            for (int dayType = 0; dayType <= 2; dayType++) {
                Set<Integer> arriving = new HashSet<>();
                for (BusStop.StopArrival arrival :
                    stop.getUpcomingArrivals(lines, 0, dayType, Integer.MAX_VALUE)) {
                    arriving.add(arrival.line.lineId);
                }
                assertEquals(stop.name, new HashSet<>(stop.getLineIds()), arriving);
            }
        }
    }

    @Test
    public void passingTimesStayWithinTheTripAndFollowTheStopOrder() {
        for (BusLine line : lines) {
            for (BusSchedule schedule : line.getSchedules()) {
                List<BusSchedule.StopTime> timing = schedule.getStopTimes();
                int last = timing.get(timing.size() - 1).arrivalTime;
                int previous = schedule.departureTime;

                for (BusStop stop : line.getStops()) {
                    int arrival = schedule.getArrivalTimeAtStop(stop.idAvpost);
                    assertTrue("line " + line.lineId + " at " + stop.name, arrival >= previous && arrival <= last);
                    previous = arrival;
                }
            }
        }
    }

    @Test
    public void scheduleIdsAreUnique() {
        Set<Integer> ids = new HashSet<>();
        for (BusLine line : lines) {
            for (BusSchedule schedule : line.getSchedules()) {
                assertTrue("duplicate id " + schedule.scheduleId, ids.add(schedule.scheduleId));
            }
        }
    }
}
