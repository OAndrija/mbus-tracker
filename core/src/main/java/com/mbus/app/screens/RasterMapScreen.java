package com.mbus.app.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.input.GestureDetector;

import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.mbus.app.MBusTracker;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.Constants;

import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.input.CameraController;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.map.MapRenderer;

import java.io.IOException;
import java.util.List;

public class RasterMapScreen implements Screen {

    private final MBusTracker app;

    private OrthographicCamera camera;
    private CameraController cameraController;
    private Viewport viewport;

    private Texture[] mapTiles;
    private ZoomXY beginTile;
    private List<BusStop> stops;

    private MapRenderer mapRenderer;

    private final Geolocation CENTER_GEOLOCATION = new Geolocation(46.557370, 15.637771);

    public RasterMapScreen(MBusTracker app) {
        this.app = app;
    }

    @Override
    public void show() {

        setupCamera();
        loadTiles();
        loadData();

        mapRenderer = new MapRenderer(camera);
        mapRenderer.loadTiles(mapTiles, beginTile);
        mapRenderer.setStops(stops);

        setupInput();
    }

    @Override
    public void render(float delta) {

        ScreenUtils.clear(0, 0, 0, 1);

        updateCamera();
        viewport.apply();
        camera.update();

        mapRenderer.render();
    }

    // ------------------------------------------------------
    //  CAMERA
    // ------------------------------------------------------

    private void setupCamera() {
        camera = new OrthographicCamera();

        // Show a good portion of the map on all devices
        float worldWidth  = Constants.MAP_WIDTH;
        float worldHeight = Constants.MAP_HEIGHT;

        // ExtendViewport ensures full-screen coverage without distortion
        viewport = new ExtendViewport(worldWidth, worldHeight, camera);

        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        float initialZoom;

        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            initialZoom = 0.20f;
        }
        else if (Gdx.app.getType() == Application.ApplicationType.Android) {
            initialZoom = 0.1f;
        }
        else if (Gdx.app.getType() == Application.ApplicationType.iOS) {
            initialZoom = 0.1f;
        }
        else {
            initialZoom = 0.20f; // fallback
        }

        camera.zoom = initialZoom;
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.update();
    }

    private void updateCamera() {
        cameraController.update();

        float effectiveViewportWidth = camera.viewportWidth * camera.zoom;
        float effectiveViewportHeight = camera.viewportHeight * camera.zoom;

        camera.position.x = MathUtils.clamp(
            camera.position.x,
            effectiveViewportWidth / 2f,
            Constants.MAP_WIDTH - effectiveViewportWidth / 2f
        );

        camera.position.y = MathUtils.clamp(
            camera.position.y,
            effectiveViewportHeight / 2f,
            Constants.MAP_HEIGHT - effectiveViewportHeight / 2f
        );
    }

    // ------------------------------------------------------
    //  TILE + DATA LOADING
    // ------------------------------------------------------

    private void loadTiles() {
        try {
            ZoomXY centerTile = MapRasterTiles.getTileNumber(
                CENTER_GEOLOCATION.lat,
                CENTER_GEOLOCATION.lng,
                Constants.ZOOM
            );

            mapTiles = MapRasterTiles.getRasterTileZone(centerTile, Constants.NUM_TILES);

            beginTile = new ZoomXY(
                Constants.ZOOM,
                centerTile.x - ((Constants.NUM_TILES - 1) / 2),
                centerTile.y - ((Constants.NUM_TILES - 1) / 2)
            );

        } catch (IOException e) {
            Gdx.app.error("RasterMapScreen", "Failed to load tiles", e);
        }
    }

    private void loadData() {
        stops = GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json");
    }

    // ------------------------------------------------------
    // INPUT
    // ------------------------------------------------------

    private void setupInput() {
        cameraController = new CameraController(camera);
        GestureDetector gd = new GestureDetector(new MapGestureListener(camera));

        Gdx.input.setInputProcessor(new InputMultiplexer(gd, cameraController));
    }

    // ------------------------------------------------------
    //  LIFECYCLE
    // ------------------------------------------------------

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
    }
}
