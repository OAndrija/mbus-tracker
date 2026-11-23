package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.BusLineColors;
import com.mbus.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MapRenderer {

    private final OrthographicCamera camera;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch spriteBatch;
    private final Theme theme;
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Color tint = new Color();

    private MapTileLayer tileLayer;
    private WorldGeometry geometry;
    private List<BusStop> allStops;
    private List<BusStop> filteredStops;
    private List<BusLine> busLines;

    private List<MarkerCluster> clusters = Collections.emptyList();
    private final List<BusLine> linesWithBuses = new ArrayList<>();
    private final Rectangle view = new Rectangle();

    private boolean showMarkers = true;

    private BusStop hoveredStop = null;
    private BusStop selectedStop = null;

    private BusLine hoveredLine = null;
    private BusLine selectedLine = null;

    private float pulseTime = 0f;

    private static final float PULSE_SPEED = 2.5f;

    // All sizes are in logical screen pixels.
    private static final float STOP_SIZE_FAR = 18f;
    private static final float STOP_SIZE_NEAR = 28f;
    private static final float LINE_WIDTH_FAR = 3f;
    private static final float LINE_WIDTH_NEAR = 6f;
    private static final float LINE_CASING = 2f;
    private static final float HOVERED_LINE_EXTRA = 2f;
    private static final float STOP_HIT_SLOP = 2f;
    private static final float LINE_ALPHA = 0.9f;

    // Camera zoom at which the far and near sizes apply
    private static final float FAR_ZOOM = 0.3f;
    private static final float NEAR_ZOOM = 0.05f;

    private Set<Integer> visibleLineIds;
    private final BusRenderer busRenderer;
    private double currentTimeMinutes = 0;
    private int currentDayType = 0;

    public MapRenderer(OrthographicCamera camera, Theme theme) {
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
        this.theme = theme;
        this.font = theme.map;
        this.busRenderer = new BusRenderer(spriteBatch, theme);
    }

    public void setCurrentTime(double timeMinutes, int dayType) {
        this.currentTimeMinutes = timeMinutes;
        this.currentDayType = dayType;
    }

    /** Takes ownership of the tile layer. */
    public void setMap(MapTileLayer tileLayer, WorldGeometry geometry) {
        this.tileLayer = tileLayer;
        this.geometry = geometry;
        busRenderer.setGeometry(geometry);
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
        updateView();
        tileLayer.render(spriteBatch);

        if (busLines != null && !busLines.isEmpty()) {
            renderBusLines();
        }

        pulseTime += delta;
        if (showMarkers) {
            renderMarkers(delta);
        } else {
            clusters = Collections.emptyList();
        }

        updateLinesWithBuses();
        spriteBatch.setProjectionMatrix(camera.combined);
        busRenderer.render(linesWithBuses, currentTimeMinutes, currentDayType, view,
            camera.zoom, getWorldPerPixel(), getCloseness(), delta);
    }

    private void updateView() {
        float width = camera.viewportWidth * camera.zoom;
        float height = camera.viewportHeight * camera.zoom;
        view.set(camera.position.x - width / 2f, camera.position.y - height / 2f, width, height);
    }

    private float getWorldPerPixel() {
        return camera.viewportWidth * camera.zoom / Gdx.graphics.getWidth();
    }

    /** 0 when zoomed out, 1 when zoomed in. */
    private float getCloseness() {
        return MathUtils.clamp((FAR_ZOOM - camera.zoom) / (FAR_ZOOM - NEAR_ZOOM), 0f, 1f);
    }

    /** A selected line shows only its own buses, otherwise all visible lines show theirs. */
    private void updateLinesWithBuses() {
        linesWithBuses.clear();
        if (busLines == null || visibleLineIds == null) return;

        for (BusLine line : busLines) {
            if (!visibleLineIds.contains(line.lineId)) continue;
            if (selectedLine != null && line != selectedLine) continue;
            linesWithBuses.add(line);
        }
    }

    private void renderBusLines() {
        if (visibleLineIds == null || visibleLineIds.isEmpty()) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float pixel = getWorldPerPixel();
        float width = MathUtils.lerp(LINE_WIDTH_FAR, LINE_WIDTH_NEAR, getCloseness()) * pixel;

        for (BusLine line : busLines) {
            if (!visibleLineIds.contains(line.lineId)) continue;
            if (line == hoveredLine || line == selectedLine) continue;
            renderLine(line, width, BusLineColors.getColor(line.lineId), LINE_ALPHA);
        }

        if (selectedLine != null && selectedLine != hoveredLine) {
            renderEmphasizedLine(selectedLine, width, pixel);
        }
        if (hoveredLine != null) {
            renderEmphasizedLine(hoveredLine, width, pixel);
        }

        shapeRenderer.end();
    }

    private void renderEmphasizedLine(BusLine line, float width, float pixel) {
        if (!visibleLineIds.contains(line.lineId)) return;
        float wide = width + HOVERED_LINE_EXTRA * pixel;
        renderLine(line, wide + 2 * LINE_CASING * pixel, Color.WHITE, 1f);
        renderLine(line, wide, BusLineColors.getColor(line.lineId), 1f);
    }

    private void renderLine(BusLine line, float lineWidth, Color color, float alpha) {
        WorldGeometry.LinePath path = geometry.getLine(line);
        if (path.size() < 2) return;

        float pad = lineWidth;
        float left = view.x - pad, right = view.x + view.width + pad;
        float bottom = view.y - pad, top = view.y + view.height + pad;
        Rectangle bounds = path.bounds;
        if (bounds.x > right || bounds.x + bounds.width < left
            || bounds.y > top || bounds.y + bounds.height < bottom) return;

        float worldPerPixel = view.width / Gdx.graphics.getBackBufferWidth();
        float minStep = worldPerPixel * 1.5f;
        int segments = MathUtils.clamp((int) (lineWidth / worldPerPixel), 6, 16);

        shapeRenderer.setColor(color.r, color.g, color.b, alpha);
        float x1 = path.x[0], y1 = path.y[0];
        int last = path.size() - 1;

        for (int i = 1; i <= last; i++) {
            float x2 = path.x[i], y2 = path.y[i];

            // Points closer than a pixel or so make no visible difference.
            if (i < last && Math.abs(x2 - x1) < minStep && Math.abs(y2 - y1) < minStep) continue;

            boolean outside = (x1 < left && x2 < left) || (x1 > right && x2 > right)
                || (y1 < bottom && y2 < bottom) || (y1 > top && y2 > top);
            if (!outside) {
                shapeRenderer.rectLine(x1, y1, x2, y2, lineWidth);
                shapeRenderer.circle(x1, y1, lineWidth / 2, segments);
                if (i == last) {
                    shapeRenderer.circle(x2, y2, lineWidth / 2, segments);
                }
            }
            x1 = x2;
            y1 = y2;
        }
    }

    public void setVisibleLineIds(Set<Integer> lineIds) {
        this.visibleLineIds = lineIds;
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        if (tileLayer != null) tileLayer.dispose();
    }

    public List<BusStop> getStops() {
        return allStops;
    }

    private void renderMarkers(float delta) {
        if (filteredStops == null || filteredStops.isEmpty()) {
            clusters = Collections.emptyList();
            return;
        }

        clusters = MarkerClusterer.clusterMarkers(
            filteredStops, geometry, camera.zoom,
            Constants.MAP_WIDTH, Constants.MAP_HEIGHT,
            delta
        );

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        MarkerCluster hovered = null;
        MarkerCluster selected = null;
        for (MarkerCluster cluster : clusters) {
            BusStop stop = cluster.getSingleStop();
            if (stop == null || cluster.isDying) continue;
            if (stop == selectedStop) selected = cluster;
            else if (stop == hoveredStop) hovered = cluster;
            else drawMarker(theme.stopMarker, cluster, getStopSize());
        }

        for (MarkerCluster cluster : clusters) {
            if (cluster.isCluster) drawCluster(cluster);
        }

        if (hovered != null) {
            drawMarker(theme.stopMarkerHovered, hovered, getStopSize() * 1.2f);
        }
        if (selected != null) {
            float breathe = MathUtils.sin(pulseTime * PULSE_SPEED) * 0.5f + 0.5f;
            drawMarker(theme.stopMarkerSelected, selected, getStopSize() * (1.4f + breathe * 0.1f));
        }

        spriteBatch.setColor(Color.WHITE);
        spriteBatch.end();
    }

    /** Diameter of a stop marker in screen pixels. */
    private float getStopSize() {
        return MathUtils.lerp(STOP_SIZE_FAR, STOP_SIZE_NEAR, getCloseness());
    }

    /** Diameter of a cluster in screen pixels. */
    private static float getClusterSize(int count) {
        if (count <= 3) return 22f;
        if (count <= 9) return 25f;
        if (count <= 19) return 28f;
        return 32f;
    }

    private void drawMarker(Texture texture, MarkerCluster cluster, float pixels) {
        float size = pixels * getWorldPerPixel() * cluster.currentScale / Theme.MARKER_DISC;
        Vector2 pos = cluster.getPosition();
        spriteBatch.setColor(1f, 1f, 1f, cluster.alpha);
        spriteBatch.draw(texture, pos.x - size / 2f, pos.y - size / 2f, size, size);
    }

    private void drawCluster(MarkerCluster cluster) {
        float pixels = getClusterSize(cluster.getCount());
        drawMarker(theme.clusterMarker, cluster, pixels);

        float scale = 0.34f * pixels * getWorldPerPixel() * cluster.currentScale / font.getCapHeight();
        Vector2 pos = cluster.getPosition();
        font.getData().setScale(scale);
        tint.set(Palette.INK.r, Palette.INK.g, Palette.INK.b, cluster.alpha);
        glyphLayout.setText(font, String.valueOf(cluster.getCount()), tint, 0, Align.left, false);
        font.draw(spriteBatch, glyphLayout, pos.x - glyphLayout.width / 2f, pos.y + font.getCapHeight() / 2f);
        font.getData().setScale(1f);
    }

    /** Returns the stop cluster at a screen position, or null. */
    public MarkerCluster findCluster(int screenX, int screenY) {
        Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
        for (MarkerCluster cluster : clusters) {
            if (!cluster.isCluster || cluster.isDying) continue;
            float radius = getClusterSize(cluster.getCount()) / 2f * getWorldPerPixel();
            if (cluster.getPosition().dst2(world.x, world.y) <= radius * radius) {
                return cluster;
            }
        }
        return null;
    }

    /** Returns the stop whose own marker is at a screen position, or null. */
    public BusStop findStop(int screenX, int screenY) {
        Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
        float radius = (getStopSize() / 2f + STOP_HIT_SLOP) * getWorldPerPixel();
        float closest = radius * radius;
        BusStop found = null;
        for (MarkerCluster cluster : clusters) {
            if (cluster.isCluster || cluster.isDying) continue;
            float distance = cluster.getPosition().dst2(world.x, world.y);
            if (distance <= closest) {
                closest = distance;
                found = cluster.getSingleStop();
            }
        }
        return found;
    }
}
