package com.mbus.app.systems.streets;

import com.mbus.app.model.BusStop;
import com.mbus.app.model.Timetable;
import com.mbus.app.systems.data.GtfsLoader;
import com.mbus.app.utils.Geolocation;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StreetsTest {

    private static final double FAR = 100_000;

    private static Streets streets;
    private static Timetable timetable;

    @BeforeClass
    public static void load() throws IOException {
        long start = System.nanoTime();
        streets = Streets.load(new FileInputStream("data/maribor_streets.txt.gz"));
        System.out.println("streets: " + (System.nanoTime() - start) / 1_000_000 + " ms");
        timetable = GtfsLoader.load(new FileInputStream("data/marprom_gtfs.zip"));
    }

    private static double meters(Geolocation a, Geolocation b) {
        return 111_320 * Math.hypot(a.lat - b.lat, (a.lng - b.lng) * Math.cos(Math.toRadians(a.lat)));
    }

    private static double length(List<Geolocation> path) {
        double length = 0;
        for (int i = 1; i < path.size(); i++) length += meters(path.get(i - 1), path.get(i));
        return length;
    }

    private static Geolocation randomPoint(Random random) {
        return new Geolocation(46.53 + random.nextDouble() * 0.05, 15.60 + random.nextDouble() * 0.08);
    }

    // The way on foot

    @Test
    public void everyStopIsNextToAStreet() {
        int far = 0;
        for (BusStop stop : timetable.getStops()) {
            WalkNetwork.Access access = streets.network.getAccess(stop.geo, 250);
            if (access == null || access.distance > 40) far++;
        }
        assertTrue(far + " stops are far from streets", far <= timetable.getStops().size() / 100);
    }

    @Test
    public void accessIsTheClosestPointOfAnyStreet() {
        // Points of the network itself have to be found, and nothing can be closer than they are.
        Random random = new Random(2);
        for (int i = 0; i < 300; i++) {
            Geolocation point = randomPoint(random);
            WalkNetwork.Access access = streets.network.getAccess(point, 2000);
            assertNotNull(access);
            WalkNetwork.Access limited = streets.network.getAccess(point, access.distance + 0.01);
            assertEquals(access.distance, limited.distance, 1e-6);
            assertNull(streets.network.getAccess(point, access.distance - 0.01));
        }
    }

    @Test
    public void waysAreSymmetricAndNeverShorterThanTheStraightLine() {
        Random random = new Random(4);
        for (int i = 0; i < 60; i++) {
            Geolocation a = randomPoint(random), b = randomPoint(random);
            WalkNetwork.Access start = streets.network.getAccess(a, 2000), end = streets.network.getAccess(b, 2000);
            double there = streets.network.waysFrom(start, FAR).to(end);
            assertEquals(there, streets.network.waysFrom(end, FAR).to(start), 0.01);
            assertEquals(there, streets.network.wayBetween(start, end, FAR).to(end), 0.01);
            assertTrue(there + start.distance + end.distance >= meters(a, b) * 0.998);
        }
    }

    @Test
    public void pathIsAsLongAsTheWayAndHasNoGaps() {
        Random random = new Random(6);
        for (int i = 0; i < 200; i++) {
            WalkNetwork.Access start = streets.network.getAccess(randomPoint(random), 2000);
            WalkNetwork.Access end = streets.network.getAccess(randomPoint(random), 2000);
            WalkNetwork.Ways ways = streets.network.wayBetween(start, end, FAR);
            assertEquals(ways.to(end), length(ways.pathTo(end)), ways.to(end) * 0.003 + 0.1);
        }
    }

    @Test
    public void waysOnTheSameStreet() {
        Random random = new Random(8);
        for (int i = 0; i < 200; i++) {
            Geolocation a = randomPoint(random);
            Geolocation b = new Geolocation(a.lat + (random.nextDouble() - 0.5) * 2e-4, a.lng + (random.nextDouble() - 0.5) * 2e-4);
            WalkNetwork.Access start = streets.network.getAccess(a, 2000), end = streets.network.getAccess(b, 2000);
            WalkNetwork.Ways ways = streets.network.wayBetween(start, end, FAR);
            if (start.edge == end.edge) assertTrue(ways.to(end) <= Math.abs(start.along - end.along) + 1e-6);
            assertEquals(ways.to(end), length(ways.pathTo(end)), ways.to(end) * 0.003 + 0.1);
        }
    }

    @Test
    public void waysEndAtTheLimit() {
        WalkNetwork.Access start = streets.network.getAccess(new Geolocation(46.5577, 15.6459), 250);
        WalkNetwork.Access end = streets.network.getAccess(new Geolocation(46.5400, 15.6459), 250);
        double meters = streets.network.waysFrom(start, FAR).to(end);
        assertTrue(Double.isInfinite(streets.network.waysFrom(start, meters - 300).to(end)));
        assertTrue(streets.network.waysFrom(start, meters - 300).pathTo(end).isEmpty());
    }

    @Test
    public void theRiverCanOnlyBeCrossedOnBridges() {
        // Both banks upstream of the bridges, where it is 300 m across
        Geolocation north = new Geolocation(46.5640, 15.6120), south = new Geolocation(46.5580, 15.6120);
        WalkNetwork.Access start = streets.network.getAccess(north, 400), end = streets.network.getAccess(south, 400);
        double way = streets.network.waysFrom(start, FAR).to(end);
        System.out.println("across the river: " + meters(north, south) + " m, " + way + " m on foot");
        assertTrue(way > 1.5 * meters(north, south));
    }

    @Test(expected = IOException.class)
    public void damagedFilesAreNoticed() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("O|46490000 15550000\nN|1 2\nE|0 7\n".getBytes(StandardCharsets.UTF_8));
        }
        Streets.load(new ByteArrayInputStream(bytes.toByteArray()));
    }

    // Addresses

    private static Addresses.Match first(String query) {
        List<Addresses.Match> found = streets.addresses.search(query, 5);
        assertFalse(query, found.isEmpty());
        return found.get(0);
    }

    @Test
    public void findsAnAddress() {
        for (String query : new String[]{"Gosposvetska cesta 10", "gosposvetska 10", "gosposvetska c 10",
            "GOSPOSVETSKA 10 maribor", "cesta gosposvetska 10"}) {
            Addresses.Match match = first(query);
            assertEquals(query, "Gosposvetska cesta 10", match.address.getName());
            assertEquals("", match.insteadOf);
        }
    }

    @Test
    public void accentsDoNotMatter() {
        assertEquals("Koroška cesta 46", first("koroska cesta 46").address.getName());
        assertEquals("Koroška cesta 46", first("Koroška 46").address.getName());
    }

    @Test
    public void numbersInNamesOfStreetsAreNotHouseNumbers() {
        assertEquals("Ulica 10. oktobra", first("ulica 10. oktobra").address.getName());
        assertEquals("Cesta 4. julija", first("cesta 4 julija").address.getName());
        assertTrue(first("cesta 4. julija 10").address.getName().startsWith("Cesta 4. julija 1"));
    }

    @Test
    public void unknownNumbersAreReplacedByTheClosest() {
        Addresses.Match match = first("gosposvetska cesta 1999");
        assertEquals("1999", match.insteadOf);
        assertEquals("Gosposvetska cesta", match.address.street);
        assertFalse(match.address.number.isEmpty());
        int highest = Integer.parseInt(match.address.number.replaceAll("\\D.*", ""));
        assertTrue(highest > 80);
    }

    @Test
    public void streetsWithoutNumbersAreFound() throws IOException {
        Streets small = Streets.load(gzip("O|46490000 15550000\nN|0 0\nN|1000 0\nE|0 1\n"
            + "A|Glavni trg|4|Maribor|10 10\nA|Glavni trg|6a|Maribor|20 10\nA|Glavni trg|7|Maribor|30 10\nS|Nova ulica|500 10\n"));
        Addresses.Match match = small.addresses.search("nova ulica 12", 5).get(0);
        assertEquals("Nova ulica", match.address.getName());
        assertEquals("12", match.insteadOf);
        assertEquals("7", small.addresses.search("glavni trg 8", 5).get(0).address.number);
        assertEquals("4", small.addresses.search("glavni trg 2", 5).get(0).address.number);
        assertEquals("6a", small.addresses.search("glavni trg 6 A", 5).get(0).address.number);
        assertEquals("6a", small.addresses.search("glavni trg 6", 5).get(0).address.number);
        assertEquals("6", small.addresses.search("glavni trg 6", 5).get(0).insteadOf);
    }

    private static ByteArrayInputStream gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    @Test
    public void everyAddressIsFoundByItsName() throws IOException {
        Random random = new Random(1);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new GZIPInputStream(new FileInputStream("data/maribor_streets.txt.gz")), StandardCharsets.UTF_8))) {
            int checked = 0;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.startsWith("A|") || random.nextInt(40) > 0) continue;
                String[] parts = line.split("\\|", -1);
                if (!parts[2].matches("\\d+[A-Za-z]?")) continue;
                boolean found = false;
                for (Addresses.Match match : streets.addresses.search(parts[1] + " " + parts[2], 1000)) {
                    found |= match.insteadOf.isEmpty() && match.address.street.equalsIgnoreCase(parts[1])
                        && match.address.number.replace(" ", "").equalsIgnoreCase(parts[2]);
                }
                assertTrue(line, found);
                checked++;
            }
            assertTrue(checked > 400);
        }
    }

    @Test
    public void exactNumbersComeFirst() {
        assertEquals("Ljubljanska ulica 5", first("ljubljanska 5").address.getName());
    }

    @Test
    public void aTownHasEveryStreetOnce() {
        int inMaribor = 0;
        for (Addresses.Match match : streets.addresses.search("titova cesta", 20)) {
            if (match.address.city.equals("Maribor")) inMaribor++;
        }
        assertEquals(1, inMaribor);
        assertEquals("the usual spelling", "Glavni trg", first("glavni trg").address.getName());
    }

    @Test
    public void nothingIsFoundWithoutAName() {
        for (String query : new String[]{"", " ", "1", "10a", "g", "č", "xyzxyz", ".,-", "10 gosposvetska"}) {
            assertTrue(query, streets.addresses.search(query, 5).isEmpty());
        }
    }

    @Test
    public void findsTheAddressNextToAPoint() {
        Addresses.Address address = first("gosposvetska 10").address;
        assertEquals(address, streets.addresses.findNear(address.geo, 60));
        assertNull(streets.addresses.findNear(new Geolocation(46.5640, 15.6100 - 1), 60));
    }
}
