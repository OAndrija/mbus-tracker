package com.mbus.app.model;

public class BusStop {

    public final int idAvpost;
    public final String idMarprom;
    public final String name;

    // Original D96/TM coordinates (EPSG:3794)
    public final double x3794;
    public final double y3794;

    // Converted WGS84 coordinates

    public final Geolocation geo;

    public BusStop(int idAvpost,
                   String idMarprom,
                   String name,
                   double x3794,
                   double y3794,
                   Geolocation geo) {

        this.idAvpost = idAvpost;
        this.idMarprom = idMarprom;
        this.name = name;

        this.x3794 = x3794;
        this.y3794 = y3794;

        this.geo = geo;
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
            '}';
    }
}
