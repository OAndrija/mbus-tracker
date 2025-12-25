package com.mbus.app.systems.input;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.utils.Constants;

import java.util.List;

public class MarkerClickHandler {

    private static final float CLICK_RADIUS = 15f; // pixels, adjust for click tolerance

    private OrthographicCamera camera;
    private List<BusStop> stops;
    private ZoomXY beginTile;

    public MarkerClickHandler(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setStops(List<BusStop> stops) {
        this.stops = stops;
    }

    public void setBeginTile(ZoomXY beginTile) {
        this.beginTile = beginTile;
    }

    /**
     * Check if a click at screen coordinates hits any bus stop marker
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return The clicked BusStop or null if no marker was clicked
     */
    public BusStop checkMarkerClick(int screenX, int screenY) {
        if (stops == null || stops.isEmpty() || beginTile == null) {
            return null;
        }

        // Convert screen coordinates to world coordinates
        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
        float worldX = worldCoords.x;
        float worldY = worldCoords.y;

        // Check each bus stop marker
        for (BusStop stop : stops) {
            Vector2 markerPos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            // Skip markers outside the map bounds
            if (markerPos.x < 0 || markerPos.y < 0 ||
                markerPos.x > Constants.MAP_WIDTH || markerPos.y > Constants.MAP_HEIGHT) {
                continue;
            }

            // Calculate distance from click to marker
            float dx = worldX - markerPos.x;
            float dy = worldY - markerPos.y;
            float distanceSquared = dx * dx + dy * dy;

            // Adjust click radius based on camera zoom (larger radius when zoomed out)
            float effectiveRadius = CLICK_RADIUS * camera.zoom;

            if (distanceSquared <= effectiveRadius * effectiveRadius) {
                return stop; // Found a clicked marker
            }
        }

        return null; // No marker was clicked
    }
}
