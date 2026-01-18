package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Logger;
import com.mbus.app.utils.Constants;
import com.mbus.app.model.Geolocation;
import com.mbus.app.utils.Keys;
import com.mbus.app.model.ZoomXY;
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

    static String mapServiceUrl = "https://maps.geoapify.com/v1/tile/";
    static String token = "?&apiKey=" + Keys.GEOAPIFY;
    static String tilesetId = "osm-bright-smooth";
    static String format = "@2x.png";

    public static final int TILE_SIZE = 512;

    private static final String CACHE_FOLDER = "tile_cache/";

    static {
        FileHandle cacheDir = Gdx.files.local(CACHE_FOLDER);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
            log.info("Created tile cache at: " + cacheDir.path());
        } else {
            log.info("Using existing tile cache: " + cacheDir.path());
        }

        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "16");
    }

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

    public static ByteArrayOutputStream fetchTile(URL url) throws IOException {
        log.debug("Connecting…");

        long start = System.currentTimeMillis();

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestProperty("Connection", "keep-alive");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(true);

            ByteArrayOutputStream bis = new ByteArrayOutputStream();
            InputStream is = connection.getInputStream();
            byte[] buffer = new byte[8192];

            int n;
            while ((n = is.read(buffer)) > 0) {
                bis.write(buffer, 0, n);
            }

            is.close();
            long time = System.currentTimeMillis() - start;

            log.info("Tile fetched in " + time + " ms");

            return bis;
        } finally {
            if (connection != null) {
                connection.getInputStream().close();
            }
        }
    }

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

    public static byte[] getTileData(int zoom, int x, int y) throws IOException {
        String fileName = zoom + "_" + x + "_" + y + ".png";
        FileHandle file = Gdx.files.local(CACHE_FOLDER + fileName);

        // 1) LOAD FROM CACHE
        if (file.exists()) {
            byte[] cached = readFileBytes(fileName);
            if (cached != null) {
                log.info("Cache hit → " + fileName);
                return cached;
            } else {
                log.error("Cache read failed for " + fileName + ", redownloading.");
            }
        }

        // 2) DOWNLOAD
        String urlStr = mapServiceUrl + tilesetId + "/" + zoom + "/" + x + "/" + y + format + token;
        log.info("Downloading tile: zoom=" + zoom + " x=" + x + " y=" + y);

        URL url = new URL(urlStr);
        ByteArrayOutputStream bis = fetchTile(url);

        byte[] data = bis.toByteArray();
        log.info("Tile downloaded (" + data.length + " bytes)");

        // 3) SAVE TO CACHE
        saveFileBytes(fileName, data);

        return data;
    }

    public static Texture createTextureFromData(byte[] data) {
        return new Texture(new Pixmap(data, 0, data.length));
    }
}
