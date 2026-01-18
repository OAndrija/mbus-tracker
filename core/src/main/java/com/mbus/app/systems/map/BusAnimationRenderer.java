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
import java.util.List;

public class BusAnimationRenderer {

    private static final float BUS_SPRITE_SIZE = 56f;
    private static final float MIN_ZOOM_SCALE = 0.8f;
    private static final float MAX_ZOOM_SCALE = 5f;
    private static final float ZOOM_SCALE_FACTOR = 6f;

    private final SpriteBatch spriteBatch;
    private TextureRegion busNorth;
    private TextureRegion busNortheast;
    private TextureRegion busEast;
    private TextureRegion busSoutheast;
    private TextureRegion busSouth;
    private TextureRegion busSouthwest;
    private TextureRegion busWest;
    private TextureRegion busNorthwest;

    private static class BusAnimationState {
        Geolocation currentPosition;
        Geolocation targetPosition;
        float interpolationProgress;
        int currentStopIndex;
        int targetStopIndex;
        float animationSpeed;
        int currentPathStartIdx;
        int currentPathEndIdx;

        BusAnimationState(Geolocation start) {
            this.currentPosition = start;
            this.targetPosition = start;
            this.interpolationProgress = 1.0f;
            this.currentStopIndex = 0;
            this.targetStopIndex = 0;
            this.animationSpeed = 1.0f;
            this.currentPathStartIdx = 0;
            this.currentPathEndIdx = 0;
        }
    }

    private final java.util.Map<String, BusAnimationState> busStates =
        new java.util.HashMap<String, BusAnimationState>();

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

    public void renderActiveBuses(BusLine selectedLine, int currentTime,
                                  int dayType, ZoomXY beginTile, float cameraZoom, float delta) {
        if (selectedLine == null || busNorth == null) return;

        List<BusPositionCalculator.ActiveBusInfo> activeBuses =
            BusPositionCalculator.getActiveBuses(
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

        List<String> activeKeys = new ArrayList<String>();
        for (BusPositionCalculator.ActiveBusInfo activeBus : activeBuses) {
            activeKeys.add(getBusKey(activeBus));
        }
        busStates.keySet().retainAll(activeKeys);

        for (BusPositionCalculator.ActiveBusInfo activeBus : activeBuses) {
            updateAndRenderBus(activeBus, beginTile, zoomScale, delta);
        }
    }

    private String getBusKey(BusPositionCalculator.ActiveBusInfo bus) {
        return bus.line.lineId + "_" + bus.schedule.scheduleId;
    }

    private void updateAndRenderBus(BusPositionCalculator.ActiveBusInfo activeBus,
                                    ZoomXY beginTile, float zoomScale, float delta) {
        String busKey = getBusKey(activeBus);
        BusAnimationState state = busStates.get(busKey);

        Geolocation targetPos = calculatePositionAlongPath(
            activeBus.line,
            activeBus.currentStopIndex,
            activeBus.nextStopIndex,
            activeBus.segmentProgress
        );

        if (targetPos == null) return;

        if (state == null) {
            state = new BusAnimationState(targetPos);
            busStates.put(busKey, state);
        }

        if (activeBus.nextStopIndex != state.targetStopIndex) {
            state.currentPosition = state.targetPosition;
            state.targetPosition = targetPos;
            state.currentStopIndex = state.targetStopIndex;
            state.targetStopIndex = activeBus.nextStopIndex;
            state.interpolationProgress = 0.0f;

            float secondsToNextStop = activeBus.minutesUntilNextStop * 60f;
            float animationDuration = Math.max(secondsToNextStop * 0.6f, 3f);

            if (animationDuration > 0) {
                state.animationSpeed = 1.0f / animationDuration;
            } else {
                state.animationSpeed = 1.0f;
            }
        }

        if (state.interpolationProgress < 1.0f) {
            state.interpolationProgress += state.animationSpeed * delta;
            if (state.interpolationProgress > 1.0f) {
                state.interpolationProgress = 1.0f;
            }
        }

        float easedProgress = easeInOutCubic(state.interpolationProgress);

        double currentLat = lerp(state.currentPosition.lat, state.targetPosition.lat, easedProgress);
        double currentLng = lerp(state.currentPosition.lng, state.targetPosition.lng, easedProgress);
        Geolocation animatedPosition = new Geolocation(currentLat, currentLng);

        Vector2 pixelPos = MapRasterTiles.getPixelPosition(
            animatedPosition.lat,
            animatedPosition.lng,
            beginTile.x,
            beginTile.y
        );

        Vector2 targetPixel = MapRasterTiles.getPixelPosition(
            state.targetPosition.lat,
            state.targetPosition.lng,
            beginTile.x,
            beginTile.y
        );

        float angle = calculateAngle(pixelPos, targetPixel);
        TextureRegion busSprite = getBusSpriteForDirection(angle);

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

    private Geolocation calculatePositionAlongPath(BusLine line,
                                                   int currentStopIndex,
                                                   int nextStopIndex,
                                                   float segmentProgress) {
        List<BusStop> stops = line.getStops();
        List<Geolocation> path = line.getPath();

        if (stops.isEmpty() || path.isEmpty()) return null;
        if (currentStopIndex >= stops.size() || nextStopIndex >= stops.size()) return null;

        BusStop currentStop = stops.get(currentStopIndex);
        BusStop nextStop = stops.get(nextStopIndex);

        int pathStartIdx = findNearestPathIndex(path, currentStop.geo);
        int pathEndIdx = findNearestPathIndex(path, nextStop.geo);

        if (pathStartIdx == -1 || pathEndIdx == -1) {
            double lat = currentStop.geo.lat + (nextStop.geo.lat - currentStop.geo.lat) * segmentProgress;
            double lng = currentStop.geo.lng + (nextStop.geo.lng - currentStop.geo.lng) * segmentProgress;
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

    private double lerp(double start, double end, float t) {
        return start + (end - start) * t;
    }

    private float easeInOutCubic(float t) {
        return t * t * (3f - 2f * t);
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
