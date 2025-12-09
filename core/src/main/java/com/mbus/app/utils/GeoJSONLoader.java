package com.mbus.app.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Logger;
import com.mbus.app.model.BusStop;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeoJSONLoader {

    private static final Logger log = new Logger("GeoJSONLoader", Logger.INFO);

    private GeoJSONLoader() {
        // utility class
    }

    /**
     * Load Marprom bus stops from a GeoJSON file and convert coordinates
     * from EPSG:3794 (D96/TM) to WGS84 (lat,lon) using GeoConverter3794.
     *
     * @param path path to GeoJSON file (e.g. "data/int_mob_marprom_postaje.json")
     * @return list of BusStop objects
     */
    public static List<BusStop> loadBusStopsFromFile(String path) {

        // 1) Find file via LibGDX (works on all platforms)
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            file = Gdx.files.local(path);
        }

        if (!file.exists()) {
            log.error("GeoJSON file not found: " + path);
            return Collections.emptyList();
        }

        log.info("Loading bus stops from: " + file.path());

        // 2) Read file as String
        String jsonText = file.readString("UTF-8");

        // 3) Parse JSON
        JSONObject root = new JSONObject(jsonText);

        JSONArray features = root.getJSONArray("features");
        List<BusStop> stops = new ArrayList<BusStop>(features.length());

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);

            // geometry
            JSONObject geometry = feature.getJSONObject("geometry");
            String geomType = geometry.getString("type");

            if (!"Point".equalsIgnoreCase(geomType)) {
                // just skip non-point geometries if any
                continue;
            }

            JSONArray coords = geometry.getJSONArray("coordinates");
            // EPSG:3794 is in meters, x = Easting, y = Northing
            double x = coords.getDouble(0);
            double y = coords.getDouble(1);

            // 4) Convert to WGS84
            double[] latlon = GeoConverter3794.toWGS84(x, y + 5000000);
            double lat = latlon[0];
            double lon = latlon[1];

            Geolocation geo = new Geolocation(lat, lon);

            // 5) Properties
            JSONObject props = feature.getJSONObject("properties");

            int idAvpost = props.optInt("id_avpost", -1);
            String idMarprom = props.optString("id_marprom", "");
            String name = props.optString("ime_postaj", "");

            BusStop stop = new BusStop(
                idAvpost,
                idMarprom,
                name,
                x,
                y,
                geo
            );

            stops.add(stop);
        }

        log.info("Loaded " + stops.size() + " bus stops from " + path);
        return stops;
    }
}
