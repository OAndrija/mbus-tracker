package com.mbus.app.systems.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Logger;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeoJSONLoader {

    private static final Logger log = new Logger("GeoJSONLoader", Logger.INFO);

    private GeoJSONLoader() {
    }

    public static List<BusStop> loadBusStopsFromFile(String path) {

        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            file = Gdx.files.local(path);
        }

        if (!file.exists()) {
            log.error("GeoJSON file not found: " + path);
            return Collections.emptyList();
        }

        log.info("Loading bus stops from: " + file.path());

        String jsonText = file.readString("UTF-8");

        JSONObject root = new JSONObject(jsonText);

        JSONArray features = root.getJSONArray("features");
        List<BusStop> stops = new ArrayList<BusStop>(features.length());

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);

            JSONObject geometry = feature.getJSONObject("geometry");
            String geomType = geometry.getString("type");

            if (!"Point".equalsIgnoreCase(geomType)) {
                continue;
            }

            JSONArray coords = geometry.getJSONArray("coordinates");
            double x = coords.getDouble(0);
            double y = coords.getDouble(1);

            double[] latlon = GeoConverter3794.toWGS84(x, y + 5000000);
            double lat = latlon[0];
            double lon = latlon[1];

            Geolocation geo = new Geolocation(lat, lon);

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

    public static List<BusLine> loadBusLinesFromFile(String path) {

        // 1) Find file via LibGDX
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            file = Gdx.files.local(path);
        }

        if (!file.exists()) {
            log.error("GeoJSON file not found: " + path);
            return Collections.emptyList();
        }

        log.info("Loading bus lines from: " + file.path());

        // 2) Read file as String
        String jsonText = file.readString("UTF-8");

        // 3) Parse JSON
        JSONObject root = new JSONObject(jsonText);

        JSONArray features = root.getJSONArray("features");

        // Map to track the best variant for each lineId
        Map<Integer, BusLine> bestVariantPerLine = new HashMap<Integer, BusLine>();

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);

            // geometry
            JSONObject geometry = feature.getJSONObject("geometry");
            String geomType = geometry.getString("type");

            if (!"LineString".equalsIgnoreCase(geomType)) {
                log.debug("Skipping non-LineString geometry: " + geomType);
                continue;
            }

            JSONArray coordinates = geometry.getJSONArray("coordinates");

            // Convert each coordinate pair to WGS84
            List<Geolocation> paths = new ArrayList<Geolocation>(coordinates.length());
            List<double[]> originalCoords = new ArrayList<double[]>(coordinates.length());

            for (int j = 0; j < coordinates.length(); j++) {
                JSONArray coord = coordinates.getJSONArray(j);
                double x = coord.getDouble(0);
                double y = coord.getDouble(1);

                // Store original coordinates
                originalCoords.add(new double[]{x, y});

                // Convert to WGS84
                double[] latlon = GeoConverter3794.toWGS84(x, y + 5000000);
                double lat = latlon[0];
                double lon = latlon[1];

                paths.add(new Geolocation(lat, lon));
            }

            // 5) Properties
            JSONObject props = feature.getJSONObject("properties");

            int lineId = props.optInt("linije_id", -1);
            int variantId = props.optInt("varianta_t", -1);
            int direction = props.optInt("smer", 0);
            double lineLength = props.optDouble("dolzina_li", 0.0);
            String name = props.optString("naziv", "");
            String note = props.optString("opomba", "");
            String providerName = props.optString("naziv_ponudnik", "");
            String providerLink = props.optString("povezava_ponudnik", "");

            BusLine line = new BusLine(
                lineId,
                variantId,
                direction,
                lineLength,
                name,
                note,
                providerName,
                providerLink,
                paths,
                originalCoords,
                null  // stops - will be added by relationship builder
            );

            // Keep only the variant with the most geolocations for each lineId
            BusLine existing = bestVariantPerLine.get(lineId);
            if (existing == null || paths.size() > existing.getPointCount()) {
                bestVariantPerLine.put(lineId, line);
                if (existing != null) {
                    log.debug("Replacing lineId " + lineId + " variant (had " +
                        existing.getPointCount() + " points, new has " + paths.size() + " points)");
                }
            }
        }

        // Convert map values to list
        List<BusLine> lines = new ArrayList<BusLine>(bestVariantPerLine.values());

        // Collect unique line IDs and log them
        java.util.Set<Integer> uniqueLineIds = new java.util.TreeSet<Integer>();
        for (BusLine line : lines) {
            uniqueLineIds.add(line.lineId);
        }

        // Convert to comma-separated string
        StringBuilder lineIdsStr = new StringBuilder();
        int count = 0;
        for (Integer id : uniqueLineIds) {
            if (count > 0) {
                lineIdsStr.append(", ");
            }
            lineIdsStr.append(id);
            count++;
        }

        log.info("Loaded " + lines.size() + " unique bus lines from " + path);
        log.info("Unique bus line IDs (" + uniqueLineIds.size() + "): " + lineIdsStr.toString());

        return lines;
    }
}
