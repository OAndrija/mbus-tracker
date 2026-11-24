package com.mbus.app.utils;

import com.badlogic.gdx.graphics.Color;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Service;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StopSearchTest {

    private final BusStop stop = new BusStop("135", "Betnavska - Žolgarjeva", new Geolocation(46.55, 15.64));

    @Test
    public void ignoresAccentsAndCase() {
        assertTrue(StopSearch.matches(stop, "zolgar"));
        assertTrue(StopSearch.matches(stop, "ŽOLGAR"));
        assertTrue(StopSearch.matches(stop, "žolgar"));
    }

    @Test
    public void allWordsMustMatchInAnyOrder() {
        assertTrue(StopSearch.matches(stop, "zolg  betn"));
        assertFalse(StopSearch.matches(stop, "betn tabor"));
    }

    @Test
    public void matchesTheStartOfTheStopNumber() {
        assertTrue(StopSearch.matches(stop, "13"));
        assertFalse(StopSearch.matches(stop, "35"));
    }

    @Test
    public void matchesLinesThatStopThere() {
        BusLine line = new BusLine("1", "P10", "", Color.WHITE);
        BusStop end = new BusStop("2", "Konec", new Geolocation(46.56, 15.65));
        RoutePattern pattern = new RoutePattern(line, Arrays.asList(stop, end), Arrays.asList(stop.geo, end.geo));
        pattern.addTrip(new Service(), new int[]{60, 70}, new int[]{60, 70});

        assertTrue(StopSearch.matches(stop, "p10"));
        assertTrue(StopSearch.matches(stop, "P10 betnavska"));
        assertFalse(StopSearch.matches(stop, "P1"));
        assertFalse("buses end here, none depart", StopSearch.matches(end, "P10"));
    }

    @Test
    public void emptyQueryMatchesEverything() {
        assertTrue(StopSearch.matches(stop, "   "));
    }
}
