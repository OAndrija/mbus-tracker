package com.mbus.app.systems.data;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Service;
import com.mbus.app.model.StopCall;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.ZoomXY;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Checks the bundled timetable as well as the reader, so that an updated file is checked too. */
public class GtfsLoaderTest {

    private static Timetable timetable;

    @BeforeClass
    public static void load() throws IOException {
        timetable = GtfsLoader.load(new FileInputStream("data/marprom_gtfs.zip"));
    }

    /** The last full week the timetable covers without the holidays at its end. */
    private static LocalDate day(DayOfWeek dayOfWeek) {
        LocalDate date = timetable.getValidUntil().minusWeeks(4);
        while (date.getDayOfWeek() != dayOfWeek) date = date.minusDays(1);
        return date;
    }

    @Test
    public void busesRunOnEveryDayOfTheWeek() {
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            assertTrue(dayOfWeek + " has few trips", timetable.getTrips(day(dayOfWeek)).size() > 100);
        }
        assertTrue(timetable.getTrips(day(DayOfWeek.TUESDAY)).size() > timetable.getTrips(day(DayOfWeek.SUNDAY)).size());
    }

    @Test
    public void everyStopIsServed() {
        assertTrue(timetable.getStops().size() > 300);
        for (BusStop stop : timetable.getStops()) {
            boolean terminus = false;
            for (BusLine line : timetable.getLines()) {
                for (RoutePattern pattern : line.getPatterns()) {
                    terminus |= pattern.getStops().get(pattern.getStops().size() - 1) == stop;
                }
            }
            assertTrue(stop.toString(), terminus || !stop.getCalls().isEmpty());
        }
    }

    @Test
    public void linesHaveBothDirectionsAndAreSorted() {
        List<BusLine> lines = timetable.getLines();
        assertTrue(lines.size() >= 15);
        for (int i = 0; i < lines.size(); i++) {
            assertTrue(lines.get(i).name, lines.get(i).getPatterns().size() >= 2);
            if (i > 0) assertTrue(lines.get(i - 1).compareTo(lines.get(i)) < 0);
        }
    }

    @Test
    public void tripsAreInOrderAndCoverTheirStops() {
        for (BusLine line : timetable.getLines()) {
            for (RoutePattern pattern : line.getPatterns()) {
                assertFalse(pattern.getTrips().isEmpty());
                for (Trip trip : pattern.getTrips()) {
                    for (int i = 1; i < trip.getStopCount(); i++) {
                        assertTrue(trip.getArrival(i) >= trip.getDeparture(i - 1) - 1);
                        assertTrue(trip.getArrival(i) >= trip.getArrival(i - 1));
                    }
                }
            }
        }
    }

    @Test
    public void departuresAreUpcomingSortedAndOfTheDay() {
        LocalDate sunday = day(DayOfWeek.SUNDAY);
        for (BusStop stop : timetable.getStops()) {
            int previous = 8 * 60;
            for (StopCall call : timetable.getDepartures(stop, sunday, 8 * 60, 10)) {
                assertTrue(call.getTime() >= previous);
                assertTrue(call.trip.service.runsOn(sunday));
                previous = call.getTime();
            }
        }
    }

    @Test
    public void allStopsAreOnTheMap() {
        ZoomXY center = MapTestSupport.beginTile();
        for (BusStop stop : timetable.getStops()) {
            double[] world = MapRasterTiles.project(stop.geo.lat, stop.geo.lng, MapRasterTiles.TILE_SIZE);
            double x = world[0] * (1 << Constants.ZOOM) - center.x * (double) MapRasterTiles.TILE_SIZE;
            double y = world[1] * (1 << Constants.ZOOM) - center.y * (double) MapRasterTiles.TILE_SIZE;
            assertTrue(stop.toString(), x > 0 && x < Constants.MAP_WIDTH && y > 0 && y < Constants.MAP_HEIGHT);
        }
    }

    @Test
    public void servicesFollowTheirDaysAndExceptions() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        Service weekdays = new Service(new boolean[]{true, true, true, true, true, false, false},
            monday, monday.plusDays(13));
        weekdays.addException(monday.plusDays(1), false);
        weekdays.addException(monday.plusDays(5), true);

        assertTrue(weekdays.runsOn(monday));
        assertFalse(weekdays.runsOn(monday.plusDays(1)));
        assertTrue(weekdays.runsOn(monday.plusDays(5)));
        assertFalse(weekdays.runsOn(monday.plusDays(6)));
        assertFalse(weekdays.runsOn(monday.minusDays(7)));
        assertFalse(weekdays.runsOn(monday.plusDays(14)));
    }

    @Test
    public void readsQuotedValues() {
        assertEquals(Arrays.asList("a", "b, \"c\"", "", "d"), GtfsLoader.split("a,\"b, \"\"c\"\"\",,d"));
    }

    @Test
    public void sharesPatternsBetweenServices() {
        Set<List<Object>> seen = new HashSet<>();
        for (BusLine line : timetable.getLines()) {
            for (RoutePattern pattern : line.getPatterns()) {
                StringBuilder road = new StringBuilder();
                pattern.getPath().forEach(p -> road.append(p.lat).append(p.lng));
                assertTrue(line.name, seen.add(Arrays.asList(line.id, pattern.getStops(), road.toString())));
            }
        }
    }

    @Test
    public void reportsAnIncompleteFile() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("stops.txt"));
            zip.write("stop_id,stop_name,stop_lat,stop_lon\n1,A,46.5,15.6\n".getBytes(StandardCharsets.UTF_8));
        }
        try {
            GtfsLoader.load(new ByteArrayInputStream(bytes.toByteArray()));
            fail("expected an error");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("routes.txt"));
        }
    }
}
