package com.mbus.app.systems.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.BusLine;

public class MapGestureListener implements GestureDetector.GestureListener {

    private final OrthographicCamera camera;
    private float initialZoom = 1f;

    private StopFinder stopFinder;
    private LineFinder lineFinder;
    private BusStopClickCallback stopClickCallback;
    private BusLineClickCallback lineClickCallback;
    private ClusterClickHandler clusterClickHandler;
    private BusClickHandler busClickHandler;
    private MapClickHandler mapClickHandler;
    private Runnable mapTouchListener;
    private Runnable panListener;

    private UiHitTest uiHitTest = (x, y) -> false;
    private boolean touchStartedOnMap = false;

    public MapGestureListener(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setUiHitTest(UiHitTest uiHitTest) {
        this.uiHitTest = uiHitTest;
    }

    private boolean isOverUi(float x, float y) {
        return uiHitTest.isOverUi((int) x, (int) y);
    }

    public void setBusStopClickCallback(BusStopClickCallback callback) {
        this.stopClickCallback = callback;
    }

    public void setBusLineClickCallback(BusLineClickCallback callback) {
        this.lineClickCallback = callback;
    }

    public void setClusterClickHandler(ClusterClickHandler handler) {
        this.clusterClickHandler = handler;
    }

    public void setBusClickHandler(BusClickHandler handler) {
        this.busClickHandler = handler;
    }

    /** Asked before anything else that may be clicked. */
    public void setMapClickHandler(MapClickHandler handler) {
        this.mapClickHandler = handler;
    }

    public void setMapTouchListener(Runnable listener) {
        this.mapTouchListener = listener;
    }

    public void setPanListener(Runnable listener) {
        this.panListener = listener;
    }

    public void setStopFinder(StopFinder stopFinder) {
        this.stopFinder = stopFinder;
    }

    public void setLineFinder(LineFinder lineFinder) {
        this.lineFinder = lineFinder;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (!touchStartedOnMap) {
            return false;
        }
        if (panListener != null) panListener.run();

        float worldPerPixel = camera.viewportWidth * camera.zoom / Gdx.graphics.getWidth();
        camera.translate(-deltaX * worldPerPixel, deltaY * worldPerPixel);
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
        touchStartedOnMap = !isOverUi(x, y);
        if (touchStartedOnMap && mapTouchListener != null) mapTouchListener.run();
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        if (isOverUi(x, y)) {
            return false;
        }

        if (mapClickHandler != null && mapClickHandler.onMapClicked((int) x, (int) y, button == Input.Buttons.RIGHT)) {
            return true;
        }
        if (button != Input.Buttons.LEFT) {
            return false;
        }

        if (busClickHandler != null && busClickHandler.onMapClicked((int) x, (int) y)) {
            return true;
        }

        if (clusterClickHandler != null && clusterClickHandler.onMapClicked((int) x, (int) y)) {
            return true;
        }

        if (stopFinder != null && stopClickCallback != null) {
            BusStop clickedStop = stopFinder.findStop((int) x, (int) y);
            if (clickedStop != null) {
                stopClickCallback.onBusStopClicked(clickedStop);
                return true;
            }
        }

        if (lineFinder != null && lineClickCallback != null) {
            BusLine clickedLine = lineFinder.findLine((int) x, (int) y);
            if (clickedLine != null) {
                lineClickCallback.onBusLineClicked(clickedLine);
                return true;
            }
        }

        return false;
    }

    @Override public boolean longPress(float x, float y) { return false; }
    @Override public boolean fling(float velocityX, float velocityY, int button) { return false; }

    public interface StopFinder {
        /** Returns the stop marker at a screen position, or null. */
        BusStop findStop(int screenX, int screenY);
    }

    public interface LineFinder {
        /** Returns the line at a screen position, or null. */
        BusLine findLine(int screenX, int screenY);
    }

    public interface MapClickHandler {
        /** Returns true if the click was used. */
        boolean onMapClicked(int screenX, int screenY, boolean rightButton);
    }

    public interface BusClickHandler {
        /** Returns true if a bus was hit. */
        boolean onMapClicked(int screenX, int screenY);
    }

    public interface ClusterClickHandler {
        /** Returns true if a stop cluster was hit. */
        boolean onMapClicked(int screenX, int screenY);
    }

    public interface BusStopClickCallback {
        void onBusStopClicked(BusStop busStop);
    }

    public interface BusLineClickCallback {
        void onBusLineClicked(BusLine busLine);
    }
}
