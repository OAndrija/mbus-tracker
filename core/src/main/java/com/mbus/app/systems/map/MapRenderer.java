package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MapRenderer {

    private final OrthographicCamera camera;
    private final SpriteBatch spriteBatch;
    private Theme theme;
    private BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Color tint = new Color();

    private MapTileLayer tileLayer;
    private LineLayer lineLayer;
    private final LineStyle lineStyle = new LineStyle();
    private MarkerCluster hoveredCluster = null;
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
    private Trip selectedTrip = null;

    private float pulseTime = 0f;

    private static final float PULSE_SPEED = 2.5f;

    // All sizes are in logical screen pixels.
    private static final float STOP_SIZE_FAR = 18f;
    private static final float STOP_SIZE_NEAR = 28f;
    private static final float LINE_CASING = 2f;
    private static final float FADE = 0.6f;
    private static final float HOVERED_LINE_EXTRA = 2f;
    private static final float STOP_HIT_SLOP = 2f;
    private static final float LINE_EDGE = 1f;
    private static final float LINE_HIT_DISTANCE = 6f;

    // Camera zoom at which the far and near sizes apply
    private static final float FAR_ZOOM = 0.3f;
    private static final float NEAR_ZOOM = 0.05f;

    private Set<BusLine> visibleLines = Collections.emptySet();
    private List<Trip> trips = Collections.emptyList();
    private final BusRenderer busRenderer;
    private double currentTimeMinutes = 0;

    public MapRenderer(OrthographicCamera camera, Theme theme) {
        this.camera = camera;
        this.spriteBatch = new SpriteBatch();
        this.theme = theme;
        this.font = theme.map;
        this.busRenderer = new BusRenderer(spriteBatch, theme);
    }

    /** The trips of the current day, ordered by start, and the time in minutes. */
    public void setTrips(List<Trip> trips, double timeMinutes) {
        this.trips = trips;
        this.currentTimeMinutes = timeMinutes;
    }

    /** Takes ownership of the tile layer. */
    public void setMap(MapTileLayer tileLayer, WorldGeometry geometry) {
        this.tileLayer = tileLayer;
        this.geometry = geometry;
        this.lineLayer = new LineLayer(geometry);
        busRenderer.setGeometry(geometry, lineLayer, lineStyle);
        tileLayer.setDark(Palette.isDark());
    }

    /** Call after the colors have changed. */
    public void setTheme(Theme theme) {
        this.theme = theme;
        this.font = theme.map;
        busRenderer.setTheme(theme);
        tileLayer.setDark(Palette.isDark());
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

    /** Shows the way of one trip and highlights its bus. */
    public void setSelectedTrip(Trip trip, boolean showBus) {
        this.selectedTrip = trip;
        busRenderer.setHighlighted(showBus ? trip : null);
    }

    public void setHoveredTrip(Trip trip) {
        busRenderer.setHovered(trip);
    }

    public Vector2 getBusPosition(Trip trip) {
        return busRenderer.getPosition(trip);
    }

    /** Returns the trip of the bus at a screen position, or null. */
    public Trip findBus(int screenX, int screenY) {
        Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
        return busRenderer.findBus(world.x, world.y, getWorldPerPixel());
    }

    /** Returns the line at a screen position, or null. */
    public BusLine findLine(int screenX, int screenY) {
        Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
        return lineLayer.findLine(world.x, world.y, LINE_HIT_DISTANCE * getWorldPerPixel(), visibleLines, lineStyle);
    }

    public void setHoveredCluster(MarkerCluster cluster) {
        this.hoveredCluster = cluster;
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
        lineStyle.set(getWorldPerPixel(), getCloseness());
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
        busRenderer.render(trips, linesWithBuses, currentTimeMinutes, view,
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
        if (busLines == null) return;

        for (BusLine line : busLines) {
            if (!visibleLines.contains(line)) continue;
            if (selectedLine != null && line != selectedLine) continue;
            linesWithBuses.add(line);
        }
    }

    private void renderBusLines() {
        if (visibleLines.isEmpty() && selectedTrip == null) return;

        boolean fade = selectedTrip != null || (hoveredLine != null && visibleLines.contains(hoveredLine));
        lineLayer.begin(camera.combined, lineStyle);

        // Lines as light or dark as the map would be lost without an edge.
        for (BusLine line : busLines) {
            if (!fade && visibleLines.contains(line) && needsEdge(line)) {
                tint.set(Palette.line(line)).lerp(Palette.isDark() ? Color.WHITE : Color.BLACK, 0.35f);
                lineLayer.draw(line, tint, 2 * LINE_EDGE);
            }
        }
        for (BusLine line : busLines) {
            if (!visibleLines.contains(line) || line == hoveredLine || line == selectedLine) continue;
            // Everything else steps back while a line is pointed at.
            tint.set(Palette.line(line));
            // Mixed with the map rather than transparent, as routes of a line overlap.
            if (fade) tint.lerp(Palette.MAP_BACKGROUND, FADE);
            lineLayer.draw(line, tint, 0f);
        }

        if (selectedLine != null && selectedLine != hoveredLine) {
            renderEmphasizedLine(selectedLine);
        }
        if (hoveredLine != null) {
            renderEmphasizedLine(hoveredLine);
        }
        if (selectedTrip != null) {
            lineLayer.draw(selectedTrip.pattern, Palette.SURFACE, HOVERED_LINE_EXTRA + 2 * LINE_CASING);
            lineLayer.draw(selectedTrip.pattern, Palette.line(selectedTrip.pattern.line), HOVERED_LINE_EXTRA);
        }
    }

    private static boolean needsEdge(BusLine line) {
        return Palette.isDark() ? line.isDark() : line.isLight();
    }

    private void renderEmphasizedLine(BusLine line) {
        if (!visibleLines.contains(line)) return;
        lineLayer.draw(line, Palette.SURFACE, HOVERED_LINE_EXTRA + 2 * LINE_CASING);
        lineLayer.draw(line, Palette.line(line), HOVERED_LINE_EXTRA);
    }

    public void setVisibleLines(Set<BusLine> lines) {
        this.visibleLines = lines;
        lineLayer.setLines(lines);
    }

    public void dispose() {
        if (lineLayer != null) lineLayer.dispose();
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
            if (!cluster.isCluster) continue;
            if (cluster.getCommonName() == null) {
                drawCluster(cluster);
            } else if (selectedStop != null && cluster.getStops().contains(selectedStop)) {
                selected = cluster;
            } else if (cluster == hoveredCluster) {
                hovered = cluster;
            } else {
                drawMarker(theme.stopMarker, cluster, getStopSize());
            }
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
            float size = cluster.getCommonName() == null ? getClusterSize(cluster.getCount()) : getStopSize() + 2 * STOP_HIT_SLOP;
            float radius = size / 2f * getWorldPerPixel();
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
