package com.mbus.app.systems.input;

import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.mbus.app.model.BusStop;

public class MapGestureListener implements GestureDetector.GestureListener {

    private final OrthographicCamera camera;
    private float initialZoom = 1f;

    private MarkerClickHandler markerClickHandler;
    private BusStopClickCallback clickCallback;

    public MapGestureListener(OrthographicCamera camera) {
        this.camera = camera;
        this.markerClickHandler = new MarkerClickHandler(camera);
    }

    /**
     * Set the callback to be invoked when a bus stop marker is clicked
     */
    public void setBusStopClickCallback(BusStopClickCallback callback) {
        this.clickCallback = callback;
    }

    /**
     * Set the marker click handler with stop data
     */
    public void setMarkerClickHandler(MarkerClickHandler handler) {
        this.markerClickHandler = handler;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
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
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        // Check if a marker was tapped
        if (markerClickHandler != null && clickCallback != null) {
            BusStop clickedStop = markerClickHandler.checkMarkerClick((int)x, (int)y);
            if (clickedStop != null) {
                clickCallback.onBusStopClicked(clickedStop);
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
}
