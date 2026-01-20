package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusStop;

import java.util.ArrayList;
import java.util.List;

public class MarkerCluster {

    public Vector2 position;
    public List<BusStop> stops;
    public boolean isCluster;

    public Vector2 targetPosition;
    public Vector2 animatedPosition;
    public float currentScale;
    public float targetScale;
    public float alpha;
    public boolean isNew;
    public boolean isDying;

    private String clusterId;

    public MarkerCluster(Vector2 position, BusStop stop) {
        this.position = position;
        this.stops = new ArrayList<BusStop>();
        this.stops.add(stop);
        this.isCluster = false;

        initAnimation();
        this.clusterId = generateId();
    }

    public MarkerCluster(Vector2 position, List<BusStop> stops) {
        this.position = position;
        this.stops = new ArrayList<BusStop>(stops);
        this.isCluster = stops.size() > 1;

        initAnimation();
        this.clusterId = generateId();
    }

    private void initAnimation() {
        this.targetPosition = new Vector2(position);
        this.animatedPosition = new Vector2(position);
        this.currentScale = 0.3f;
        this.targetScale = 1.0f;
        this.alpha = 0.6f;
        this.isNew = true;
        this.isDying = false;
    }

    public int getCount() {
        return stops.size();
    }

    public Vector2 getPosition() {
        return animatedPosition;
    }

    public Vector2 getTargetPosition() {
        return targetPosition;
    }

    public BusStop getSingleStop() {
        return isCluster ? null : stops.get(0);
    }

    public List<BusStop> getStops() {
        return stops;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String id) {
        this.clusterId = id;
    }

    private String generateId() {
        StringBuilder sb = new StringBuilder();
        List<Integer> sortedIds = new ArrayList<Integer>();
        for (BusStop stop : stops) {
            sortedIds.add(stop.idAvpost);
        }
        java.util.Collections.sort(sortedIds);
        for (Integer id : sortedIds) {
            sb.append(id).append("-");
        }
        return sb.toString();
    }

    public void updateAnimation(float delta) {
        float POSITION_LERP_SPEED = 5.0f;
        float SCALE_LERP_SPEED = 6.0f;
        float ALPHA_FADE_SPEED = 8.0f;

        if (isDying) {
            float distanceToTarget = animatedPosition.dst(targetPosition);
            if (distanceToTarget > 300f) {
                POSITION_LERP_SPEED = 2.5f;
                ALPHA_FADE_SPEED = 3.0f;
            } else if (distanceToTarget > 150f) {
                POSITION_LERP_SPEED = 3.5f;
                ALPHA_FADE_SPEED = 4.0f;
            }
        }

        float posLerpFactor = 1.0f - (float)Math.pow(0.001f, delta * POSITION_LERP_SPEED);
        animatedPosition.x += (targetPosition.x - animatedPosition.x) * posLerpFactor;
        animatedPosition.y += (targetPosition.y - animatedPosition.y) * posLerpFactor;

        float scaleLerpFactor = 1.0f - (float)Math.pow(0.001f, delta * SCALE_LERP_SPEED);
        currentScale += (targetScale - currentScale) * scaleLerpFactor;

        if (isDying) {
            alpha = Math.max(0, alpha - delta * ALPHA_FADE_SPEED);
        } else {
            alpha = Math.min(1, alpha + delta * ALPHA_FADE_SPEED);
        }

        if (isNew && alpha >= 0.95f) {
            isNew = false;
        }
    }

    public void setTarget(Vector2 newTargetPosition, int newCount) {
        this.targetPosition.set(newTargetPosition);

        if (newCount > stops.size()) {
            targetScale = 1.3f;
        } else if (newCount < stops.size()) {
            targetScale = 0.8f;
        }
    }

    public void markForDeath() {
        isDying = true;
        targetScale = 0.2f;
    }

    public boolean shouldRemove() {
        float distanceToTarget = animatedPosition.dst(targetPosition);

        if (distanceToTarget < 20f && alpha <= 0.05f) {
            return true;
        }

        return false;
    }
}
