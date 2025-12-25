package com.mbus.app.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;

import com.mbus.app.MBusTracker;
import com.mbus.app.assets.AssetDescriptors;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.ui.HudPanel;
import com.mbus.app.utils.Constants;

import java.io.IOException;
import java.util.List;

public class RasterMapScreen implements Screen {

    private final MBusTracker app;

    private Texture[] mapTiles;
    private ZoomXY beginTile;
    private List<BusStop> stops;

    private MapRenderer mapRenderer;
    private GestureDetector gestureDetector;
    private HudPanel hudPanel;
    private Skin skin;

    private final Geolocation CENTER_GEOLOCATION =
        new Geolocation(46.557314, 15.637771);

    public RasterMapScreen(MBusTracker app) {
        this.app = app;
    }

    @Override
    public void show() {
        // Load skin from AssetManager
        skin = app.getAssetManager().get(AssetDescriptors.SKIN);

        loadTiles();
        loadData();

        mapRenderer = new MapRenderer(app.camera);
        mapRenderer.loadTiles(mapTiles, beginTile);
        mapRenderer.setStops(stops);

        hudPanel = new HudPanel(skin);

        setupInput();
    }

    @Override
    public void render(float delta) {

        ScreenUtils.clear(0, 0, 0, 1);

        updateCamera();

        app.viewport.apply();
        app.camera.update();

        mapRenderer.render();
        hudPanel.render();
    }

    // ------------------------------------------------------
    // CAMERA UPDATE + CLAMP
    // ------------------------------------------------------

    private void updateCamera() {

        app.cameraController.update();

        float effectiveViewportWidth  = app.camera.viewportWidth  * app.camera.zoom;
        float effectiveViewportHeight = app.camera.viewportHeight * app.camera.zoom;

        app.camera.position.x = MathUtils.clamp(
            app.camera.position.x,
            effectiveViewportWidth / 2f,
            Constants.MAP_WIDTH - effectiveViewportWidth / 2f
        );

        app.camera.position.y = MathUtils.clamp(
            app.camera.position.y,
            effectiveViewportHeight / 2f,
            Constants.MAP_HEIGHT - effectiveViewportHeight / 2f
        );
    }


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

    private void setupInput() {

        gestureDetector = new GestureDetector(new MapGestureListener(app.camera));

        if (app.inputMultiplexer != null) {
            app.inputMultiplexer.addProcessor(hudPanel.getStage());
            app.inputMultiplexer.addProcessor(gestureDetector);
        } else {
            Gdx.input.setInputProcessor(new InputMultiplexer(
                hudPanel.getStage(),
                gestureDetector,
                app.cameraController
            ));
        }
    }

    @Override
    public void resize(int width, int height) {
        app.viewport.update(width, height, false);
        if (hudPanel != null) {
            hudPanel.resize(width, height);
        }
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        // remove to avoid stacking
        if (gestureDetector != null && app.inputMultiplexer != null) {
            app.inputMultiplexer.removeProcessor(gestureDetector);
        }
        if (hudPanel != null && app.inputMultiplexer != null) {
            app.inputMultiplexer.removeProcessor(hudPanel.getStage());
        }
    }

    @Override
    public void dispose() {
        hide();
        if (mapRenderer != null) mapRenderer.dispose();
        if (hudPanel != null) hudPanel.dispose();
    }
}
