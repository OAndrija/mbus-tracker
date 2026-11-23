package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.BusLineColors;
import com.mbus.app.utils.Geolocation;
import com.mbus.app.utils.BusPositionCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    private static final float BADGE_RADIUS = 0.24f;      // relative to the bus sprite
    private static final float BADGE_OFFSET = 0.42f;

    private final Map<String, BusRenderState> busStates = new HashMap<>();
    private final Map<BusLine, Map<Integer, Integer>> pathIndexByStop = new IdentityHashMap<>();
    private final GlyphLayout badgeLayout = new GlyphLayout();
    private WorldGeometry geometry;
    private long frame = 0;

    private static class BusRenderState {
        Geolocation currentPosition;
        Geolocation targetPosition;
        float currentAngle;
        float targetAngle;
        float displayAngle;
        boolean isWaiting;
        long lastFrame;
        int lineId;
        final Vector2 pixelPosition = new Vector2();
        List<Float> recentAngles;

        BusRenderState(Geolocation position, float angle, boolean waiting) {
            this.currentPosition = position;
            this.targetPosition = position;
            this.currentAngle = angle;
            this.targetAngle = angle;
            this.displayAngle = angle;
            this.isWaiting = waiting;
            this.recentAngles = new ArrayList<>();
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

    public void setGeometry(WorldGeometry geometry) {
        this.geometry = geometry;
        pathIndexByStop.clear();
    }

    public void render(List<BusLine> lines, float currentTime, int dayType, OrthographicCamera camera,
                       Rectangle view, ShapeRenderer shapeRenderer, BitmapFont font, float delta) {
        if (busNorth == null || geometry == null) return;

        frame++;
        float size = BUS_SPRITE_SIZE * calculateZoomScale(camera.zoom);

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        for (BusPositionCalculator.ActiveBusInfo activeBus :
            BusPositionCalculator.getActiveBusesAtTime(lines, currentTime, dayType)) {
            renderBusSmooth(activeBus, size, delta);
        }
        spriteBatch.end();

        // Finished trips and lines that are no longer shown
        busStates.values().removeIf(state -> state.lastFrame != frame);

        renderBadges(size, camera, view, shapeRenderer, font);
    }

    private void renderBadges(float size, OrthographicCamera camera, Rectangle view,
                              ShapeRenderer shapeRenderer, BitmapFont font) {
        float radius = size * BADGE_RADIUS;
        float offset = size * BADGE_OFFSET;

        shapeRenderer.setProjectionMatrix(camera.combined);
        font.setColor(Color.WHITE);

        // One badge at a time, so overlapping badges never show each other's number.
        for (BusRenderState state : busStates.values()) {
            if (!isInView(state, view, size)) continue;
            float x = state.pixelPosition.x + offset;
            float y = state.pixelPosition.y + offset;

            Color color = BusLineColors.getColor(state.lineId);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(x, y, radius * 1.15f, 20);
            shapeRenderer.setColor(color.r * 0.8f, color.g * 0.8f, color.b * 0.8f, 1f);
            shapeRenderer.circle(x, y, radius, 20);
            shapeRenderer.end();

            String text = String.valueOf(state.lineId);
            font.getData().setScale(1f);
            badgeLayout.setText(font, text);
            float fit = Math.min(radius * 1.5f / badgeLayout.width, radius * 1.1f / badgeLayout.height);
            font.getData().setScale(fit);
            badgeLayout.setText(font, text);
            spriteBatch.begin();
            font.draw(spriteBatch, badgeLayout, x - badgeLayout.width / 2f, y + badgeLayout.height / 2f);
            spriteBatch.end();
        }
        font.getData().setScale(1f);
    }

    private static boolean isInView(BusRenderState state, Rectangle view, float margin) {
        return state.pixelPosition.x > view.x - margin
            && state.pixelPosition.x < view.x + view.width + margin
            && state.pixelPosition.y > view.y - margin
            && state.pixelPosition.y < view.y + view.height + margin;
    }

    private void renderBusSmooth(BusPositionCalculator.ActiveBusInfo activeBus,
                                 float size, float delta) {
        Geolocation targetPosition = calculateTargetPosition(activeBus);
        if (targetPosition == null) return;

        String busKey = getBusKey(activeBus);
        BusRenderState state = busStates.get(busKey);

        float targetAngle = calculateSmoothDirection(activeBus, targetPosition);

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

        Vector2 pixelPos = state.pixelPosition.set(geometry.project(state.currentPosition));
        state.lastFrame = frame;
        state.lineId = activeBus.line.lineId;

        TextureRegion busSprite = getBusSpriteForDirection(state.displayAngle);
        float spriteBaseAngle = getBaseAngleForSprite(state.displayAngle);
        float rotationOffset = normalizeAngle(state.currentAngle - spriteBaseAngle);

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
            sumX += (float) Math.cos(Math.toRadians(angle));
            sumY += (float) Math.sin(Math.toRadians(angle));
        }

        float avgX = sumX / angles.size();
        float avgY = sumY / angles.size();

        float avgAngle = (float) Math.toDegrees(Math.atan2(avgY, avgX));

        while (avgAngle < 0) avgAngle += 360;
        while (avgAngle >= 360) avgAngle -= 360;

        return avgAngle;
    }

    private float calculateSmoothDirection(BusPositionCalculator.ActiveBusInfo activeBus,
                                           Geolocation currentPos) {
        if (activeBus.isWaitingAtStop) {
            String busKey = getBusKey(activeBus);
            BusRenderState state = busStates.get(busKey);

            if (state != null) {
                return state.currentAngle;
            }

            List<BusSchedule.StopTime> stopTimes = activeBus.schedule.getStopTimes();
            List<BusStop> allStops = activeBus.line.getStops();

            // While waiting, current and next stop are the same one, so face the stop after it.
            int upcomingIndex = activeBus.currentStopIndex + 1;
            if (activeBus.currentStopIndex >= 0 && upcomingIndex < stopTimes.size()) {
                int currentStopId = stopTimes.get(activeBus.currentStopIndex).stopId;
                int nextStopId = stopTimes.get(upcomingIndex).stopId;

                BusStop currentStop = findStopById(allStops, currentStopId);
                BusStop nextStop = findStopById(allStops, nextStopId);

                if (currentStop != null && nextStop != null) {
                    return calculateAngleBetweenGeolocations(currentStop.geo, nextStop.geo);
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
            return calculateAngleBetweenGeolocations(currentPos, lookaheadPos);
        }

        List<BusSchedule.StopTime> stopTimes = activeBus.schedule.getStopTimes();
        List<BusStop> allStops = activeBus.line.getStops();

        if (activeBus.nextStopIndex >= 0 && activeBus.nextStopIndex < stopTimes.size()) {
            int stopId = stopTimes.get(activeBus.nextStopIndex).stopId;
            BusStop nextStop = findStopById(allStops, stopId);
            if (nextStop != null) {
                return calculateAngleBetweenGeolocations(currentPos, nextStop.geo);
            }
        }

        return 0;
    }

    private float calculateAngleBetweenGeolocations(Geolocation from, Geolocation to) {
        Vector2 fromPixel = geometry.project(from);
        Vector2 toPixel = geometry.project(to);

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
        return normalizeAngle(targetAngle - currentAngle);
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
        int pathStartIdx;
        int pathEndIdx;

        if (currentStopIndex == -1) {
            startGeo = path.get(0);
            pathStartIdx = 0;
            if (nextStopIndex >= 0 && nextStopIndex < stopTimes.size()) {
                int stopId = stopTimes.get(nextStopIndex).stopId;
                BusStop stop = findStopById(allStops, stopId);
                if (stop != null) {
                    endGeo = stop.geo;
                    pathEndIdx = pathIndexOf(line, stop);
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
            pathStartIdx = pathIndexOf(line, currentStop);
            pathEndIdx = pathIndexOf(line, nextStop);
        }

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

    private int pathIndexOf(BusLine line, BusStop stop) {
        return pathIndexByStop
            .computeIfAbsent(line, l -> new HashMap<>())
            .computeIfAbsent(stop.idAvpost, id -> findNearestPathIndex(line.getPath(), stop.geo));
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
        List<Float> segmentDistances = new ArrayList<>();

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
}
