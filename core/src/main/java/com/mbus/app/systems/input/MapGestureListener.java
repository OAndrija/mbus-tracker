package com.mbus.app.systems.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.BusLine;

public class MapGestureListener implements GestureDetector.GestureListener {

    private final OrthographicCamera camera;
    private float initialZoom = 1f;

    private MarkerClickHandler markerClickHandler;
    private BusLineClickHandler lineClickHandler;
    private BusStopClickCallback stopClickCallback;
    private BusLineClickCallback lineClickCallback;

    // HUD boundary
    private float hudWidth = 0f;

    public MapGestureListener(OrthographicCamera camera) {
        this.camera = camera;
        this.markerClickHandler = new MarkerClickHandler(camera);
        this.lineClickHandler = new BusLineClickHandler(camera);
    }

    /**
     * Set the HUD width to exclude from gesture handling
     */
    public void setHudWidth(float width) {
        this.hudWidth = width;
    }

    /**
     * Check if position is over the HUD area
     */
    private boolean isOverHud(float x) {
        return x < hudWidth;
    }

    /**
     * Set the callback to be invoked when a bus stop marker is clicked
     */
    public void setBusStopClickCallback(BusStopClickCallback callback) {
        this.stopClickCallback = callback;
    }

    /**
     * Set the callback to be invoked when a bus line is clicked
     */
    public void setBusLineClickCallback(BusLineClickCallback callback) {
        this.lineClickCallback = callback;
    }

    /**
     * Set the marker click handler with stop data
     */
    public void setMarkerClickHandler(MarkerClickHandler handler) {
        this.markerClickHandler = handler;
    }

    /**
     * Set the line click handler with line data
     */
    public void setLineClickHandler(BusLineClickHandler handler) {
        this.lineClickHandler = handler;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        // Don't pan if gesture started over HUD
        if (isOverHud(x)) {
            return false;
        }
        camera.translate(-deltaX * camera.zoom, deltaY * camera.zoom);
        return true;
    }

    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        float ratio = initialDistance / distance;
        camera.zoom = initialZoom * ratio;
        return true;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2,
                         Vector2 pointer1, Vector2 pointer2) {
        return false;
    }

    @Override
    public void pinchStop() {
        initialZoom = camera.zoom;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        initialZoom = camera.zoom;
        // Don't handle if over HUD
        if (isOverHud(x)) {
            return false;
        }
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        // Don't handle taps over HUD
        if (isOverHud(x)) {
            return false;
        }

        // Check if a marker was tapped first (markers have priority)
        if (markerClickHandler != null && stopClickCallback != null) {
            BusStop clickedStop = markerClickHandler.checkMarkerClick((int)x, (int)y);
            if (clickedStop != null) {
                stopClickCallback.onBusStopClicked(clickedStop);
                return true; // Consume the event
            }
        }

        // If no marker was clicked, check if a bus line was tapped
        if (lineClickHandler != null && lineClickCallback != null) {
            BusLine clickedLine = lineClickHandler.checkLineClick((int)x, (int)y);
            if (clickedLine != null) {
                lineClickCallback.onBusLineClicked(clickedLine);
                return true; // Consume the event
            }
        }

        return false;
    }

    @Override public boolean longPress(float x, float y) { return false; }
    @Override public boolean fling(float velocityX, float velocityY, int button) { return false; }

    /**
     * Callback interface for bus stop clicks
     */
    public interface BusStopClickCallback {
        void onBusStopClicked(BusStop busStop);
    }

    /**
     * Callback interface for bus line clicks
     */
    public interface BusLineClickCallback {
        void onBusLineClicked(BusLine busLine);
    }
}
