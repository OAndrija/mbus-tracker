package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusLine {

    public final int lineId;
    public final int variantId;
    public final int direction;  // -1 or 1
    public final double length;
    public final String name;
    public final String note;
    public final String providerName;
    public final String providerLink;

    // Path as a list of WGS84 coordinates
    private final List<Geolocation> path;

    // Optional: original D96/TM coordinates if needed for debugging
    private final List<double[]> originalCoordinates;

    public BusLine(int lineId,
                   int variantId,
                   int direction,
                   double length,
                   String name,
                   String note,
                   String providerName,
                   String providerLink,
                   List<Geolocation> path,
                   List<double[]> originalCoordinates) {

        this.lineId = lineId;
        this.variantId = variantId;
        this.direction = direction;
        this.length = length;
        this.name = name;
        this.note = note;
        this.providerName = providerName;
        this.providerLink = providerLink;
        this.path = Collections.unmodifiableList(new ArrayList<Geolocation>(path));
        this.originalCoordinates = originalCoordinates != null
            ? Collections.unmodifiableList(new ArrayList<double[]>(originalCoordinates))
            : null;
    }

    /**
     * Get the path as an unmodifiable list of WGS84 coordinates
     */
    public List<Geolocation> getPath() {
        return path;
    }

    /**
     * Get the original EPSG:3794 coordinates (for debugging)
     */
    public List<double[]> getOriginalCoordinates() {
        return originalCoordinates;
    }

    /**
     * Get the number of points in this line
     */
    public int getPointCount() {
        return path.size();
    }

    @Override
    public String toString() {
        return "BusLine{" +
            "lineId=" + lineId +
            ", variantId=" + variantId +
            ", direction=" + direction +
            ", name='" + name + '\'' +
            ", points=" + path.size() +
            '}';
    }
}
