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

import java.util.ArrayList;
import java.util.List;

/**
 * Renders animated buses moving smoothly along their routes
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

    // Track previous positions for smooth interpolation
    private static class BusAnimationState {
        Geolocation currentPosition;
        Geolocation targetPosition;
        float interpolationProgress; // 0.0 to 1.0
        int currentStopIndex;
        int targetStopIndex;
        float animationSpeed; // How fast to interpolate (based on schedule)

        BusAnimationState(Geolocation start) {
            this.currentPosition = start;
            this.targetPosition = start;
            this.interpolationProgress = 1.0f; // Start at target
            this.currentStopIndex = 0;
            this.targetStopIndex = 0;
            this.animationSpeed = 1.0f;
        }
    }

    // Store animation state for each active bus (key: line+schedule id)
    private final java.util.Map<String, BusAnimationState> busStates =
        new java.util.HashMap<String, BusAnimationState>();

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
     * Render all active buses for a selected line with smooth animation
     */
    public void renderActiveBuses(BusLine selectedLine, int currentTime,
                                  int dayType, ZoomXY beginTile, float cameraZoom, float delta) {
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
        }

        float zoomScale = calculateZoomScale(cameraZoom);

        // Clean up states for buses that are no longer active
        List<String> activeKeys = new ArrayList<String>();
        for (BusPositionCalculator.ActiveBus activeBus : activeBuses) {
            activeKeys.add(getBusKey(activeBus));
        }
        busStates.keySet().retainAll(activeKeys);

        // Render each active bus
        for (BusPositionCalculator.ActiveBus activeBus : activeBuses) {
            updateAndRenderBus(activeBus, beginTile, zoomScale, delta);
        }
    }

    /**
     * Generate a unique key for each bus (line + schedule combination)
     */
    private String getBusKey(BusPositionCalculator.ActiveBus bus) {
        return bus.line.lineId + "_" + bus.schedule.scheduleId;
    }

    /**
     * Update bus animation state and render it
     */
    private void updateAndRenderBus(BusPositionCalculator.ActiveBus activeBus,
                                    ZoomXY beginTile, float zoomScale, float delta) {
        String busKey = getBusKey(activeBus);
        BusAnimationState state = busStates.get(busKey);

        // Initialize state if this is a new bus
        if (state == null) {
            state = new BusAnimationState(activeBus.currentPosition);
            busStates.put(busKey, state);
        }

        // Check if we need to update target (bus moved to next segment)
        if (activeBus.nextStopIndex != state.targetStopIndex) {
            state.currentPosition = state.targetPosition;
            state.targetPosition = activeBus.currentPosition;
            state.currentStopIndex = state.targetStopIndex;
            state.targetStopIndex = activeBus.nextStopIndex;
            state.interpolationProgress = 0.0f;

            // Calculate animation speed based on time to next stop
            // If we have 2 minutes (120 seconds) to reach next stop, we need to interpolate
            // over that duration at 60fps = 7200 frames
            float secondsToNextStop = activeBus.minutesUntilNextStop * 60f;
            if (secondsToNextStop > 0) {
                state.animationSpeed = 1.0f / secondsToNextStop; // Progress per second
            } else {
                state.animationSpeed = 1.0f; // Move instantly if no time left
            }
        }

        // Update interpolation progress
        if (state.interpolationProgress < 1.0f) {
            state.interpolationProgress += state.animationSpeed * delta;
            if (state.interpolationProgress > 1.0f) {
                state.interpolationProgress = 1.0f;
            }
        }

        // Apply easing for smoother motion
        float easedProgress = easeInOutCubic(state.interpolationProgress);

        // Interpolate current position
        double currentLat = lerp(state.currentPosition.lat, state.targetPosition.lat, easedProgress);
        double currentLng = lerp(state.currentPosition.lng, state.targetPosition.lng, easedProgress);
        Geolocation animatedPosition = new Geolocation(currentLat, currentLng);

        // Get pixel position
        Vector2 pixelPos = MapRasterTiles.getPixelPosition(
            animatedPosition.lat,
            animatedPosition.lng,
            beginTile.x,
            beginTile.y
        );

        // Calculate direction to target
        Vector2 targetPixel = MapRasterTiles.getPixelPosition(
            state.targetPosition.lat,
            state.targetPosition.lng,
            beginTile.x,
            beginTile.y
        );

        // Calculate angle and get appropriate sprite
        float angle = calculateAngle(pixelPos, targetPixel);
        TextureRegion busSprite = getBusSpriteForDirection(angle);

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
     * Linear interpolation
     */
    private double lerp(double start, double end, float t) {
        return start + (end - start) * t;
    }

    /**
     * Easing function for smoother animation
     */
    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4f * t * t * t;
        } else {
            float f = t - 1f;
            return 1f + 4f * f * f * f;
        }
    }

    /**
     * Calculate angle from current position to next position
     * Returns angle in degrees (0 = East, 90 = North, etc.)
     */
    private float calculateAngle(Vector2 from, Vector2 to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;

        // Avoid division by zero
        if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) {
            return 0; // Default to east if no movement
        }

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
     * Calculate zoom-based scale factor
     */
    private float calculateZoomScale(float zoom) {
        float scale = MIN_ZOOM_SCALE + (zoom * ZOOM_SCALE_FACTOR);
        return Math.min(Math.max(scale, MIN_ZOOM_SCALE), MAX_ZOOM_SCALE);
    }

    /**
     * Clear all animation states (useful when switching lines or resetting)
     */
    public void clearStates() {
        busStates.clear();
    }
}
