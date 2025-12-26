package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusStop {

    public final int idAvpost;
    public final String idMarprom;
    public final String name;

    // Original D96/TM coordinates (EPSG:3794)
    public final double x3794;
    public final double y3794;

    // Converted WGS84 coordinates
    public final Geolocation geo;

    // NEW: Line IDs that pass through this stop
    private final List<Integer> lineIds;

    public BusStop(int idAvpost,
                   String idMarprom,
                   String name,
                   double x3794,
                   double y3794,
                   Geolocation geo,
                   List<Integer> lineIds) {

        this.idAvpost = idAvpost;
        this.idMarprom = idMarprom;
        this.name = name;

        this.x3794 = x3794;
        this.y3794 = y3794;

        this.geo = geo;

        this.lineIds = lineIds != null
            ? Collections.unmodifiableList(new ArrayList<Integer>(lineIds))
            : Collections.unmodifiableList(new ArrayList<Integer>());
    }

    // Convenience constructor without lineIds for backward compatibility
    public BusStop(int idAvpost,
                   String idMarprom,
                   String name,
                   double x3794,
                   double y3794,
                   Geolocation geo) {
        this(idAvpost, idMarprom, name, x3794, y3794, geo, null);
    }

    /**
     * Get all line IDs that pass through this stop
     */
    public List<Integer> getLineIds() {
        return lineIds;
    }

    /**
     * Get the number of lines that pass through this stop
     */
    public int getLineCount() {
        return lineIds.size();
    }

    /**
     * Check if a specific line passes through this stop
     */
    public boolean hasLine(int lineId) {
        return lineIds.contains(lineId);
    }

    /**
     * Get a formatted string of all line IDs (e.g., "1, 3, 6")
     */
    public String getLineIdsString() {
        if (lineIds.isEmpty()) {
            return "No lines";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineIds.size(); i++) {
            sb.append(lineIds.get(i));
            if (i < lineIds.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "BusStop{" +
            "idAvpost=" + idAvpost +
            ", idMarprom='" + idMarprom + '\'' +
            ", name='" + name + '\'' +
            ", x3794=" + x3794 +
            ", y3794=" + y3794 +
            ", geo=(" + geo.lat + ", " + geo.lng + ")" +
            ", lines=" + getLineIdsString() +
            '}';
    }
}
