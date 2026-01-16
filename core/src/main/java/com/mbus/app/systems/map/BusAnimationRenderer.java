package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.BusPositionCalculator;

import java.util.List;

/**
 * Renders animated buses moving along their routes
 */
public class BusAnimationRenderer {

    private static final float BUS_SPRITE_SIZE = 48f;
    private static final float MIN_ZOOM_SCALE = 0.8f;
    private static final float MAX_ZOOM_SCALE = 3.5f;
    private static final float ZOOM_SCALE_FACTOR = 5f;

    private final SpriteBatch spriteBatch;
    private TextureRegion busNorth;
    private TextureRegion busNortheast;
    private TextureRegion busEast;
    private TextureRegion busSoutheast;
    private TextureRegion busSouth;
    private TextureRegion busSouthwest;
    private TextureRegion busWest;
    private TextureRegion busNorthwest;

    public BusAnimationRenderer(SpriteBatch spriteBatch) {
        this.spriteBatch = spriteBatch;
    }

    /**
     * Load bus sprite regions from texture atlas
     */
    public void loadBusSprites(TextureRegion north, TextureRegion northeast,
                               TextureRegion east, TextureRegion southeast,
                               TextureRegion south, TextureRegion southwest,
                               TextureRegion west, TextureRegion northwest) {
        this.busNorth = north;
        this.busNortheast = northeast;
        this.busEast = east;
        this.busSoutheast = southeast;
        this.busSouth = south;
        this.busSouthwest = southwest;
        this.busWest = west;
        this.busNorthwest = northwest;
    }

    /**
     * Render all active buses for a selected line
     */
    public void renderActiveBuses(BusLine selectedLine, int currentTime,
                                  int dayType, ZoomXY beginTile, float cameraZoom) {
        if (selectedLine == null || busNorth == null) return;

        List<BusPositionCalculator.ActiveBus> activeBuses =
            BusPositionCalculator.getActiveBuses(
                java.util.Collections.singletonList(selectedLine),
                currentTime,
                dayType
            );

        // Debug logging
        if (activeBuses.isEmpty()) {
            Gdx.app.log("BusAnimationRenderer",
                "No active buses found for line " + selectedLine.lineId +
                    " at time " + currentTime + " (day type: " + dayType + ")");
        } else {
            Gdx.app.log("BusAnimationRenderer",
                "Rendering " + activeBuses.size() + " active buses for line " + selectedLine.lineId);
        }

        float zoomScale = calculateZoomScale(cameraZoom);

        for (BusPositionCalculator.ActiveBus activeBus : activeBuses) {
            renderBus(activeBus, beginTile, zoomScale);
        }
    }

    /**
     * Render a single bus at its current position with correct direction
     */
    private void renderBus(BusPositionCalculator.ActiveBus activeBus,
                           ZoomXY beginTile, float zoomScale) {
        // Get current position in pixel coordinates
        Vector2 pixelPos = MapRasterTiles.getPixelPosition(
            activeBus.currentPosition.lat,
            activeBus.currentPosition.lng,
            beginTile.x,
            beginTile.y
        );

        Gdx.app.log("BusAnimationRenderer",
            "Rendering bus at (" + pixelPos.x + ", " + pixelPos.y + ") " +
                "geo: (" + activeBus.currentPosition.lat + ", " + activeBus.currentPosition.lng + ") " +
                "progress: " + activeBus.progressAlongRoute + " " +
                "nextStop: " + activeBus.nextStopIndex);

        // Calculate direction to next stop
        BusSchedule.StopTime nextStopTime =
            activeBus.schedule.getStopTimes().get(activeBus.nextStopIndex);
        Geolocation nextStopGeo = findStopPosition(activeBus.line, nextStopTime.stopId);

        if (nextStopGeo == null) {
            Gdx.app.error("BusAnimationRenderer",
                "Could not find position for stop ID: " + nextStopTime.stopId);
            return;
        }

        Vector2 nextStopPixel = MapRasterTiles.getPixelPosition(
            nextStopGeo.lat,
            nextStopGeo.lng,
            beginTile.x,
            beginTile.y
        );

        // Calculate angle and get appropriate sprite
        float angle = calculateAngle(pixelPos, nextStopPixel);
        TextureRegion busSprite = getBusSpriteForDirection(angle);

        Gdx.app.log("BusAnimationRenderer",
            "Bus direction angle: " + angle + " degrees");

        // Draw the bus
        float size = BUS_SPRITE_SIZE * zoomScale;
        float halfSize = size / 2f;

        spriteBatch.draw(
            busSprite,
            pixelPos.x - halfSize,
            pixelPos.y - halfSize,
            size,
            size
        );
    }

    /**
     * Calculate angle from current position to next position
     * Returns angle in degrees (0 = East, 90 = North, etc.)
     */
    private float calculateAngle(Vector2 from, Vector2 to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;

        // atan2 returns angle in radians, convert to degrees
        float angleRad = (float) Math.atan2(dy, dx);
        float angleDeg = (float) Math.toDegrees(angleRad);

        // Normalize to 0-360
        if (angleDeg < 0) {
            angleDeg += 360;
        }

        return angleDeg;
    }

    /**
     * Get the appropriate bus sprite based on direction angle
     * Angles: 0=East, 45=NE, 90=North, 135=NW, 180=West, 225=SW, 270=South, 315=SE
     */
    private TextureRegion getBusSpriteForDirection(float angleDeg) {
        // Normalize angle to 0-360
        angleDeg = angleDeg % 360;
        if (angleDeg < 0) angleDeg += 360;

        // Determine which sprite to use based on 8 directions (45-degree segments)
        if (angleDeg >= 337.5f || angleDeg < 22.5f) {
            return busEast;
        } else if (angleDeg >= 22.5f && angleDeg < 67.5f) {
            return busNortheast;
        } else if (angleDeg >= 67.5f && angleDeg < 112.5f) {
            return busNorth;
        } else if (angleDeg >= 112.5f && angleDeg < 157.5f) {
            return busNorthwest;
        } else if (angleDeg >= 157.5f && angleDeg < 202.5f) {
            return busWest;
        } else if (angleDeg >= 202.5f && angleDeg < 247.5f) {
            return busSouthwest;
        } else if (angleDeg >= 247.5f && angleDeg < 292.5f) {
            return busSouth;
        } else { // 292.5f to 337.5f
            return busSoutheast;
        }
    }

    /**
     * Find the geographic position of a stop on a line
     */
    private Geolocation findStopPosition(BusLine line, int stopId) {
        for (int i = 0; i < line.getStops().size(); i++) {
            if (line.getStops().get(i).idAvpost == stopId) {
                return line.getStops().get(i).geo;
            }
        }
        return null;
    }

    /**
     * Calculate zoom-based scale factor
     */
    private float calculateZoomScale(float zoom) {
        float scale = MIN_ZOOM_SCALE + (zoom * ZOOM_SCALE_FACTOR);
        return Math.min(Math.max(scale, MIN_ZOOM_SCALE), MAX_ZOOM_SCALE);
    }
}
