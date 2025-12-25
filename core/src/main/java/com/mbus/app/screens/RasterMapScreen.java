package com.mbus.app.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;

import com.mbus.app.MBusTracker;
import com.mbus.app.assets.AssetDescriptors;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.input.MarkerClickHandler;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.ui.HudPanel;
import com.mbus.app.ui.BusStopDetailPanel;
import com.mbus.app.utils.Constants;

import java.io.IOException;
import java.util.List;

public class RasterMapScreen implements Screen {

    private final MBusTracker app;

    private Texture[] mapTiles;
    private ZoomXY beginTile;
    private List<BusStop> stops;

    private MapRenderer mapRenderer;
    private MapGestureListener mapGestureListener;
    private GestureDetector gestureDetector;
    private HudPanel hudPanel;
    private BusStopDetailPanel detailPanel;
    private Skin skin;

    // Camera animation
    private boolean animatingCamera = false;
    private float animationProgress = 0f;
    private float animationDuration = 1.5f; // Duration in seconds
    private float startX, startY, startZoom;
    private float targetX, targetY, targetZoom;

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

        // Create HUD panels
        hudPanel = new HudPanel(skin);
        detailPanel = new BusStopDetailPanel(skin);

        // Set callback for when detail panel visibility changes
        detailPanel.setVisibilityChangeCallback(new BusStopDetailPanel.VisibilityChangeCallback() {
            @Override
            public void onVisibilityChanged(boolean visible) {
                updateHudBoundaries();
            }
        });

        // Set callback for "Vse postaje" button toggle
        hudPanel.setShowAllStopsCallback(new HudPanel.ShowAllStopsCallback() {
            @Override
            public void onShowAllStopsChanged(boolean showAll) {
                Gdx.app.log("RasterMapScreen", "Show all stops: " + showAll);
                mapRenderer.setShowMarkers(showAll);
            }
        });

        // Set callback for when a bus stop is clicked in the HUD panel
        hudPanel.setBusStopClickCallback(new HudPanel.BusStopClickCallback() {
            @Override
            public void onBusStopClicked(BusStop busStop) {
                Gdx.app.log("RasterMapScreen", "HUD clicked bus stop: " + busStop.name);
                // Show the detail panel
                detailPanel.showBusStop(busStop);
                // Zoom to the bus stop on the map
                zoomToBusStop(busStop);
            }
        });

        // Set the bus stops data in the HUD panel
        hudPanel.setBusStops(stops);

        setupInput();
    }

    /**
     * Zoom and center the camera on a specific bus stop with smooth animation
     */
    private void zoomToBusStop(BusStop busStop) {
        // Get the pixel position of the bus stop on the map
        Vector2 pos = MapRasterTiles.getPixelPosition(
            busStop.geo.lat,
            busStop.geo.lng,
            beginTile.x,
            beginTile.y
        );

        Gdx.app.log("RasterMapScreen", "Zooming to bus stop: " + busStop.name +
            " at position (" + pos.x + ", " + pos.y + ")");
        Gdx.app.log("RasterMapScreen", "Map dimensions: " + Constants.MAP_WIDTH + " x " + Constants.MAP_HEIGHT);

        // Store current camera state
        startX = app.camera.position.x;
        startY = app.camera.position.y;
        startZoom = app.camera.zoom;

        // Set target camera state
        targetX = pos.x;
        targetY = pos.y;
        targetZoom = 0.1f;

        Gdx.app.log("RasterMapScreen", "Animation: from (" + startX + ", " + startY + ", zoom=" + startZoom +
            ") to (" + targetX + ", " + targetY + ", zoom=" + targetZoom + ")");

        // Start animation
        animatingCamera = true;
        animationProgress = 0f;
    }

    @Override
    public void render(float delta) {

        ScreenUtils.clear(0, 0, 0, 1);

        // Update camera animation if active
        if (animatingCamera) {
            updateCameraAnimation(delta);
        } else {
            updateCamera();
        }

        app.viewport.apply();
        app.camera.update();

        mapRenderer.render();

        // Render HUD panels on top
        hudPanel.render();
        detailPanel.render();
    }

    // ------------------------------------------------------
    // CAMERA UPDATE + CLAMP
    // ------------------------------------------------------

    /**
     * Update camera animation for smooth zoom/pan to bus stop
     */
    private void updateCameraAnimation(float delta) {
        animationProgress += delta / animationDuration;

        if (animationProgress >= 1f) {
            // Animation complete
            animationProgress = 1f;
            animatingCamera = false;
            Gdx.app.log("RasterMapScreen", "Animation complete at (" +
                app.camera.position.x + ", " + app.camera.position.y +
                ", zoom=" + app.camera.zoom + ")");
        }

        // Use easeInOutCubic for smooth animation
        float t = easeInOutCubic(animationProgress);

        // Interpolate position and zoom
        app.camera.position.x = MathUtils.lerp(startX, targetX, t);
        app.camera.position.y = MathUtils.lerp(startY, targetY, t);
        app.camera.zoom = MathUtils.lerp(startZoom, targetZoom, t);

        if (animationProgress >= 1f) {
            clampCamera();
        }
    }

    /**
     * Easing function for smooth animation (ease in-out cubic)
     */
    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4f * t * t * t;
        } else {
            float f = t - 1f;
            return 1f + 4f * f * f * f;
        }
    }

    private void updateCamera() {
        // Don't update camera controller during animation
        if (!animatingCamera) {
            app.cameraController.update();
        }
        clampCamera();
    }

    private void clampCamera() {
        float effectiveViewportWidth  = app.camera.viewportWidth  * app.camera.zoom;
        float effectiveViewportHeight = app.camera.viewportHeight * app.camera.zoom;

        // Calculate bounds - if viewport is larger than map, center it
        float minX, maxX, minY, maxY;

        if (effectiveViewportWidth >= Constants.MAP_WIDTH) {
            // Viewport wider than map - center horizontally
            minX = maxX = Constants.MAP_WIDTH / 2f;
        } else {
            // Normal clamping
            minX = effectiveViewportWidth / 2f;
            maxX = Constants.MAP_WIDTH - effectiveViewportWidth / 2f;
        }

        if (effectiveViewportHeight >= Constants.MAP_HEIGHT) {
            // Viewport taller than map - center vertically
            minY = maxY = Constants.MAP_HEIGHT / 2f;
        } else {
            // Normal clamping
            minY = effectiveViewportHeight / 2f;
            maxY = Constants.MAP_HEIGHT - effectiveViewportHeight / 2f;
        }

        app.camera.position.x = MathUtils.clamp(app.camera.position.x, minX, maxX);
        app.camera.position.y = MathUtils.clamp(app.camera.position.y, minY, maxY);
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
        // Create gesture listener with marker click handling
        mapGestureListener = new MapGestureListener(app.camera);

        // Set up marker click handler
        MarkerClickHandler markerClickHandler = new MarkerClickHandler(app.camera);
        markerClickHandler.setStops(stops);
        markerClickHandler.setBeginTile(beginTile);
        mapGestureListener.setMarkerClickHandler(markerClickHandler);

        // Set callback for when a bus stop is clicked on the map
        mapGestureListener.setBusStopClickCallback(new MapGestureListener.BusStopClickCallback() {
            @Override
            public void onBusStopClicked(BusStop busStop) {
                // Only show detail panel if markers are visible
                if (mapRenderer.isShowingMarkers()) {
                    Gdx.app.log("RasterMapScreen", "Map clicked bus stop: " + busStop.name);
                    detailPanel.showBusStop(busStop);
                    updateHudBoundaries(); // Update boundaries when detail panel opens
                }
            }
        });

        gestureDetector = new GestureDetector(mapGestureListener);

        if (app.inputMultiplexer != null) {
            // Add processors in priority order (first = highest priority)
            app.inputMultiplexer.addProcessor(detailPanel.getStage()); // Detail panel first
            app.inputMultiplexer.addProcessor(hudPanel.getStage()); // HUD panel second
            app.inputMultiplexer.addProcessor(gestureDetector); // Map gestures (including tap) last
        } else {
            Gdx.input.setInputProcessor(new InputMultiplexer(
                detailPanel.getStage(),
                hudPanel.getStage(),
                gestureDetector,
                app.cameraController
            ));
        }

        // Set initial HUD boundaries
        updateHudBoundaries();
    }

    /**
     * Update HUD width boundaries for map input handlers
     */
    private void updateHudBoundaries() {
        float hudPanelWidth = Gdx.graphics.getWidth() / Constants.HUD_WIDTH;
        float detailPanelWidth = detailPanel.getEffectiveWidth();
        float totalHudWidth = hudPanelWidth + detailPanelWidth;

        app.cameraController.setHudWidth(totalHudWidth);
        mapGestureListener.setHudWidth(totalHudWidth);
    }

    @Override
    public void resize(int width, int height) {
        app.viewport.update(width, height, false);
        if (hudPanel != null) {
            hudPanel.resize(width, height);
        }
        if (detailPanel != null) {
            detailPanel.resize(width, height);
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
        if (detailPanel != null && app.inputMultiplexer != null) {
            app.inputMultiplexer.removeProcessor(detailPanel.getStage());
        }
    }

    @Override
    public void dispose() {
        hide();
        if (mapRenderer != null) mapRenderer.dispose();
        if (hudPanel != null) hudPanel.dispose();
        if (detailPanel != null) detailPanel.dispose();
    }
}
