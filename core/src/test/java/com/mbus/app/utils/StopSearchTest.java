package com.mbus.app.utils;

import com.mbus.app.model.BusStop;
import org.junit.Test;

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
    public void emptyQueryMatchesEverything() {
        assertTrue(StopSearch.matches(stop, "   "));
    }
}
