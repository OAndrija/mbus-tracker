package com.mbus.app.systems.input;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.map.MapRasterTiles;

import java.util.List;
import java.util.Set;

public class BusLineClickHandler {

    // Click tolerance in pixels - how close to the line you need to click
    private static final float CLICK_TOLERANCE = 15f;

    private OrthographicCamera camera;
    private List<BusLine> busLines;
    private Set<Integer> visibleLineIds;
    private ZoomXY beginTile;

    public BusLineClickHandler(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setBusLines(List<BusLine> busLines) {
        this.busLines = busLines;
    }

    public void setVisibleLineIds(Set<Integer> visibleLineIds) {
        this.visibleLineIds = visibleLineIds;
    }

    public void setBeginTile(ZoomXY beginTile) {
        this.beginTile = beginTile;
    }

    /**
     * Check if a click at screen coordinates hits any visible bus line
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return The clicked BusLine or null if no line was clicked
     */
    public BusLine checkLineClick(int screenX, int screenY) {
        if (busLines == null || busLines.isEmpty() || beginTile == null ||
            visibleLineIds == null || visibleLineIds.isEmpty()) {
            return null;
        }

        // Convert screen coordinates to world coordinates
        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
        float worldX = worldCoords.x;
        float worldY = worldCoords.y;

        // Scale tolerance with zoom
        float tolerance = CLICK_TOLERANCE * camera.zoom;

        // Check each visible bus line
        BusLine closestLine = null;
        float closestDistance = Float.MAX_VALUE;

        for (BusLine line : busLines) {
            // Skip if line is not visible
            if (!visibleLineIds.contains(line.lineId)) {
                continue;
            }

            List<Geolocation> path = line.getPath();
            if (path.size() < 2) continue;

            // Check each line segment
            for (int i = 0; i < path.size() - 1; i++) {
                Geolocation point1 = path.get(i);
                Geolocation point2 = path.get(i + 1);

                Vector2 pos1 = MapRasterTiles.getPixelPosition(
                    point1.lat, point1.lng, beginTile.x, beginTile.y
                );
                Vector2 pos2 = MapRasterTiles.getPixelPosition(
                    point2.lat, point2.lng, beginTile.x, beginTile.y
                );

                // Calculate distance from point to line segment
                float distance = distanceToLineSegment(worldX, worldY, pos1.x, pos1.y, pos2.x, pos2.y);

                if (distance <= tolerance && distance < closestDistance) {
                    closestLine = line;
                    closestDistance = distance;
                }
            }
        }

        return closestLine;
    }

    /**
     * Calculate the shortest distance from a point to a line segment
     */
    private float distanceToLineSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;

        // If the line segment is actually a point
        if (dx == 0 && dy == 0) {
            return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        // Calculate the parameter t that represents the projection of point P onto the line
        float t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);

        // Clamp t to the segment [0, 1]
        t = Math.max(0, Math.min(1, t));

        // Find the closest point on the segment
        float closestX = x1 + t * dx;
        float closestY = y1 + t * dy;

        // Return distance from point to closest point on segment
        float distX = px - closestX;
        float distY = py - closestY;
        return (float) Math.sqrt(distX * distX + distY * distY);
    }
}
