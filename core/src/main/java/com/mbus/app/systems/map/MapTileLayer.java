package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.MipMaps;
import com.mbus.app.utils.ZoomXY;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws the map background. The low-resolution overview is always there; full-resolution tiles are
 * streamed in for the visible area only, and the least recently used ones are dropped again.
 */
public class MapTileLayer implements Disposable {

    /** Screen pixels per world pixel above which the overview gets blurry. */
    private static final float FULL_DETAIL_DENSITY = 0.3f;
    private static final int MIN_CACHED_TILES = 96;
    private static final int MAX_UPLOADS_PER_FRAME = 3;
    private static final int SIZE = Constants.NUM_TILES;

    private final OrthographicCamera camera;
    private final ZoomXY beginTile;
    private final Texture overview;

    private final LinkedHashMap<Integer, Texture> tiles = new LinkedHashMap<>(128, 0.75f, true);
    private final Set<Integer> requested = new HashSet<>();
    private final ConcurrentLinkedQueue<DecodedTile> decoded = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;

    private volatile VisibleRange visible = VisibleRange.NONE;
    private volatile boolean disposed = false;

    /** Takes ownership of the pixmap. */
    public MapTileLayer(OrthographicCamera camera, ZoomXY beginTile, Pixmap overviewPixels) {
        this.camera = camera;
        this.beginTile = beginTile;

        this.overview = MipMaps.create(overviewPixels);
        overviewPixels.dispose();

        this.executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "tile-decoder");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void render(SpriteBatch batch) {
        uploadDecodedTiles();

        batch.disableBlending();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(overview, 0, 0, Constants.MAP_WIDTH, Constants.MAP_HEIGHT,
            0, 0, TileOverview.MAP_PIXELS, TileOverview.MAP_PIXELS, false, false);

        float density = Gdx.graphics.getBackBufferWidth() / (camera.viewportWidth * camera.zoom);
        if (density >= FULL_DETAIL_DENSITY) {
            drawFullDetail(batch);
        } else {
            visible = VisibleRange.NONE;
        }

        batch.end();
        batch.enableBlending();
        dropUnusedTiles();
    }

    private void drawFullDetail(SpriteBatch batch) {
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        VisibleRange range = new VisibleRange(
            toTile(camera.position.x - halfWidth),
            toTile(camera.position.x + halfWidth),
            toTile(Constants.MAP_HEIGHT - (camera.position.y + halfHeight)),
            toTile(Constants.MAP_HEIGHT - (camera.position.y - halfHeight))
        );
        visible = range;

        for (int row = range.firstRow; row <= range.lastRow; row++) {
            for (int column = range.firstColumn; column <= range.lastColumn; column++) {
                int index = row * SIZE + column;
                Texture texture = tiles.get(index);
                if (texture != null) {
                    batch.draw(texture,
                        column * MapRasterTiles.TILE_SIZE,
                        Constants.MAP_HEIGHT - (row + 1) * MapRasterTiles.TILE_SIZE,
                        MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE);
                } else {
                    request(index);
                }
            }
        }
    }

    private static int toTile(float worldPixels) {
        return MathUtils.clamp(MathUtils.floor(worldPixels / MapRasterTiles.TILE_SIZE), 0, SIZE - 1);
    }

    /** A tile that fails to load stays in {@link #requested}, so it is not retried every frame. */
    private void request(final int index) {
        if (!requested.add(index)) return;

        executor.execute(() -> {
            if (disposed) return;
            if (!visible.contains(index)) {
                // Scrolled past before its turn came; allow it to be requested again.
                decoded.add(new DecodedTile(index, null));
                return;
            }
            try {
                byte[] data = MapRasterTiles.getTileData(
                    Constants.ZOOM, beginTile.x + index % SIZE, beginTile.y + index / SIZE);
                decoded.add(new DecodedTile(index, TileOverview.decode(data)));
            } catch (Exception e) {
                Gdx.app.error("MapTileLayer", "Tile " + index + " not available: " + e.getMessage());
            }
        });
    }

    private void uploadDecodedTiles() {
        int uploads = 0;
        while (uploads < MAX_UPLOADS_PER_FRAME) {
            DecodedTile tile = decoded.poll();
            if (tile == null) return;

            requested.remove(tile.index);
            if (tile.pixels == null) continue;

            Texture texture = MipMaps.create(tile.pixels);
            tile.pixels.dispose();
            tiles.put(tile.index, texture);
            uploads++;
        }
    }

    private void dropUnusedTiles() {
        VisibleRange range = visible;
        int limit = Math.max(MIN_CACHED_TILES, range.count() + range.count() / 4);

        Iterator<Map.Entry<Integer, Texture>> oldestFirst = tiles.entrySet().iterator();
        while (tiles.size() > limit && oldestFirst.hasNext()) {
            oldestFirst.next().getValue().dispose();
            oldestFirst.remove();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        executor.shutdownNow();
        for (DecodedTile tile = decoded.poll(); tile != null; tile = decoded.poll()) {
            if (tile.pixels != null) tile.pixels.dispose();
        }
        for (Texture texture : tiles.values()) {
            texture.dispose();
        }
        tiles.clear();
        overview.dispose();
    }

    private static class DecodedTile {
        final int index;
        final Pixmap pixels;

        DecodedTile(int index, Pixmap pixels) {
            this.index = index;
            this.pixels = pixels;
        }
    }

    private static class VisibleRange {
        static final VisibleRange NONE = new VisibleRange(0, -1, 0, -1);

        final int firstColumn, lastColumn, firstRow, lastRow;

        VisibleRange(int firstColumn, int lastColumn, int firstRow, int lastRow) {
            this.firstColumn = firstColumn;
            this.lastColumn = lastColumn;
            this.firstRow = firstRow;
            this.lastRow = lastRow;
        }

        boolean contains(int index) {
            int column = index % SIZE;
            int row = index / SIZE;
            return column >= firstColumn && column <= lastColumn && row >= firstRow && row <= lastRow;
        }

        int count() {
            return (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1);
        }
    }
}
