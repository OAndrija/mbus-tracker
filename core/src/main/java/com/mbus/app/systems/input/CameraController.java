package com.mbus.app.systems.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.mbus.app.utils.Constants;

public class CameraController implements InputProcessor {

    private final OrthographicCamera camera;

    private boolean dragging = false;
    private int lastX, lastY;

    public float minZoom      = 0.05f;
    public float maxZoom      = 2.5f;
    public float keyboardSpeed = 20f;
    public float zoomSpeed     = 0.02f;
    public float dragSpeed     = 1.6f;

    private float hudWidth = 0f;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setHudWidth(float width) {
        this.hudWidth = width;
    }

    private boolean isMouseOverHud() {
        return Gdx.input.getX() < hudWidth;
    }

    public void update() {
        handleMouseDrag();
        handleKeyboardMovement();
        clampCamera();
    }

    private void handleMouseDrag() {
        if (isMouseOverHud()) {
            dragging = false;
            return;
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {

            int x = Gdx.input.getX();
            int y = Gdx.input.getY();

            if (!dragging) {
                dragging = true;
                lastX = x;
                lastY = y;
                return;
            }

            int deltaX = x - lastX;
            int deltaY = y - lastY;

            float speed = dragSpeed * (float) Math.sqrt(camera.zoom);
            camera.translate(-deltaX * speed, deltaY * speed);

            lastX = x;
            lastY = y;

        } else {
            dragging = false;
        }
    }

    private void handleKeyboardMovement() {

        float speed = keyboardSpeed * camera.zoom;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  camera.translate(-speed, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.translate(speed, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    camera.translate(0, speed);
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  camera.translate(0, -speed);

        if (Gdx.input.isKeyPressed(Input.Keys.Q)) camera.zoom -= zoomSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) camera.zoom += zoomSpeed;
    }

    private void clampCamera() {
        camera.zoom = Math.max(minZoom, camera.zoom);

        float maxZoomX = Constants.MAP_WIDTH  / camera.viewportWidth;
        float maxZoomY = Constants.MAP_HEIGHT / camera.viewportHeight;

        float maxAllowedZoom = Math.min(maxZoomX, maxZoomY);

        camera.zoom = Math.min(camera.zoom, maxAllowedZoom);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (isMouseOverHud()) {
            return false;
        }
        camera.zoom += amountY * zoomSpeed;
        return true;
    }

    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
}
