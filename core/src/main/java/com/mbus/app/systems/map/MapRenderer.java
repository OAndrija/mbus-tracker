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
    private List<BusStop> allStops;
    private List<BusStop> filteredStops;
    private List<BusLine> busLines;

    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;

    private boolean showMarkers = true;

    private BusStop hoveredStop = null;
    private BusStop selectedStop = null;

    private BusLine hoveredLine = null;
    private BusLine selectedLine = null;

    private float pulseTime = 0f;

    private static final float BASE_MARKER_SIZE = 32f;
    private static final float HOVER_SCALE = 1.4f;
    private static final float SELECT_SCALE = 1.4f;
    private static final float PULSE_SPEED = 2.5f;

    private static final float MIN_ZOOM_SCALE = 0.7f;
    private static final float MAX_ZOOM_SCALE = 4f;
    private static final float ZOOM_SCALE_FACTOR = 6f;

    private static final float BUS_LINE_WIDTH = 10f;
    private static final float HOVER_LINE_WIDTH = 14f;
    private static final float SELECT_LINE_WIDTH = 16f;

    private static final float LABEL_BASE_FONT_SCALE = 1.8f;
    private static final float LABEL_ZOOM_FONT_SCALE = 13.0f;
    private static final float LABEL_BASE_PADDING_X = 16f;
    private static final float LABEL_BASE_PADDING_Y = 12f;
    private static final float LABEL_CORNER_RADIUS = 8f;
    private static final float LABEL_SHADOW_OFFSET = 3f;

    private Set<Integer> visibleLineIds;
    private BusAnimationRenderer busAnimationRenderer;
    private float currentTimeMinutes = 0f;
    private int currentDayType = 0;

    public MapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
        this.font = new BitmapFont();
        this.busAnimationRenderer = new BusAnimationRenderer(spriteBatch);
    }

    public void loadBusSprites(TextureRegion north, TextureRegion northeast,
                               TextureRegion east, TextureRegion southeast,
                               TextureRegion south, TextureRegion southwest,
                               TextureRegion west, TextureRegion northwest) {
        busAnimationRenderer.loadBusSprites(north, northeast, east, southeast,
            south, southwest, west, northwest);
    }

    public void setCurrentTime(float timeMinutes, int dayType) {
        this.currentTimeMinutes = timeMinutes;
        this.currentDayType = dayType;
    }

    public void loadTiles(Texture[] tiles, ZoomXY beginTile) {
        this.mapTiles = tiles;
        this.beginTile = beginTile;
        buildTileMap();
    }

    public void setMarkerTexture(Texture texture) {
        this.markerTexture = texture;
    }

    public void setStops(List<BusStop> stops) {
        this.allStops = stops;
        this.filteredStops = stops;
    }

    public void setFilteredStops(List<BusStop> stops) {
        this.filteredStops = stops;
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

    public void setHoveredLine(BusLine line) {
        this.hoveredLine = line;
    }

    public void setSelectedLine(BusLine line) {
        this.selectedLine = line;
    }

    public BusLine getSelectedLine() {
        return selectedLine;
    }

    public void render(float delta) {
        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();

        if (busLines != null && !busLines.isEmpty()) {
            renderBusLines();
        }

        if (showMarkers) {
            pulseTime += delta;
            renderMarkers();
        }

        if (selectedLine != null) {
            spriteBatch.setProjectionMatrix(camera.combined);
            spriteBatch.begin();
            busAnimationRenderer.renderActiveBuses(
                selectedLine,
                currentTimeMinutes,
                currentDayType,
                beginTile,
                camera.zoom,
                delta
            );
            spriteBatch.end();
        }

        renderLineLabels();
    }

    private void renderBusLines() {
        if (visibleLineIds == null || visibleLineIds.isEmpty()) {
            return;
        }

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float zoomScale = getZoomScale();
        float baseLineWidth = BUS_LINE_WIDTH * Math.min(zoomScale * 0.5f, 2.0f);

        for (BusLine line : busLines) {
            if (!visibleLineIds.contains(line.lineId)) continue;
            if (line == hoveredLine || line == selectedLine) continue;

            Color lineColor = BusLineColors.getColor(line.lineId);
            renderLine(line, baseLineWidth, lineColor);
        }

        if (hoveredLine != null && visibleLineIds.contains(hoveredLine.lineId)) {
            Color lineColor = BusLineColors.getColor(hoveredLine.lineId);
            Color glowColor = new Color(lineColor.r, lineColor.g, lineColor.b, 0.3f);
            renderLine(hoveredLine, HOVER_LINE_WIDTH * Math.min(zoomScale * 0.5f, 2.0f) + 4f, glowColor);
            renderLine(hoveredLine, HOVER_LINE_WIDTH * Math.min(zoomScale * 0.5f, 2.0f), lineColor);
        }

        if (selectedLine != null && visibleLineIds.contains(selectedLine.lineId)) {
            float breathe = (float) Math.sin(pulseTime * PULSE_SPEED) * 0.5f + 0.5f;
            float animatedWidth = (SELECT_LINE_WIDTH + breathe * 3f) * Math.min(zoomScale * 0.5f, 2.0f);

            Color lineColor = BusLineColors.getColor(selectedLine.lineId);
            Color glowColor = new Color(lineColor.r, lineColor.g, lineColor.b, 0.4f + breathe * 0.2f);
            renderLine(selectedLine, animatedWidth + 6f, glowColor);
            renderLine(selectedLine, animatedWidth, lineColor);
        }

        shapeRenderer.end();
    }

    private void renderLine(BusLine line, float lineWidth, Color color) {
        shapeRenderer.setColor(color);
        List<com.mbus.app.model.Geolocation> path = line.getPath();

        if (path.size() < 2) return;

        for (int i = 0; i < path.size() - 1; i++) {
            com.mbus.app.model.Geolocation point1 = path.get(i);
            com.mbus.app.model.Geolocation point2 = path.get(i + 1);

            Vector2 pos1 = MapRasterTiles.getPixelPosition(
                point1.lat, point1.lng, beginTile.x, beginTile.y
            );
            Vector2 pos2 = MapRasterTiles.getPixelPosition(
                point2.lat, point2.lng, beginTile.x, beginTile.y
            );

            if (!isLineVisible(pos1, pos2)) continue;

            shapeRenderer.rectLine(pos1.x, pos1.y, pos2.x, pos2.y, lineWidth);
        }
    }

    public void setVisibleLineIds(Set<Integer> lineIds) {
        this.visibleLineIds = lineIds;
    }

    private boolean isLineVisible(Vector2 pos1, Vector2 pos2) {
        float padding = 500f;
        float minX = -padding;
        float maxX = Constants.MAP_WIDTH + padding;
        float minY = -padding;
        float maxY = Constants.MAP_HEIGHT + padding;

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
    }

    public ZoomXY getBeginTile() {
        return beginTile;
    }

    public List<BusStop> getStops() {
        return allStops;
    }

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

    private float getZoomScale() {
        float scale = MIN_ZOOM_SCALE + (camera.zoom * ZOOM_SCALE_FACTOR);
        return Math.min(Math.max(scale, MIN_ZOOM_SCALE), MAX_ZOOM_SCALE);
    }

    private void renderMarkers() {
        if (filteredStops == null || filteredStops.isEmpty()) return;
        if (markerTexture == null) {
            renderFallbackMarkers();
            return;
        }

        spriteBatch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        float zoomScale = getZoomScale();

        List<MarkerCluster> clusters = MarkerClusterer.clusterMarkers(
            filteredStops,
            beginTile,
            camera.zoom,
            Constants.MAP_WIDTH,
            Constants.MAP_HEIGHT
        );

        spriteBatch.begin();
        for (MarkerCluster cluster : clusters) {
            if (cluster.isCluster) continue;

            BusStop stop = cluster.getSingleStop();
            if (stop == selectedStop || stop == hoveredStop) continue;

            Vector2 pos = cluster.getPosition();
            drawMarker(pos.x, pos.y, 1.0f * zoomScale, new Color(1, 1, 1, 0.9f), false);
        }
        spriteBatch.end();

        for (MarkerCluster cluster : clusters) {
            if (!cluster.isCluster) continue;

            Vector2 pos = cluster.getPosition();
            drawCluster(pos.x, pos.y, cluster.getCount(), camera.zoom);
        }

        if (hoveredStop != null) {
            for (MarkerCluster cluster : clusters) {
                if (!cluster.isCluster && cluster.getSingleStop() == hoveredStop) {
                    Vector2 pos = cluster.getPosition();

                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    shapeRenderer.setColor(0.2f, 0.6f, 1.0f, 0.25f);
                    shapeRenderer.circle(pos.x, pos.y, BASE_MARKER_SIZE * 0.6f * zoomScale);
                    shapeRenderer.end();

                    spriteBatch.begin();
                    drawMarker(pos.x, pos.y, HOVER_SCALE * zoomScale, new Color(0.5f, 0.8f, 1.0f, 1f), false);
                    spriteBatch.end();
                    break;
                }
            }
        }

        if (selectedStop != null) {
            for (MarkerCluster cluster : clusters) {
                if (!cluster.isCluster && cluster.getSingleStop() == selectedStop) {
                    Vector2 pos = cluster.getPosition();

                    float breathe = (float) Math.sin(pulseTime * PULSE_SPEED) * 0.5f + 0.5f;
                    float currentScale = (SELECT_SCALE + breathe * 0.15f) * zoomScale;

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

        spriteBatch.setColor(0, 0, 0, 0.3f * tint.a);
        spriteBatch.draw(markerTexture,
            x - halfSize + 2, y - halfSize - 2,
            size, size);

        spriteBatch.setColor(tint);
        spriteBatch.draw(markerTexture,
            x - halfSize, y - halfSize,
            size, size);

        if (addHighlight) {
            spriteBatch.setColor(1, 1, 1, 0.3f);
            spriteBatch.draw(markerTexture,
                x - halfSize, y - halfSize,
                size, size);
        }

        spriteBatch.setColor(Color.WHITE);
    }

    private Color getClusterColor(int count) {
        if (count <= 3) {
            return new Color(0.2f, 0.6f, 1.0f, 1f);
        } else if (count <= 8) {
            return new Color(1.0f, 0.85f, 0.2f, 1f);
        } else {
            return new Color(1.0f, 0.5f, 0.1f, 1f);
        }
    }

    private void drawCluster(float x, float y, int count, float zoom) {
        float zoomScale = 1.0f + (zoom * 2.5f);
        float countScale = 1.0f + Math.min(count / 8f, 1.2f);
        float clusterSize = BASE_MARKER_SIZE * countScale * zoomScale;

        Color clusterColor = getClusterColor(count);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.25f);
        shapeRenderer.circle(x, y, clusterSize * 0.7f);

        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.5f);
        shapeRenderer.circle(x, y, clusterSize * 0.55f);

        shapeRenderer.setColor(clusterColor.r, clusterColor.g, clusterColor.b, 0.85f);
        shapeRenderer.circle(x, y, clusterSize * 0.4f);

        shapeRenderer.end();

        spriteBatch.begin();

        String countText = String.valueOf(count);
        float fontScale = 0.5f + (zoom * 13f);
        font.getData().setScale(fontScale);

        GlyphLayout layout = new GlyphLayout(font, countText);
        float textWidth = layout.width;
        float textHeight = layout.height;

        font.setColor(1, 1, 1, 1);
        font.draw(spriteBatch, countText, x - textWidth / 2, y + textHeight / 2);

        spriteBatch.end();
    }

    private void renderFallbackMarkers() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float zoomScale = getZoomScale();

        for (BusStop stop : filteredStops) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat, stop.geo.lng,
                beginTile.x, beginTile.y
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

    private void renderLineLabels() {
        spriteBatch.setProjectionMatrix(camera.combined);

        if (hoveredLine != null && visibleLineIds != null && visibleLineIds.contains(hoveredLine.lineId)) {
            renderLineLabel(hoveredLine);
        }
    }

    private void renderLineLabel(BusLine line) {
        String labelText = "Linija " + line.lineId;

        Vector2 labelPos = getMouseWorldPosition();
        labelPos.x += 40 * camera.zoom;
        labelPos.y += 40 * camera.zoom;

        float fontScale = Math.max(LABEL_BASE_FONT_SCALE, LABEL_ZOOM_FONT_SCALE * camera.zoom);
        font.getData().setScale(fontScale);

        GlyphLayout layout = new GlyphLayout(font, labelText);
        float textWidth = layout.width;
        float textHeight = layout.height;

        float zoomMultiplier = Math.max(1f, camera.zoom * 15f);
        float paddingX = LABEL_BASE_PADDING_X * zoomMultiplier;
        float paddingY = LABEL_BASE_PADDING_Y * zoomMultiplier;
        float bgWidth = textWidth + paddingX * 2;
        float bgHeight = textHeight + paddingY * 2;
        float cornerRadius = LABEL_CORNER_RADIUS * zoomMultiplier;

        float x = labelPos.x;
        float y = labelPos.y - textHeight * 0.5f - paddingY * 0.5f;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float shadowOffset = LABEL_SHADOW_OFFSET * zoomMultiplier;
        shapeRenderer.setColor(0f, 0f, 0f, 0.2f);

        shapeRenderer.rect(x + cornerRadius + shadowOffset, y - shadowOffset,
            bgWidth - cornerRadius * 2, bgHeight);
        shapeRenderer.rect(x + shadowOffset, y + cornerRadius - shadowOffset,
            bgWidth, bgHeight - cornerRadius * 2);

        shapeRenderer.circle(x + cornerRadius + shadowOffset, y + cornerRadius - shadowOffset, cornerRadius, 16);
        shapeRenderer.circle(x + bgWidth - cornerRadius + shadowOffset, y + cornerRadius - shadowOffset, cornerRadius, 16);
        shapeRenderer.circle(x + cornerRadius + shadowOffset, y + bgHeight - cornerRadius - shadowOffset, cornerRadius, 16);
        shapeRenderer.circle(x + bgWidth - cornerRadius + shadowOffset, y + bgHeight - cornerRadius - shadowOffset, cornerRadius, 16);

        shapeRenderer.setColor(1f, 1f, 1f, 0.97f);

        shapeRenderer.rect(x + cornerRadius, y, bgWidth - cornerRadius * 2, bgHeight);
        shapeRenderer.rect(x, y + cornerRadius, bgWidth, bgHeight - cornerRadius * 2);

        shapeRenderer.circle(x + cornerRadius, y + cornerRadius, cornerRadius, 16);
        shapeRenderer.circle(x + bgWidth - cornerRadius, y + cornerRadius, cornerRadius, 16);
        shapeRenderer.circle(x + cornerRadius, y + bgHeight - cornerRadius, cornerRadius, 16);
        shapeRenderer.circle(x + bgWidth - cornerRadius, y + bgHeight - cornerRadius, cornerRadius, 16);

        shapeRenderer.end();

        spriteBatch.begin();
        font.setColor(0.25f, 0.25f, 0.25f, 1f);
        font.draw(spriteBatch, labelText, x + paddingX, y + bgHeight * 0.5f + textHeight * 0.35f);
        spriteBatch.end();

        // Reset
        font.getData().setScale(1f);
    }

    private Vector2 getMouseWorldPosition() {
        com.badlogic.gdx.math.Vector3 worldCoords = camera.unproject(
            new com.badlogic.gdx.math.Vector3(
                com.badlogic.gdx.Gdx.input.getX(),
                com.badlogic.gdx.Gdx.input.getY(),
                0
            )
        );
        return new Vector2(worldCoords.x, worldCoords.y);
    }
}
