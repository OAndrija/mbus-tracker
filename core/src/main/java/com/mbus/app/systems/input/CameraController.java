package com.mbus.app.systems.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.mbus.app.utils.Constants;

/** Wheel and keyboard control of the map camera. Dragging is handled by {@link MapGestureListener}. */
public class CameraController extends InputAdapter {

    private static final float MIN_ZOOM = 0.03f;
    private static final float WHEEL_STEP = 1.2f;
    private static final float ZOOM_SMOOTHING = 14f;
    private static final float KEY_ZOOM_RATE = 2.5f;      // zoom factor per second
    private static final float KEY_PAN_SCREENS = 0.6f;    // screen heights per second

    private final OrthographicCamera camera;
    private final Vector3 anchor = new Vector3();
    private final Vector3 tmp = new Vector3();

    private float targetZoom;
    private boolean zooming = false;
    private int anchorX, anchorY;
    private UiHitTest uiHitTest = (x, y) -> false;
    private boolean keyboardEnabled = true;
    private Runnable userInputListener;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setUiHitTest(UiHitTest uiHitTest) {
        this.uiHitTest = uiHitTest;
    }

    /** Off while the user types in a text field, whose letters would otherwise move the map. */
    public void setKeyboardEnabled(boolean enabled) {
        this.keyboardEnabled = enabled;
    }

    /** Called when the user moves the map, e.g. to cancel a running fly-to animation. */
    public void setUserInputListener(Runnable listener) {
        this.userInputListener = listener;
    }

    public void update(float delta) {
        handleKeyboard(delta);
        updateZoom(delta);
        camera.zoom = clampZoom(camera.zoom);
    }

    public void stopZooming() {
        zooming = false;
    }

    private void handleKeyboard(float delta) {
        if (!keyboardEnabled) return;

        float distance = KEY_PAN_SCREENS * camera.viewportHeight * camera.zoom * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  camera.translate(-distance, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.translate(distance, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    camera.translate(0, distance);
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  camera.translate(0, -distance);

        float step = (float) Math.pow(KEY_ZOOM_RATE, delta);
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) { zooming = false; camera.zoom /= step; }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { zooming = false; camera.zoom *= step; }
    }

    private void updateZoom(float delta) {
        if (!zooming) return;

        float t = 1f - (float) Math.exp(-ZOOM_SMOOTHING * delta);
        float zoom = camera.zoom + (targetZoom - camera.zoom) * t;
        if (Math.abs(targetZoom - zoom) < targetZoom * 0.002f) {
            zoom = targetZoom;
            zooming = false;
        }
        zoomTo(zoom, anchorX, anchorY);
    }

    /** Changes the zoom while keeping the map point under the given screen position in place. */
    private void zoomTo(float zoom, int screenX, int screenY) {
        camera.update();
        camera.unproject(anchor.set(screenX, screenY, 0));
        camera.zoom = clampZoom(zoom);
        camera.update();
        camera.unproject(tmp.set(screenX, screenY, 0));
        camera.translate(anchor.x - tmp.x, anchor.y - tmp.y);
    }

    public float clampZoom(float zoom) {
        float maxZoom = Math.min(
            Constants.MAP_WIDTH / camera.viewportWidth,
            Constants.MAP_HEIGHT / camera.viewportHeight);
        return MathUtils.clamp(zoom, MIN_ZOOM, maxZoom);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        int x = Gdx.input.getX();
        int y = Gdx.input.getY();
        if (uiHitTest.isOverUi(x, y)) {
            return false;
        }
        zoomBy((float) Math.pow(WHEEL_STEP, amountY), x, y);
        return true;
    }

    /** Smoothly changes the zoom, keeping the given screen position in place. */
    public void zoomBy(float factor, int screenX, int screenY) {
        if (userInputListener != null) userInputListener.run();

        float from = zooming ? targetZoom : camera.zoom;
        targetZoom = clampZoom(from * factor);
        anchorX = screenX;
        anchorY = screenY;
        zooming = true;
    }
}
