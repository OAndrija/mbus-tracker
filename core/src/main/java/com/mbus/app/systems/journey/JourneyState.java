package com.mbus.app.systems.journey;

import java.util.Collections;
import java.util.List;

/** What is being asked and what was found. Outlives the widgets, which are made anew for other colors. */
public class JourneyState {

    public Place from, to;
    public boolean arriveBy = false;
    public boolean searching = false;
    public List<Journey> results = Collections.emptyList();
    /** The journey whose details are shown, or null. */
    public Journey selected;

    public boolean isComplete() {
        return from != null && to != null;
    }

    public Place get(boolean start) {
        return start ? from : to;
    }

    public void set(boolean start, Place place) {
        if (start) from = place;
        else to = place;
        results = Collections.emptyList();
        selected = null;
    }

    public void swap() {
        Place place = from;
        from = to;
        to = place;
        results = Collections.emptyList();
        selected = null;
    }
}
