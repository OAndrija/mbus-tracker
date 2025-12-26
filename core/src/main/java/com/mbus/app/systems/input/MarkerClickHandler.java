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

    // Base marker size should match the rendered size (32px base size)
    private static final float BASE_MARKER_SIZE = 32f;

    // Click tolerance multiplier - makes the clickable area slightly larger than visual
    private static final float CLICK_TOLERANCE = 1.2f;

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

        // Calculate the effective click radius in world coordinates
        // The marker size stays constant in world space, so we use the base size
        float clickRadius = (BASE_MARKER_SIZE / 2f) * CLICK_TOLERANCE;

        // Check each bus stop marker
        // To handle overlapping markers, we'll find the closest one within range
        BusStop closestStop = null;
        float closestDistance = Float.MAX_VALUE;

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

            // Calculate distance from click to marker center
            float dx = worldX - markerPos.x;
            float dy = worldY - markerPos.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            // Check if click is within the marker's clickable area
            if (distance <= clickRadius && distance < closestDistance) {
                closestStop = stop;
                closestDistance = distance;
            }
        }

        return closestStop;
    }
}
