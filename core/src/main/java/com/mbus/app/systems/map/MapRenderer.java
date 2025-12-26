package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
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

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.BusLineColors;
import com.mbus.app.utils.Constants;

import java.util.List;
import java.util.Set;

public class MapRenderer {

    private final OrthographicCamera camera;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch spriteBatch;
    private final BitmapFont font;

    private Texture[] mapTiles;
    private Texture markerTexture;
    private ZoomXY beginTile;
    private List<BusStop> stops;
    private List <BusLine> busLines;

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
    private static final float HOVER_SCALE = 1.4f;
    private static final float SELECT_SCALE = 1.4f;
    private static final float PULSE_SPEED = 2.5f;

    // Zoom-based scaling (subtle effect)z
    private static final float MIN_ZOOM_SCALE = 0.7f;
    private static final float MAX_ZOOM_SCALE = 4f;
    private static final float ZOOM_SCALE_FACTOR = 6f;

    // BUS LINES
    private static final float BUS_LINE_WIDTH = 10f;
    private static final Color BUS_LINE_COLOR = new Color(0.2f, 0.5f, 1.0f, 0.7f);

    private Set<Integer> visibleLineIds;

    public MapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
        this.font = new BitmapFont();
        this.font.getData().setScale(1.2f);
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

    public void setBusLines(List<BusLine> busLines) {
        this.busLines = busLines;
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

        // Render bus lines first (underneath markers)
        if (busLines != null && !busLines.isEmpty()) {
            renderBusLines();
        }

        // Only render markers if showMarkers is true
        if (showMarkers) {
            pulseTime += delta;
            renderMarkers();
        }
    }

    private void renderBusLines() {
        if (visibleLineIds == null || visibleLineIds.isEmpty()) {
            return; // Don't render if no lines are visible
        }

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float zoomScale = getZoomScale();
        float lineWidth = BUS_LINE_WIDTH * Math.min(zoomScale * 0.5f, 2.0f);

        for (BusLine line : busLines) {
            // Only render if this line is visible
            if (!visibleLineIds.contains(line.lineId)) {
                continue;
            }

            // Get the color for this specific line
            Color lineColor = BusLineColors.getColor(line.lineId);
            shapeRenderer.setColor(lineColor);

            List<com.mbus.app.model.Geolocation> path = line.getPath();

            if (path.size() < 2) continue;

            for (int i = 0; i < path.size() - 1; i++) {
                com.mbus.app.model.Geolocation point1 = path.get(i);
                com.mbus.app.model.Geolocation point2 = path.get(i + 1);

                Vector2 pos1 = MapRasterTiles.getPixelPosition(
                    point1.lat,
                    point1.lng,
                    beginTile.x,
                    beginTile.y
                );

                Vector2 pos2 = MapRasterTiles.getPixelPosition(
                    point2.lat,
                    point2.lng,
                    beginTile.x,
                    beginTile.y
                );

                if (!isLineVisible(pos1, pos2)) {
                    continue;
                }

                shapeRenderer.rectLine(pos1.x, pos1.y, pos2.x, pos2.y, lineWidth);
            }
        }

        shapeRenderer.end();
    }

    public void setVisibleLineIds(Set<Integer> lineIds) {
        this.visibleLineIds = lineIds;
    }

    // Helper method to check if a line segment is potentially visible
    private boolean isLineVisible(Vector2 pos1, Vector2 pos2) {
        // Add some padding for lines that might partially intersect the viewport
        float padding = 500f;

        float minX = -padding;
        float maxX = Constants.MAP_WIDTH + padding;
        float minY = -padding;
        float maxY = Constants.MAP_HEIGHT + padding;

        // Check if both points are completely outside the same boundary
        if (pos1.x < minX && pos2.x < minX) return false;
        if (pos1.x > maxX && pos2.x > maxX) return false;
        if (pos1.y < minY && pos2.y < minY) return false;
        if (pos1.y > maxY && pos2.y > maxY) return false;

        return true;
    }


    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();

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

    /**
     * Calculate zoom-based scale for markers
     * Returns a subtle scale multiplier based on camera zoom
     */
    private float getZoomScale() {
        float scale = MIN_ZOOM_SCALE + (camera.zoom * ZOOM_SCALE_FACTOR);
        return Math.min(Math.max(scale, MIN_ZOOM_SCALE), MAX_ZOOM_SCALE);
    }

    private void renderMarkers() {
        if (stops == null || stops.isEmpty()) return;
        if (markerTexture == null) {
            renderFallbackMarkers();
            return;
        }

        spriteBatch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Get zoom scale for this frame
        float zoomScale = getZoomScale();

        // Perform clustering based on current zoom level
        List<MarkerCluster> clusters = MarkerClusterer.clusterMarkers(
            stops,
            beginTile,
            camera.zoom,
            Constants.MAP_WIDTH,
            Constants.MAP_HEIGHT
        );

        // Render in four passes: normal markers, clusters, hover, selected

        // Pass 1: Normal individual markers
        spriteBatch.begin();
        for (MarkerCluster cluster : clusters) {
            if (cluster.isCluster) continue;

            BusStop stop = cluster.getSingleStop();
            if (stop == selectedStop || stop == hoveredStop) continue;

            Vector2 pos = cluster.getPosition();
            drawMarker(pos.x, pos.y, 1.0f * zoomScale, new Color(1, 1, 1, 0.9f), false);
        }
        spriteBatch.end();

        // Pass 2: Cluster markers
        for (MarkerCluster cluster : clusters) {
            if (!cluster.isCluster) continue;

            Vector2 pos = cluster.getPosition();
            drawCluster(pos.x, pos.y, cluster.getCount(), camera.zoom);
        }

        // Pass 3: Hovered marker
        if (hoveredStop != null) {
            for (MarkerCluster cluster : clusters) {
                if (!cluster.isCluster && cluster.getSingleStop() == hoveredStop) {
                    Vector2 pos = cluster.getPosition();

                    // Draw glow effect (also scales with zoom)
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    shapeRenderer.setColor(0.2f, 0.6f, 1.0f, 0.25f);
                    shapeRenderer.circle(pos.x, pos.y, BASE_MARKER_SIZE * 0.6f * zoomScale);
                    shapeRenderer.end();

                    // Draw marker
                    spriteBatch.begin();
                    drawMarker(pos.x, pos.y, HOVER_SCALE * zoomScale, new Color(0.5f, 0.8f, 1.0f, 1f), false);
                    spriteBatch.end();
                    break;
                }
            }
        }

        // Pass 4: Selected marker with breathing animation
        if (selectedStop != null) {
            for (MarkerCluster cluster : clusters) {
                if (!cluster.isCluster && cluster.getSingleStop() == selectedStop) {
                    Vector2 pos = cluster.getPosition();

                    // Subtle breathing animation
                    float breathe = (float) Math.sin(pulseTime * PULSE_SPEED) * 0.5f + 0.5f;
                    float currentScale = (SELECT_SCALE + breathe * 0.15f) * zoomScale;

                    // Draw marker with breathing animation
                    spriteBatch.begin();
                    drawMarker(pos.x, pos.y, currentScale, new Color(0.3f, 0.8f, 1.0f, 1f), false);
                    spriteBatch.end();
                    break;
                }
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
     * Get cluster color based on count
     * 2-3: Blue
     * 4-8: Yellow
     * 9+: Orange/Red
     */
    private Color getClusterColor(int count) {
        if (count <= 3) {
            // Blue for small clusters
            return new Color(0.2f, 0.6f, 1.0f, 1f);
        } else if (count <= 8) {
            // Yellow for medium clusters
            return new Color(1.0f, 0.85f, 0.2f, 1f);
        } else {
            // Orange for large clusters
            return new Color(1.0f, 0.5f, 0.1f, 1f);
        }
    }

    /**
     * Draw a cluster marker with count
     */
    private void drawCluster(float x, float y, int count, float zoom) {
        // Base size scales with zoom - bigger when zoomed out
        float zoomScale = 1.0f + (zoom * 2.5f);

        // Calculate cluster size based on count and zoom
        float countScale = 1.0f + Math.min(count / 8f, 1.2f);
        float clusterSize = BASE_MARKER_SIZE * countScale * zoomScale;

        // Get color based on cluster size
        Color clusterColor = getClusterColor(count);

        // Draw outer circle with gradient effect
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer ring (larger when zoomed out) - lighter version
        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.25f);
        shapeRenderer.circle(x, y, clusterSize * 0.7f);

        // Middle ring
        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.5f);
        shapeRenderer.circle(x, y, clusterSize * 0.55f);

        // Inner circle - full color
        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.85f);
        shapeRenderer.circle(x, y, clusterSize * 0.4f);

        shapeRenderer.end();

        // Draw count text
        spriteBatch.begin();

        String countText = String.valueOf(count);

        // Scale font with zoom
        float fontScale = 1f + (zoom * 8f);
        font.getData().setScale(fontScale);

        GlyphLayout layout = new GlyphLayout(font, countText);
        float textWidth = layout.width;
        float textHeight = layout.height;

        // Draw text
        font.setColor(1, 1, 1, 1);
        font.draw(spriteBatch, countText, x - textWidth / 2, y + textHeight / 2);

        spriteBatch.end();
    }

    /**
     * Fallback rendering using shapes if texture is not loaded
     */
    private void renderFallbackMarkers() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float zoomScale = getZoomScale();

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
                shapeRenderer.circle(pos.x, pos.y, 10 * zoomScale);
            } else if (stop == hoveredStop) {
                shapeRenderer.setColor(0.5f, 0.8f, 1.0f, 1);
                shapeRenderer.circle(pos.x, pos.y, 8 * zoomScale);
            } else {
                shapeRenderer.setColor(1, 0, 0, 1);
                shapeRenderer.circle(pos.x, pos.y, 6 * zoomScale);
            }
        }

        shapeRenderer.end();
    }
}
