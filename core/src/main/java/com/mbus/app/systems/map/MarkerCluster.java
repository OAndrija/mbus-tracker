package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusStop;

import java.util.ArrayList;
import java.util.List;

public class MarkerCluster {

    public Vector2 position;
    public List<BusStop> stops;
    public boolean isCluster;

    public MarkerCluster(Vector2 position, BusStop stop) {
        this.position = position;
        this.stops = new ArrayList<BusStop>();
        this.stops.add(stop);
        this.isCluster = false;
    }

    public MarkerCluster(Vector2 position, List<BusStop> stops) {
        this.position = position;
        this.stops = new ArrayList<BusStop>(stops);
        this.isCluster = stops.size() > 1;
    }

    public int getCount() {
        return stops.size();
    }

    public Vector2 getPosition() {
        return position;
    }

    public BusStop getSingleStop() {
        return isCluster ? null : stops.get(0);
    }

    public List<BusStop> getStops() {
        return stops;
    }
}
