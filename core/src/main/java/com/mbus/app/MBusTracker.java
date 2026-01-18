package com.mbus.app;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.mbus.app.assets.AssetDescriptors;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.screens.LoadingScreen;
import com.mbus.app.screens.RasterMapScreen;
import com.mbus.app.systems.input.CameraController;
import com.mbus.app.utils.Constants;

import java.util.List;

public class MBusTracker extends Game {
    private static final String TAG = MBusTracker.class.getSimpleName();

    public OrthographicCamera camera;
    public Viewport viewport;
    public CameraController cameraController;
    public InputMultiplexer inputMultiplexer;
    private AssetManager assetManager;

    public float initialZoom;

    private List<BusStop> busStops;
    private List<BusLine> busLines;

    @Override
    public void create() {
        camera = new OrthographicCamera();

        viewport = new ExtendViewport(Constants.MAP_WIDTH, Constants.MAP_HEIGHT, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        assetManager = new AssetManager();

        try {
            Gdx.app.log(TAG, "Loading assets...");
            assetManager.load(AssetDescriptors.SKIN);
            assetManager.load(AssetDescriptors.BUS_ICON);
            assetManager.load(AssetDescriptors.TITLE_ICON);
            assetManager.load(AssetDescriptors.TIME_ICON);
            assetManager.load(AssetDescriptors.BUS);
            assetManager.finishLoading();
            Gdx.app.log(TAG, "Assets loaded successfully");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Error loading assets: " + e.getMessage(), e);
            return;
        }

        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            initialZoom = 0.20f;
        } else if (Gdx.app.getType() == Application.ApplicationType.Android
            || Gdx.app.getType() == Application.ApplicationType.iOS) {
            initialZoom = 0.10f;
        } else {
            initialZoom = 0.20f;
        }

        camera.zoom = initialZoom;
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.update();

        cameraController = new CameraController(camera);

        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(cameraController);
        Gdx.input.setInputProcessor(inputMultiplexer);

        setScreen(new LoadingScreen(this));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
        super.resize(width, height);
    }

    @Override
    public void dispose() {
        super.dispose();
        assetManager.dispose();
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public void setBusData(List<BusStop> stops, List<BusLine> lines) {
        this.busStops = stops;
        this.busLines = lines;
    }

    public List<BusStop> getBusStops() {
        return busStops;
    }

    public List<BusLine> getBusLines() {
        return busLines;
    }
}
