package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Vector2;

import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.Constants;

import java.util.List;

public class MapRenderer {

    private final OrthographicCamera camera;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch spriteBatch;

    private Texture[] mapTiles;
    private Texture markerTexture;
    private ZoomXY beginTile;
    private List<BusStop> stops;

    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;

    private boolean showMarkers = true;

    // Marker state
    private BusStop hoveredStop = null;
    private BusStop selectedStop = null;

    // Animation
    private float pulseTime = 0f;

    // Marker size configuration
    private static final float BASE_MARKER_SIZE = 32f;
    private static final float HOVER_SCALE = 1.2f;
    private static final float SELECT_SCALE = 1.3f;
    private static final float PULSE_SPEED = 2.5f;

    public MapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
    }

    // ----------------------------
    // PUBLIC API
    // ----------------------------

    public void loadTiles(Texture[] tiles, ZoomXY beginTile) {
        this.mapTiles = tiles;
        this.beginTile = beginTile;
        buildTileMap();
    }

    public void setMarkerTexture(Texture texture) {
        this.markerTexture = texture;
    }

    public void setStops(List<BusStop> stops) {
        this.stops = stops;
    }

    public void setShowMarkers(boolean show) {
        this.showMarkers = show;
    }

    public boolean isShowingMarkers() {
        return showMarkers;
    }

    public void setHoveredStop(BusStop stop) {
        this.hoveredStop = stop;
    }

    public void setSelectedStop(BusStop stop) {
        this.selectedStop = stop;
    }

    public BusStop getSelectedStop() {
        return selectedStop;
    }

    public void render(float delta) {
        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();

        // Only render markers if showMarkers is true
        if (showMarkers) {
            pulseTime += delta;
            renderMarkers();
        }
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();

        if (tiledMap != null)
            tiledMap.dispose();

        if (mapTiles != null) {
            for (Texture t : mapTiles)
                if (t != null) t.dispose();
        }

        // Note: Don't dispose markerTexture here - it's managed by AssetManager
    }

    // Getters for click detection
    public ZoomXY getBeginTile() {
        return beginTile;
    }

    public List<BusStop> getStops() {
        return stops;
    }

    // ----------------------------
    // PRIVATE INTERNAL LOGIC
    // ----------------------------

    private void buildTileMap() {
        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(
            Constants.NUM_TILES,
            Constants.NUM_TILES,
            MapRasterTiles.TILE_SIZE,
            MapRasterTiles.TILE_SIZE
        );

        int index = 0;
        for (int j = Constants.NUM_TILES - 1; j >= 0; j--) {
            for (int i = 0; i < Constants.NUM_TILES; i++) {
                TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                cell.setTile(new StaticTiledMapTile(new TextureRegion(mapTiles[index], MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE)));
                layer.setCell(i, j, cell);
                index++;
            }
        }

        layers.add(layer);
        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);
    }

    private void renderMarkers() {
        if (stops == null || stops.isEmpty()) return;
        if (markerTexture == null) {
            renderFallbackMarkers();
            return;
        }

        spriteBatch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Render in three passes: normal markers first, then special states on top

        // Pass 1: Normal markers
        spriteBatch.begin();
        for (BusStop stop : stops) {
            if (stop == selectedStop || stop == hoveredStop) continue;

            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x < 0 || pos.y < 0 ||
                pos.x > Constants.MAP_WIDTH || pos.y > Constants.MAP_HEIGHT)
                continue;

            drawMarker(pos.x, pos.y, 1.0f, new Color(1, 1, 1, 0.9f), false);
        }
        spriteBatch.end();

        // Pass 2: Hovered marker
        if (hoveredStop != null) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                hoveredStop.geo.lat,
                hoveredStop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x >= 0 && pos.y >= 0 &&
                pos.x <= Constants.MAP_WIDTH && pos.y <= Constants.MAP_HEIGHT) {

                // Draw glow effect
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(0.2f, 0.6f, 1.0f, 0.25f);
                shapeRenderer.circle(pos.x, pos.y, BASE_MARKER_SIZE * 0.6f);
                shapeRenderer.end();

                // Draw marker
                spriteBatch.begin();
                drawMarker(pos.x, pos.y, HOVER_SCALE, new Color(0.5f, 0.8f, 1.0f, 1f), false);
                spriteBatch.end();
            }
        }

        // Pass 3: Selected marker with subtle breathing animation
        if (selectedStop != null) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                selectedStop.geo.lat,
                selectedStop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x >= 0 && pos.y >= 0 &&
                pos.x <= Constants.MAP_WIDTH && pos.y <= Constants.MAP_HEIGHT) {

                // Subtle breathing animation
                float breathe = (float) Math.sin(pulseTime * PULSE_SPEED) * 0.5f + 0.5f;
                float currentScale = SELECT_SCALE + breathe * 0.15f;

                // Draw marker with breathing animation
                spriteBatch.begin();
                drawMarker(pos.x, pos.y, currentScale, new Color(0.3f, 0.8f, 1.0f, 1f), false);
                spriteBatch.end();
            }
        }
    }

    private void drawMarker(float x, float y, float scale, Color tint, boolean addHighlight) {
        float size = BASE_MARKER_SIZE * scale;
        float halfSize = size / 2f;

        // Draw shadow first
        spriteBatch.setColor(0, 0, 0, 0.3f * tint.a);
        spriteBatch.draw(markerTexture,
            x - halfSize + 2, y - halfSize - 2,
            size, size);

        // Draw main marker
        spriteBatch.setColor(tint);
        spriteBatch.draw(markerTexture,
            x - halfSize, y - halfSize,
            size, size);

        // Optional highlight overlay for selected
        if (addHighlight) {
            spriteBatch.setColor(1, 1, 1, 0.3f);
            spriteBatch.draw(markerTexture,
                x - halfSize, y - halfSize,
                size, size);
        }

        // Reset color
        spriteBatch.setColor(Color.WHITE);
    }

    /**
     * Fallback rendering using shapes if texture is not loaded
     */
    private void renderFallbackMarkers() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (BusStop stop : stops) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x < 0 || pos.y < 0 ||
                pos.x > Constants.MAP_WIDTH || pos.y > Constants.MAP_HEIGHT)
                continue;

            if (stop == selectedStop) {
                shapeRenderer.setColor(0.2f, 0.8f, 1.0f, 1);
                shapeRenderer.circle(pos.x, pos.y, 10);
            } else if (stop == hoveredStop) {
                shapeRenderer.setColor(0.5f, 0.8f, 1.0f, 1);
                shapeRenderer.circle(pos.x, pos.y, 8);
            } else {
                shapeRenderer.setColor(1, 0, 0, 1);
                shapeRenderer.circle(pos.x, pos.y, 6);
            }
        }

        shapeRenderer.end();
    }
}
