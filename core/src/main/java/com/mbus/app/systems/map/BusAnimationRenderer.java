package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.BusPositionCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusAnimationRenderer {

    private static final float BUS_SPRITE_SIZE = 60f;
    private static final float MIN_ZOOM_SCALE = 0.8f;
    private static final float MAX_ZOOM_SCALE = 5f;
    private static final float ZOOM_SCALE_FACTOR = 7f;

    private static final float POSITION_INTERPOLATION_SPEED = 15f;
    private static final float ANGLE_INTERPOLATION_SPEED = 8f;
    private static final float POSITION_THRESHOLD = 0.0000001f;

    private static final float DIRECTION_LOOKAHEAD = 0.25f;
    private static final float ANGLE_SMOOTHING_WINDOW = 5;

    private final SpriteBatch spriteBatch;
    private TextureRegion busNorth;
    private TextureRegion busNortheast;
    private TextureRegion busEast;
    private TextureRegion busSoutheast;
    private TextureRegion busSouth;
    private TextureRegion busSouthwest;
    private TextureRegion busWest;
    private TextureRegion busNorthwest;

    private Map<String, BusRenderState> busStates = new HashMap<String, BusRenderState>();

    private static class BusRenderState {
        Geolocation currentPosition;
        Geolocation targetPosition;
        float currentAngle;
        float targetAngle;
        float displayAngle;
        boolean isWaiting;
        long lastUpdateTime;
        List<Float> recentAngles;

        BusRenderState(Geolocation position, float angle, boolean waiting) {
            this.currentPosition = position;
            this.targetPosition = position;
            this.currentAngle = angle;
            this.targetAngle = angle;
            this.displayAngle = angle;
            this.isWaiting = waiting;
            this.lastUpdateTime = System.currentTimeMillis();
            this.recentAngles = new ArrayList<Float>();
            this.recentAngles.add(angle);
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
        Geolocation targetPosition = calculateTargetPosition(activeBus);
        if (targetPosition == null) return;

        String busKey = getBusKey(activeBus);
        BusRenderState state = busStates.get(busKey);

        float targetAngle = calculateSmoothDirection(activeBus, targetPosition, beginTile);

        if (state == null) {
            state = new BusRenderState(targetPosition, targetAngle, activeBus.isWaitingAtStop);
            busStates.put(busKey, state);
        }

        state.targetPosition = targetPosition;
        state.targetAngle = targetAngle;

        float posLerpFactor = Math.min(1.0f, POSITION_INTERPOLATION_SPEED * delta);

        double latDiff = state.targetPosition.lat - state.currentPosition.lat;
        double lngDiff = state.targetPosition.lng - state.currentPosition.lng;

        if (Math.abs(latDiff) > POSITION_THRESHOLD || Math.abs(lngDiff) > POSITION_THRESHOLD) {
            state.currentPosition = new Geolocation(
                state.currentPosition.lat + latDiff * posLerpFactor,
                state.currentPosition.lng + lngDiff * posLerpFactor
            );
        } else {
            state.currentPosition = state.targetPosition;
        }

        if (!activeBus.isWaitingAtStop) {
            float angleLerpFactor = Math.min(1.0f, ANGLE_INTERPOLATION_SPEED * delta);
            float angleDiff = getShortestAngleDifference(state.currentAngle, state.targetAngle);

            if (Math.abs(angleDiff) > 0.5f) {
                state.currentAngle = normalizeAngle(state.currentAngle + angleDiff * angleLerpFactor);
            } else {
                state.currentAngle = state.targetAngle;
            }

            state.recentAngles.add(state.currentAngle);
            if (state.recentAngles.size() > ANGLE_SMOOTHING_WINDOW) {
                state.recentAngles.remove(0);
            }

            state.displayAngle = calculateSmoothedAngle(state.recentAngles);

            state.isWaiting = false;
        } else {
            state.isWaiting = true;
        }

        Vector2 pixelPos = MapRasterTiles.getPixelPosition(
            state.currentPosition.lat,
            state.currentPosition.lng,
            beginTile.x,
            beginTile.y
        );

        TextureRegion busSprite = getBusSpriteForDirection(state.displayAngle);
        float spriteBaseAngle = getBaseAngleForSprite(state.displayAngle);
        float rotationOffset = normalizeAngle(state.currentAngle - spriteBaseAngle);

        float size = BUS_SPRITE_SIZE * zoomScale;
        float halfSize = size / 2f;

        spriteBatch.draw(
            busSprite,
            pixelPos.x - halfSize,
            pixelPos.y - halfSize,
            halfSize,
            halfSize,
            size,
            size,
            1f,
            1f,
            rotationOffset
        );
    }

    private float calculateSmoothedAngle(List<Float> angles) {
        if (angles.isEmpty()) return 0;
        if (angles.size() == 1) return angles.get(0);

        float sumX = 0;
        float sumY = 0;

        for (float angle : angles) {
            sumX += Math.cos(Math.toRadians(angle));
            sumY += Math.sin(Math.toRadians(angle));
        }

        float avgX = sumX / angles.size();
        float avgY = sumY / angles.size();

        float avgAngle = (float) Math.toDegrees(Math.atan2(avgY, avgX));

        while (avgAngle < 0) avgAngle += 360;
        while (avgAngle >= 360) avgAngle -= 360;

        return avgAngle;
    }

    private float calculateSmoothDirection(BusPositionCalculator.ActiveBusInfo activeBus,
                                           Geolocation currentPos,
                                           ZoomXY beginTile) {
        if (activeBus.isWaitingAtStop) {
            String busKey = getBusKey(activeBus);
            BusRenderState state = busStates.get(busKey);

            if (state != null) {
                return state.currentAngle;
            }

            List<BusSchedule.StopTime> stopTimes = activeBus.schedule.getStopTimes();
            List<BusStop> allStops = activeBus.line.getStops();

            if (activeBus.currentStopIndex >= 0 && activeBus.currentStopIndex < stopTimes.size() &&
                activeBus.nextStopIndex >= 0 && activeBus.nextStopIndex < stopTimes.size()) {
                int currentStopId = stopTimes.get(activeBus.currentStopIndex).stopId;
                int nextStopId = stopTimes.get(activeBus.nextStopIndex).stopId;

                BusStop currentStop = findStopById(allStops, currentStopId);
                BusStop nextStop = findStopById(allStops, nextStopId);

                if (currentStop != null && nextStop != null) {
                    return calculateAngleBetweenGeolocations(currentStop.geo, nextStop.geo, beginTile);
                }
            }
        }

        float lookaheadProgress = Math.min(1.0f, activeBus.segmentProgress + DIRECTION_LOOKAHEAD);

        Geolocation lookaheadPos = calculatePositionAlongPath(
            activeBus.line,
            activeBus.schedule,
            activeBus.currentStopIndex,
            activeBus.nextStopIndex,
            lookaheadProgress
        );

        if (lookaheadPos != null) {
            return calculateAngleBetweenGeolocations(currentPos, lookaheadPos, beginTile);
        }

        List<BusSchedule.StopTime> stopTimes = activeBus.schedule.getStopTimes();
        List<BusStop> allStops = activeBus.line.getStops();

        if (activeBus.nextStopIndex >= 0 && activeBus.nextStopIndex < stopTimes.size()) {
            int stopId = stopTimes.get(activeBus.nextStopIndex).stopId;
            BusStop nextStop = findStopById(allStops, stopId);
            if (nextStop != null) {
                return calculateAngleBetweenGeolocations(currentPos, nextStop.geo, beginTile);
            }
        }

        return 0;
    }

    private float calculateAngleBetweenGeolocations(Geolocation from, Geolocation to, ZoomXY beginTile) {
        Vector2 fromPixel = MapRasterTiles.getPixelPosition(from.lat, from.lng, beginTile.x, beginTile.y);
        Vector2 toPixel = MapRasterTiles.getPixelPosition(to.lat, to.lng, beginTile.x, beginTile.y);

        float dx = toPixel.x - fromPixel.x;
        float dy = toPixel.y - fromPixel.y;

        if (Math.abs(dx) < 0.1f && Math.abs(dy) < 0.1f) {
            return 0;
        }

        float angleRad = (float) Math.atan2(dy, dx);
        float angleDeg = (float) Math.toDegrees(angleRad);

        while (angleDeg < 0) angleDeg += 360;
        while (angleDeg >= 360) angleDeg -= 360;

        return angleDeg;
    }

    private float getShortestAngleDifference(float currentAngle, float targetAngle) {
        float diff = normalizeAngle(targetAngle - currentAngle);
        return diff;
    }

    private Geolocation calculateTargetPosition(BusPositionCalculator.ActiveBusInfo activeBus) {
        return calculatePositionAlongPath(
            activeBus.line,
            activeBus.schedule,
            activeBus.currentStopIndex,
            activeBus.nextStopIndex,
            activeBus.segmentProgress
        );
    }

    private BusStop findStopById(List<BusStop> stops, int stopId) {
        for (BusStop stop : stops) {
            if (stop.idAvpost == stopId) {
                return stop;
            }
        }
        return null;
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
                                                   BusSchedule schedule,
                                                   int currentStopIndex,
                                                   int nextStopIndex,
                                                   float segmentProgress) {
        List<BusStop> allStops = line.getStops();
        List<Geolocation> path = line.getPath();
        List<BusSchedule.StopTime> stopTimes = schedule.getStopTimes();

        if (allStops.isEmpty() || path.isEmpty() || stopTimes.isEmpty()) return null;

        Geolocation startGeo;
        Geolocation endGeo;

        if (currentStopIndex == -1) {
            if (path.isEmpty()) return null;
            startGeo = path.get(0);
            if (nextStopIndex >= 0 && nextStopIndex < stopTimes.size()) {
                int stopId = stopTimes.get(nextStopIndex).stopId;
                BusStop stop = findStopById(allStops, stopId);
                if (stop != null) {
                    endGeo = stop.geo;
                } else {
                    return startGeo;
                }
            } else {
                return startGeo;
            }
        } else {
            if (currentStopIndex >= stopTimes.size() || nextStopIndex >= stopTimes.size()) return null;
            if (currentStopIndex < 0 || nextStopIndex < 0) return null;

            int currentStopId = stopTimes.get(currentStopIndex).stopId;
            int nextStopId = stopTimes.get(nextStopIndex).stopId;

            BusStop currentStop = findStopById(allStops, currentStopId);
            BusStop nextStop = findStopById(allStops, nextStopId);

            if (currentStop == null || nextStop == null) return null;

            startGeo = currentStop.geo;
            endGeo = nextStop.geo;
        }

        int pathStartIdx = findNearestPathIndex(path, startGeo);
        int pathEndIdx = findNearestPathIndex(path, endGeo);

        if (pathStartIdx == -1 || pathEndIdx == -1) {
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

    private TextureRegion getBusSpriteForDirection(float angleDeg) {
        angleDeg = angleDeg % 360;
        if (angleDeg < 0) angleDeg += 360;

        if (angleDeg >= 345f || angleDeg < 15f) {
            return busEast;
        } else if (angleDeg >= 15f && angleDeg < 75f) {
            return busNortheast;
        } else if (angleDeg >= 75f && angleDeg < 105f) {
            return busNorth;
        } else if (angleDeg >= 105f && angleDeg < 165f) {
            return busNorthwest;
        } else if (angleDeg >= 165f && angleDeg < 195f) {
            return busWest;
        } else if (angleDeg >= 195f && angleDeg < 255f) {
            return busSouthwest;
        } else if (angleDeg >= 255f && angleDeg < 285f) {
            return busSouth;
        } else {
            return busSoutheast;
        }
    }

    private float getBaseAngleForSprite(float angleDeg) {
        angleDeg = angleDeg % 360;
        if (angleDeg < 0) angleDeg += 360;

        if (angleDeg >= 345f || angleDeg < 15f) {
            return 0f;
        } else if (angleDeg >= 15f && angleDeg < 75f) {
            return 45f;
        } else if (angleDeg >= 75f && angleDeg < 105f) {
            return 90f;
        } else if (angleDeg >= 105f && angleDeg < 165f) {
            return 135f;
        } else if (angleDeg >= 165f && angleDeg < 195f) {
            return 180f;
        } else if (angleDeg >= 195f && angleDeg < 255f) {
            return 225f;
        } else if (angleDeg >= 255f && angleDeg < 285f) {
            return 270f;
        } else {
            return 315f;
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
