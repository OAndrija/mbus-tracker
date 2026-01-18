package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.BusPositionCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusAnimationRenderer {

    private static final float BUS_SPRITE_SIZE = 56f;
    private static final float MIN_ZOOM_SCALE = 0.8f;
    private static final float MAX_ZOOM_SCALE = 5f;
    private static final float ZOOM_SCALE_FACTOR = 6f;

    // Reduced smoothing for more direct positioning
    private static final float INTERPOLATION_SPEED = 20f; // Very fast catch-up
    private static final float POSITION_THRESHOLD = 0.000001f;

    private final SpriteBatch spriteBatch;
    private TextureRegion busNorth;
    private TextureRegion busNortheast;
    private TextureRegion busEast;
    private TextureRegion busSoutheast;
    private TextureRegion busSouth;
    private TextureRegion busSouthwest;
    private TextureRegion busWest;
    private TextureRegion busNorthwest;

    // Store interpolated positions for each bus
    private Map<String, BusRenderState> busStates = new HashMap<String, BusRenderState>();

    private static class BusRenderState {
        Geolocation currentPosition;
        float currentAngle;

        BusRenderState(Geolocation position, float angle) {
            this.currentPosition = position;
            this.currentAngle = angle;
        }
    }

    public BusAnimationRenderer(SpriteBatch spriteBatch) {
        this.spriteBatch = spriteBatch;
    }

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

    public void renderActiveBuses(BusLine selectedLine, float currentTime,
                                  int dayType, ZoomXY beginTile, float cameraZoom, float delta) {
        if (selectedLine == null || busNorth == null) return;

        // Get current bus positions based on schedule with precise time
        List<BusPositionCalculator.ActiveBusInfo> activeBuses =
            BusPositionCalculator.getActiveBusesAtTime(
                java.util.Collections.singletonList(selectedLine),
                currentTime,
                dayType
            );

        if (activeBuses.isEmpty()) {
            Gdx.app.log("BusAnimationRenderer",
                "No active buses found for line " + selectedLine.lineId +
                    " at time " + currentTime + " (day type: " + dayType + ")");
        }

        float zoomScale = calculateZoomScale(cameraZoom);

        for (BusPositionCalculator.ActiveBusInfo activeBus : activeBuses) {
            renderBusSmooth(activeBus, beginTile, zoomScale, delta);
        }
    }

    private void renderBusSmooth(BusPositionCalculator.ActiveBusInfo activeBus,
                                 ZoomXY beginTile, float zoomScale, float delta) {
        // Calculate target position
        Geolocation targetPosition = calculateTargetPosition(activeBus);
        if (targetPosition == null) return;

        // Calculate target direction
        Geolocation directionTarget = calculateDirectionTarget(activeBus);
        Vector2 targetPixel = MapRasterTiles.getPixelPosition(
            directionTarget.lat, directionTarget.lng, beginTile.x, beginTile.y);
        Vector2 posPixel = MapRasterTiles.getPixelPosition(
            targetPosition.lat, targetPosition.lng, beginTile.x, beginTile.y);
        float targetAngle = calculateAngle(posPixel, targetPixel);

        // Get or create render state for this bus
        String busKey = getBusKey(activeBus);
        BusRenderState state = busStates.get(busKey);

        if (state == null) {
            // First time seeing this bus - initialize at target position
            state = new BusRenderState(targetPosition, targetAngle);
            busStates.put(busKey, state);
        }

        // Interpolate position using delta time for frame-rate independent movement
        float lerpFactor = Math.min(1.0f, INTERPOLATION_SPEED * delta);

        double latDiff = targetPosition.lat - state.currentPosition.lat;
        double lngDiff = targetPosition.lng - state.currentPosition.lng;

        // Only interpolate if there's meaningful movement
        if (Math.abs(latDiff) > POSITION_THRESHOLD || Math.abs(lngDiff) > POSITION_THRESHOLD) {
            state.currentPosition = new Geolocation(
                state.currentPosition.lat + latDiff * lerpFactor,
                state.currentPosition.lng + lngDiff * lerpFactor
            );
        } else {
            state.currentPosition = targetPosition;
        }

        // Interpolate angle (handle wrapping)
        float angleDiff = normalizeAngle(targetAngle - state.currentAngle);
        state.currentAngle = normalizeAngle(state.currentAngle + angleDiff * lerpFactor);

        // Render at interpolated position
        Vector2 pixelPos = MapRasterTiles.getPixelPosition(
            state.currentPosition.lat,
            state.currentPosition.lng,
            beginTile.x,
            beginTile.y
        );

        TextureRegion busSprite = getBusSpriteForDirection(state.currentAngle);

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

    private Geolocation calculateTargetPosition(BusPositionCalculator.ActiveBusInfo activeBus) {
        if (activeBus.isWaitingAtStop) {
            // Bus is at a stop
            List<BusStop> stops = activeBus.line.getStops();
            if (activeBus.currentStopIndex >= 0 && activeBus.currentStopIndex < stops.size()) {
                return stops.get(activeBus.currentStopIndex).geo;
            } else {
                return null;
            }
        } else {
            // Bus is traveling between stops
            return calculatePositionAlongPath(
                activeBus.line,
                activeBus.currentStopIndex,
                activeBus.nextStopIndex,
                activeBus.segmentProgress
            );
        }
    }

    private Geolocation calculateDirectionTarget(BusPositionCalculator.ActiveBusInfo activeBus) {
        List<BusStop> stops = activeBus.line.getStops();

        if (activeBus.isWaitingAtStop) {
            // When waiting, use next stop for direction
            if (activeBus.nextStopIndex >= 0 && activeBus.nextStopIndex < stops.size()) {
                return stops.get(activeBus.nextStopIndex).geo;
            }
        } else {
            // When traveling, use next stop
            if (activeBus.nextStopIndex >= 0 && activeBus.nextStopIndex < stops.size()) {
                return stops.get(activeBus.nextStopIndex).geo;
            }
        }

        // Fallback to current position
        return calculateTargetPosition(activeBus);
    }

    private String getBusKey(BusPositionCalculator.ActiveBusInfo activeBus) {
        return activeBus.line.lineId + "_" +
            activeBus.schedule.scheduleId + "_" +
            activeBus.schedule.departureTime;
    }

    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private Geolocation calculatePositionAlongPath(BusLine line,
                                                   int currentStopIndex,
                                                   int nextStopIndex,
                                                   float segmentProgress) {
        List<BusStop> stops = line.getStops();
        List<Geolocation> path = line.getPath();

        if (stops.isEmpty() || path.isEmpty()) return null;

        Geolocation startGeo;
        Geolocation endGeo;

        if (currentStopIndex == -1) {
            // Before first stop - use path start to first stop
            if (path.isEmpty()) return null;
            startGeo = path.get(0);
            if (nextStopIndex >= 0 && nextStopIndex < stops.size()) {
                endGeo = stops.get(nextStopIndex).geo;
            } else {
                return startGeo;
            }
        } else {
            // Between stops
            if (currentStopIndex >= stops.size() || nextStopIndex >= stops.size()) return null;
            if (currentStopIndex < 0 || nextStopIndex < 0) return null;

            startGeo = stops.get(currentStopIndex).geo;
            endGeo = stops.get(nextStopIndex).geo;
        }

        int pathStartIdx = findNearestPathIndex(path, startGeo);
        int pathEndIdx = findNearestPathIndex(path, endGeo);

        if (pathStartIdx == -1 || pathEndIdx == -1) {
            // Fallback to direct interpolation
            double lat = startGeo.lat + (endGeo.lat - startGeo.lat) * segmentProgress;
            double lng = startGeo.lng + (endGeo.lng - startGeo.lng) * segmentProgress;
            return new Geolocation(lat, lng);
        }

        if (pathStartIdx == pathEndIdx) {
            return path.get(pathStartIdx);
        }

        if (pathStartIdx > pathEndIdx) {
            int temp = pathStartIdx;
            pathStartIdx = pathEndIdx;
            pathEndIdx = temp;
        }

        return interpolateAlongPath(path, pathStartIdx, pathEndIdx, segmentProgress);
    }

    private int findNearestPathIndex(List<Geolocation> path, Geolocation target) {
        if (path.isEmpty()) return -1;

        int nearestIdx = 0;
        double minDistance = distance(path.get(0), target);

        for (int i = 1; i < path.size(); i++) {
            double dist = distance(path.get(i), target);
            if (dist < minDistance) {
                minDistance = dist;
                nearestIdx = i;
            }
        }

        return nearestIdx;
    }

    private double distance(Geolocation a, Geolocation b) {
        double dLat = a.lat - b.lat;
        double dLng = a.lng - b.lng;
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    private Geolocation interpolateAlongPath(List<Geolocation> path,
                                             int startIdx, int endIdx,
                                             float progress) {
        if (startIdx == endIdx) {
            return path.get(startIdx);
        }

        float totalDistance = 0f;
        List<Float> segmentDistances = new ArrayList<Float>();

        for (int i = startIdx; i < endIdx; i++) {
            float segDist = (float) distance(path.get(i), path.get(i + 1));
            segmentDistances.add(segDist);
            totalDistance += segDist;
        }

        if (totalDistance == 0) {
            return path.get(startIdx);
        }

        float targetDistance = totalDistance * progress;
        float accumulatedDistance = 0f;

        for (int i = 0; i < segmentDistances.size(); i++) {
            float segDist = segmentDistances.get(i);

            if (accumulatedDistance + segDist >= targetDistance) {
                float localProgress = (targetDistance - accumulatedDistance) / segDist;

                Geolocation p1 = path.get(startIdx + i);
                Geolocation p2 = path.get(startIdx + i + 1);

                double lat = p1.lat + (p2.lat - p1.lat) * localProgress;
                double lng = p1.lng + (p2.lng - p1.lng) * localProgress;

                return new Geolocation(lat, lng);
            }

            accumulatedDistance += segDist;
        }

        return path.get(endIdx);
    }

    private float calculateAngle(Vector2 from, Vector2 to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;

        if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) {
            return 0;
        }

        float angleRad = (float) Math.atan2(dy, dx);
        float angleDeg = (float) Math.toDegrees(angleRad);

        if (angleDeg < 0) {
            angleDeg += 360;
        }

        return angleDeg;
    }

    private TextureRegion getBusSpriteForDirection(float angleDeg) {
        angleDeg = angleDeg % 360;
        if (angleDeg < 0) angleDeg += 360;

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
        } else {
            return busSoutheast;
        }
    }

    private float calculateZoomScale(float zoom) {
        float scale = MIN_ZOOM_SCALE + (zoom * ZOOM_SCALE_FACTOR);
        return Math.min(Math.max(scale, MIN_ZOOM_SCALE), MAX_ZOOM_SCALE);
    }

    public void clearStates() {
        busStates.clear();
    }
}
