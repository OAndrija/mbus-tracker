package com.mbus.app.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MapRasterTiles {

    private static final Logger log = new Logger("MapRasterTiles", Logger.INFO);

    // Geoapify raster tiles
    static String mapServiceUrl = "https://maps.geoapify.com/v1/tile/";
    static String token = "?&apiKey=" + Keys.GEOAPIFY;
    static String tilesetId = "klokantech-basic";
    static String format = "@2x.png";

    public static final int TILE_SIZE = 512;

    // Cache folder in LibGDX local storage
    private static final String CACHE_FOLDER = "tile_cache/";

    static {
        FileHandle cacheDir = Gdx.files.local(CACHE_FOLDER);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
            log.info("Created tile cache at: " + cacheDir.path());
        } else {
            log.info("Using existing tile cache: " + cacheDir.path());
        }
    }

    // ===========================
    // TILE FETCHING WITH CACHING
    // ===========================

    public static Texture getRasterTile(int zoom, int x, int y) throws IOException {
        String fileName = zoom + "_" + x + "_" + y + ".png";
        FileHandle file = Gdx.files.local(CACHE_FOLDER + fileName);

        // 1) LOAD FROM CACHE
        if (file.exists()) {
            byte[] cached = readFileBytes(fileName);
            if (cached != null) {
                log.info("Cache hit → " + fileName);
                return getTexture(cached);
            } else {
                log.error("Cache read failed for " + fileName + ", redownloading.");
            }
        }

        // 2) DOWNLOAD
        String urlStr = mapServiceUrl + tilesetId + "/" + zoom + "/" + x + "/" + y + format + token;
        log.info("Downloading tile: zoom=" + zoom + " x=" + x + " y=" + y);
        log.debug("URL: " + urlStr);

        URL url = new URL(urlStr);
        ByteArrayOutputStream bis = fetchTile(url);

        log.info("Tile downloaded (" + bis.size() + " bytes)");

        // 3) SAVE TO CACHE
        saveFileBytes(fileName, bis.toByteArray());

        return getTexture(bis.toByteArray());
    }

    public static Texture getRasterTile(String zoomXY) throws IOException {
        String safeName = zoomXY.replace("/", "_") + ".png";
        FileHandle file = Gdx.files.local(CACHE_FOLDER + safeName);

        if (file.exists()) {
            byte[] cached = readFileBytes(safeName);
            if (cached != null) {
                log.info("Cache hit → " + safeName);
                return getTexture(cached);
            } else {
                log.error("Cache read failed for " + safeName + ", redownloading.");
            }
        }

        String urlStr = mapServiceUrl + tilesetId + "/" + zoomXY + format + token;
        log.info("Downloading tile: " + zoomXY);

        URL url = new URL(urlStr);
        ByteArrayOutputStream bis = fetchTile(url);

        log.info("Tile downloaded (" + bis.size() + " bytes)");

        saveFileBytes(safeName, bis.toByteArray());

        return getTexture(bis.toByteArray());
    }

    public static Texture getRasterTile(ZoomXY zoomXY) throws IOException {
        return getRasterTile(zoomXY.zoom, zoomXY.x, zoomXY.y);
    }

    public static Texture[] getRasterTileZone(ZoomXY zoomXY, int size) throws IOException {
        log.info("Fetching tile zone: center=" + zoomXY + " size=" + size);

        Texture[] array = new Texture[size * size];
        int[] factorY = new int[size * size];
        int[] factorX = new int[size * size];

        int value = (size - 1) / -2;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                factorY[i * size + j] = value;
                factorX[i + j * size] = value;
            }
            value++;
        }

        for (int i = 0; i < size * size; i++) {
            int tx = zoomXY.x + factorX[i];
            int ty = zoomXY.y + factorY[i];

            log.info("Zone tile: " + zoomXY.zoom + "/" + tx + "/" + ty);
            array[i] = getRasterTile(zoomXY.zoom, tx, ty);
        }

        return array;
    }

    // ===========================
    // FILE I/O HELPERS (LibGDX)
    // ===========================

    private static void saveFileBytes(String fileName, byte[] data) {
        try {
            FileHandle file = Gdx.files.local(CACHE_FOLDER + fileName);
            file.writeBytes(data, false);
            log.info("Saved cached tile: " + fileName);
        } catch (Exception e) {
            log.error("Failed to save cached tile: " + fileName, e);
        }
    }

    private static byte[] readFileBytes(String fileName) {
        try {
            FileHandle file = Gdx.files.local(CACHE_FOLDER + fileName);
            if (!file.exists()) {
                return null;
            }
            return file.readBytes();
        } catch (Exception e) {
            log.error("Failed to read cached tile: " + fileName, e);
            return null;
        }
    }

    // ===========================
    // TILE DOWNLOAD CORE
    // ===========================

    public static ByteArrayOutputStream fetchTile(URL url) throws IOException {
        log.debug("Connecting…");

        long start = System.currentTimeMillis();

        ByteArrayOutputStream bis = new ByteArrayOutputStream();
        InputStream is = url.openStream();
        byte[] buffer = new byte[4096];

        int n;
        while ((n = is.read(buffer)) > 0) {
            bis.write(buffer, 0, n);
        }

        is.close();
        long time = System.currentTimeMillis() - start;

        log.info("Tile fetched in " + time + " ms");

        return bis;
    }

    public static Texture getTexture(byte[] array) {
        return new Texture(new Pixmap(array, 0, array.length));
    }

    // ===========================
    // TILE NUMBER CALCULATION
    // ===========================

    public static ZoomXY getTileNumber(final double lat, final double lon, final int zoom) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) +
            1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

        xtile = Math.max(0, Math.min(xtile, (1 << zoom) - 1));
        ytile = Math.max(0, Math.min(ytile, (1 << zoom) - 1));

        ZoomXY tile = new ZoomXY(zoom, xtile, ytile);
        log.info("Tile number result: " + tile);

        return tile;
    }

    // ===========================
    // PIXEL POSITION CONVERSION
    // ===========================

    public static Vector2 getPixelPosition(double lat, double lng, int beginTileX, int beginTileY) {
        double[] worldCoordinate = project(lat, lng, TILE_SIZE);
        double scale = Math.pow(2, Constants.ZOOM);

        Vector2 result = new Vector2(
            (float) (Math.floor(worldCoordinate[0] * scale) - (beginTileX * TILE_SIZE)),
            (float) (Constants.MAP_HEIGHT -
                (Math.floor(worldCoordinate[1] * scale) -
                    (beginTileY * TILE_SIZE) - 1))
        );

        log.debug("Pixel position: " + result);
        return result;
    }

    public static double[] project(double lat, double lng, int tileSize) {
        double siny = Math.sin((lat * Math.PI) / 180);
        siny = Math.min(Math.max(siny, -0.9999), 0.9999);

        return new double[]{
            tileSize * (0.5 + lng / 360),
            tileSize * (0.5 -
                Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI))
        };
    }

    // ===========================
    // ROUTING API
    // ===========================

    public static Geolocation[][] fetchPath(Geolocation[] geolocations) {
        log.info("Fetching interpolated route for " + geolocations.length + " points");

        double[][] coordinates = new double[geolocations.length][2];
        for (int i = 0; i < geolocations.length; i++) {
            coordinates[i] = new double[]{geolocations[i].lat, geolocations[i].lng};
        }

        try {
            return getRouteFromCoordinates(coordinates);
        } catch (Exception e) {
            log.error("Routing failed", e);
        }
        return null;
    }

    public static Geolocation[][] getRouteFromCoordinates(double[][] coordinates) throws Exception {
        log.info("Requesting Geoapify route…");

        StringBuilder coordinatesPath = new StringBuilder();
        for (int i = 0; i < coordinates.length; i++) {
            coordinatesPath.append(coordinates[i][0]).append(",").append(coordinates[i][1]);
            if (i < coordinates.length - 1) coordinatesPath.append("|");
        }

        String urlString = "https://api.geoapify.com/v1/routing?waypoints=" + coordinatesPath +
            "&mode=drive&apiKey=" + Keys.GEOAPIFY;

        log.debug("Routing URL: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            log.error("Routing request failed. HTTP " + connection.getResponseCode());
            return null;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();

        String line;
        while ((line = in.readLine()) != null) response.append(line);

        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray features = jsonResponse.getJSONArray("features");

        if (features.length() == 0) {
            log.error("Routing API returned no features.");
            return null;
        }

        JSONObject geometry = features.getJSONObject(0).getJSONObject("geometry");
        JSONArray coordinatesArray = geometry.getJSONArray("coordinates");

        Geolocation[][] geolocations = new Geolocation[coordinatesArray.length()][];

        for (int i = 0; i < coordinatesArray.length(); i++) {
            JSONArray coord = coordinatesArray.getJSONArray(i);
            Geolocation[] segment = new Geolocation[coord.length()];

            for (int j = 0; j < coord.length(); j++) {
                JSONArray c = coord.getJSONArray(j);
                // GeoJSON: [lon, lat]
                segment[j] = new Geolocation(c.getDouble(1), c.getDouble(0));
            }

            geolocations[i] = segment;
        }

        log.info("Route parsing complete.");
        return geolocations;
    }
}
