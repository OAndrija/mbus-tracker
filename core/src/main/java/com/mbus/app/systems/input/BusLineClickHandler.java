package com.mbus.app.systems.input;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.mbus.app.model.BusLine;
import com.mbus.app.systems.map.WorldGeometry;

import java.util.List;
import java.util.Set;

public class BusLineClickHandler {

    private static final float CLICK_TOLERANCE = 15f;

    private final OrthographicCamera camera;
    private List<BusLine> busLines;
    private Set<Integer> visibleLineIds;
    private WorldGeometry geometry;

    public BusLineClickHandler(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setBusLines(List<BusLine> busLines) {
        this.busLines = busLines;
    }

    public void setVisibleLineIds(Set<Integer> visibleLineIds) {
        this.visibleLineIds = visibleLineIds;
    }

    public void setGeometry(WorldGeometry geometry) {
        this.geometry = geometry;
    }

    public BusLine checkLineClick(int screenX, int screenY) {
        if (busLines == null || busLines.isEmpty() || geometry == null ||
            visibleLineIds == null || visibleLineIds.isEmpty()) {
            return null;
        }

        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
        float worldX = worldCoords.x;
        float worldY = worldCoords.y;

        float tolerance = CLICK_TOLERANCE * camera.zoom;

        BusLine closestLine = null;
        float closestDistance = Float.MAX_VALUE;

        for (BusLine line : busLines) {
            if (!visibleLineIds.contains(line.lineId)) {
                continue;
            }

            WorldGeometry.LinePath path = geometry.getLine(line);
            if (path.size() < 2 || !isNear(path, worldX, worldY, tolerance)) continue;

            for (int i = 0; i < path.size() - 1; i++) {
                float distance = distanceToLineSegment(worldX, worldY,
                    path.x[i], path.y[i], path.x[i + 1], path.y[i + 1]);

                if (distance <= tolerance && distance < closestDistance) {
                    closestLine = line;
                    closestDistance = distance;
                }
            }
        }

        return closestLine;
    }

    private boolean isNear(WorldGeometry.LinePath path, float x, float y, float tolerance) {
        return x >= path.bounds.x - tolerance && x <= path.bounds.x + path.bounds.width + tolerance
            && y >= path.bounds.y - tolerance && y <= path.bounds.y + path.bounds.height + tolerance;
    }

    private float distanceToLineSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        float t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);

        t = Math.max(0, Math.min(1, t));

        float closestX = x1 + t * dx;
        float closestY = y1 + t * dy;

        float distX = px - closestX;
        float distY = py - closestY;
        return (float) Math.sqrt(distX * distX + distY * distY);
    }
}
