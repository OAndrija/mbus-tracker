package com.mbus.app.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;

import com.mbus.app.MBusTracker;
import com.mbus.app.assets.AssetDescriptors;
import com.mbus.app.assets.RegionNames;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.input.BusLineClickHandler;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.input.MarkerClickHandler;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.ui.HudPanel;
import com.mbus.app.ui.BusStopDetailPanel;
import com.mbus.app.utils.BusPositionCalculator;
import com.mbus.app.utils.Constants;

import java.io.IOException;
import java.util.*;

public class RasterMapScreen implements Screen {

    private final MBusTracker app;

    private Texture[] mapTiles;
    private Texture markerTexture;
    private Texture titleIcon;
    private Texture  timeIcon;
    private TextureAtlas uiAtlas;
    private ZoomXY beginTile;
    private List<BusStop> stops;
    private List<BusLine> busLines;

    private MapRenderer mapRenderer;
    private MapGestureListener mapGestureListener;
    private GestureDetector gestureDetector;
    private HudPanel hudPanel;
    private BusStopDetailPanel detailPanel;
    private Skin skin;

    private MarkerClickHandler markerClickHandler;
    private BusLineClickHandler lineClickHandler;

    private boolean animatingCamera = false;
    private float animationProgress = 0f;
    private float animationDuration = 1.5f;
    private float startX, startY, startZoom;
    private float targetX, targetY, targetZoom;

    private float timeSinceLastRefresh = 0f;
    private static final float REFRESH_INTERVAL = 30f;

    private final Geolocation CENTER_GEOLOCATION =
        new Geolocation(46.557314, 15.637771);

    public RasterMapScreen(MBusTracker app, Texture[] preloadedTiles, ZoomXY preloadedBeginTile) {
        this.app = app;
        this.mapTiles = preloadedTiles;
        this.beginTile = preloadedBeginTile;
    }

    @Override
    public void show() {
        skin = app.getAssetManager().get(AssetDescriptors.SKIN);
        markerTexture = app.getAssetManager().get(AssetDescriptors.BUS_ICON);
        titleIcon = app.getAssetManager().get(AssetDescriptors.TITLE_ICON);
        timeIcon = app.getAssetManager().get(AssetDescriptors.TIME_ICON);
        uiAtlas = app.getAssetManager().get(AssetDescriptors.BUS);

        stops = app.getBusStops();
        busLines = app.getBusLines();

        if (stops == null || busLines == null) {
            Gdx.app.error("RasterMapScreen", "Bus data not loaded! This should not happen.");
            return;
        }

        Gdx.app.log("RasterMapScreen", "Using " + stops.size() + " stops and " +
            busLines.size() + " lines from pre-loaded data");

        hudPanel = new HudPanel(skin, titleIcon);
        detailPanel = new BusStopDetailPanel(skin, timeIcon);

        hudPanel.setBusLines(busLines);
        hudPanel.setBusStops(stops);
        detailPanel.setBusLines(busLines);

        mapRenderer = new MapRenderer(app.camera);
        mapRenderer.loadTiles(mapTiles, beginTile);
        mapRenderer.setMarkerTexture(markerTexture);
        mapRenderer.setStops(stops);
        mapRenderer.setBusLines(busLines);
        mapRenderer.setVisibleLineIds(hudPanel.getVisibleLineIds());

        TextureAtlas.AtlasRegion busNorth = uiAtlas.findRegion(RegionNames.BUS_NORTH);
        TextureAtlas.AtlasRegion busNortheast = uiAtlas.findRegion(RegionNames.BUS_NORTH_EAST);
        TextureAtlas.AtlasRegion busEast = uiAtlas.findRegion(RegionNames.BUS_EAST);
        TextureAtlas.AtlasRegion busSoutheast = uiAtlas.findRegion(RegionNames.BUS_SOUTH_EAST);
        TextureAtlas.AtlasRegion busSouth = uiAtlas.findRegion(RegionNames.BUS_SOUTH);
        TextureAtlas.AtlasRegion busSouthwest = uiAtlas.findRegion(RegionNames.BUS_SOUTH_WEST);
        TextureAtlas.AtlasRegion busWest = uiAtlas.findRegion(RegionNames.BUS_WEST);
        TextureAtlas.AtlasRegion busNorthwest = uiAtlas.findRegion(RegionNames.BUS_NORTH_WEST);

        if (busNorth != null && busEast != null) {
            mapRenderer.loadBusSprites(
                busNorth, busNortheast, busEast, busSoutheast,
                busSouth, busSouthwest, busWest, busNorthwest
            );
        } else {
            Gdx.app.error("RasterMapScreen", "Failed to load bus sprites");
        }

        mapRenderer.setCurrentTime(
            BusPositionCalculator.getCurrentTimeMinutesWithSeconds(),
            BusPositionCalculator.getCurrentDayType()
        );

        hudPanel.setFilteredStopsCallback(new HudPanel.FilteredStopsCallback() {
            @Override
            public void onFilteredStopsChanged(List<BusStop> filteredStops) {
                Gdx.app.log("RasterMapScreen", "Filtered stops count: " + filteredStops.size());
                mapRenderer.setFilteredStops(filteredStops);
                markerClickHandler.setStops(filteredStops);
            }
        });

        hudPanel.setBusLineVisibilityCallback(new HudPanel.BusLineVisibilityCallback() {
            @Override
            public void onBusLineVisibilityChanged(Set<Integer> visibleLineIds) {
                Gdx.app.log("RasterMapScreen", "Visible lines: " + visibleLineIds);
                mapRenderer.setVisibleLineIds(visibleLineIds);
                if (lineClickHandler != null) {
                    lineClickHandler.setVisibleLineIds(visibleLineIds);
                }
            }
        });

        detailPanel.setVisibilityChangeCallback(new BusStopDetailPanel.VisibilityChangeCallback() {
            @Override
            public void onVisibilityChanged(boolean visible) {
                updateHudBoundaries();
                if (!visible) {
                    mapRenderer.setSelectedStop(null);
                }
            }
        });

        hudPanel.setShowAllStopsCallback(new HudPanel.ShowAllStopsCallback() {
            @Override
            public void onShowAllStopsChanged(boolean showAll) {
                Gdx.app.log("RasterMapScreen", "Show all stops: " + showAll);
                mapRenderer.setShowMarkers(showAll);
            }
        });

        hudPanel.setBusStopClickCallback(new HudPanel.BusStopClickCallback() {
            @Override
            public void onBusStopClicked(BusStop busStop) {
                Gdx.app.log("RasterMapScreen", "HUD clicked bus stop: " + busStop.name);
                mapRenderer.setSelectedStop(busStop);
                detailPanel.showBusStop(busStop);
                int currentTime = BusPositionCalculator.getCurrentTimeMinutes();
                int dayType = BusPositionCalculator.getCurrentDayType();
                String timeStr = BusPositionCalculator.formatTime(currentTime);
                int dayOfWeek = convertDayTypeToDayOfWeek(dayType);
                detailPanel.updateCurrentTime(timeStr, dayOfWeek);
                zoomToBusStop(busStop);
            }
        });

        setupInput();
    }

    private void zoomToBusStop(BusStop busStop) {
        Vector2 pos = MapRasterTiles.getPixelPosition(
            busStop.geo.lat,
            busStop.geo.lng,
            beginTile.x,
            beginTile.y
        );

        Gdx.app.log("RasterMapScreen", "Zooming to bus stop: " + busStop.name +
            " at position (" + pos.x + ", " + pos.y + ")");

        startX = app.camera.position.x;
        startY = app.camera.position.y;
        startZoom = app.camera.zoom;

        targetX = pos.x;
        targetY = pos.y;
        targetZoom = 0.1f;

        animatingCamera = true;
        animationProgress = 0f;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);

        updateHoverState();

        if (animatingCamera) {
            updateCameraAnimation(delta);
        } else {
            updateCamera();
        }

        app.viewport.apply();
        app.camera.update();

        float currentTime = BusPositionCalculator.getCurrentTimeMinutesWithSeconds();
        int dayType = BusPositionCalculator.getCurrentDayType();
        mapRenderer.setCurrentTime(currentTime, dayType);

        mapRenderer.render(delta);

        hudPanel.render();

        if (detailPanel.isVisible()) {
            int currentTimeInt = (int) currentTime;
            String timeStr = BusPositionCalculator.formatTime(currentTimeInt);
            int dayOfWeek = convertDayTypeToDayOfWeek(dayType);
            detailPanel.updateCurrentTime(timeStr, dayOfWeek);
        }

        detailPanel.render();

        timeSinceLastRefresh += delta;
        if (timeSinceLastRefresh >= REFRESH_INTERVAL) {
            timeSinceLastRefresh = 0f;
            if (detailPanel.isVisible()) {
                detailPanel.refresh();
            }
        }
    }

    private void updateHoverState() {
        int mouseX = Gdx.input.getX();
        float hudPanelWidth = Gdx.graphics.getWidth() / Constants.HUD_WIDTH;
        float detailPanelWidth = detailPanel.getEffectiveWidth();
        float totalHudWidth = hudPanelWidth + detailPanelWidth;

        if (mouseX < totalHudWidth) {
            mapRenderer.setHoveredStop(null);
            mapRenderer.setHoveredLine(null);
            return;
        }

        int mouseY = Gdx.input.getY();

        if (mapRenderer.isShowingMarkers() && !animatingCamera) {
            BusStop hoveredStop = markerClickHandler.checkMarkerClick(mouseX, mouseY);
            mapRenderer.setHoveredStop(hoveredStop);

            if (hoveredStop == null && lineClickHandler != null) {
                BusLine hoveredLine = lineClickHandler.checkLineClick(mouseX, mouseY);
                mapRenderer.setHoveredLine(hoveredLine);
            } else {
                mapRenderer.setHoveredLine(null);
            }
        } else {
            mapRenderer.setHoveredStop(null);

            if (lineClickHandler != null) {
                BusLine hoveredLine = lineClickHandler.checkLineClick(mouseX, mouseY);
                mapRenderer.setHoveredLine(hoveredLine);
            }
        }
    }

    private void updateCameraAnimation(float delta) {
        animationProgress += delta / animationDuration;

        if (animationProgress >= 1f) {
            animationProgress = 1f;
            animatingCamera = false;
            Gdx.app.log("RasterMapScreen", "Animation complete at (" +
                app.camera.position.x + ", " + app.camera.position.y +
                ", zoom=" + app.camera.zoom + ")");
        }

        float t = easeInOutCubic(animationProgress);

        app.camera.position.x = MathUtils.lerp(startX, targetX, t);
        app.camera.position.y = MathUtils.lerp(startY, targetY, t);
        app.camera.zoom = MathUtils.lerp(startZoom, targetZoom, t);

        if (animationProgress >= 1f) {
            clampCamera();
        }
    }

    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4f * t * t * t;
        } else {
            float f = t - 1f;
            return 1f + 4f * f * f * f;
        }
    }

    private void updateCamera() {
        if (!animatingCamera) {
            app.cameraController.update();
        }
        clampCamera();
    }

    private void clampCamera() {
        float effectiveViewportWidth  = app.camera.viewportWidth  * app.camera.zoom;
        float effectiveViewportHeight = app.camera.viewportHeight * app.camera.zoom;

        float minX, maxX, minY, maxY;

        if (effectiveViewportWidth >= Constants.MAP_WIDTH) {
            minX = maxX = Constants.MAP_WIDTH / 2f;
        } else {
            minX = effectiveViewportWidth / 2f;
            maxX = Constants.MAP_WIDTH - effectiveViewportWidth / 2f;
        }

        if (effectiveViewportHeight >= Constants.MAP_HEIGHT) {
            minY = maxY = Constants.MAP_HEIGHT / 2f;
        } else {
            minY = effectiveViewportHeight / 2f;
            maxY = Constants.MAP_HEIGHT - effectiveViewportHeight / 2f;
        }

        app.camera.position.x = MathUtils.clamp(app.camera.position.x, minX, maxX);
        app.camera.position.y = MathUtils.clamp(app.camera.position.y, minY, maxY);
    }

    private void setupInput() {
        mapGestureListener = new MapGestureListener(app.camera);

        markerClickHandler = new MarkerClickHandler(app.camera);
        markerClickHandler.setStops(stops);
        markerClickHandler.setBeginTile(beginTile);
        mapGestureListener.setMarkerClickHandler(markerClickHandler);

        lineClickHandler = new BusLineClickHandler(app.camera);
        lineClickHandler.setBusLines(busLines);
        lineClickHandler.setBeginTile(beginTile);
        lineClickHandler.setVisibleLineIds(hudPanel.getVisibleLineIds());
        mapGestureListener.setLineClickHandler(lineClickHandler);

        mapGestureListener.setBusStopClickCallback(new MapGestureListener.BusStopClickCallback() {
            @Override
            public void onBusStopClicked(BusStop busStop) {
                if (mapRenderer.isShowingMarkers()) {
                    Gdx.app.log("RasterMapScreen", "Map clicked bus stop: " + busStop.name);
                    mapRenderer.setSelectedStop(busStop);
                    detailPanel.showBusStop(busStop);
                    int currentTime = BusPositionCalculator.getCurrentTimeMinutes();
                    int dayType = BusPositionCalculator.getCurrentDayType();
                    String timeStr = BusPositionCalculator.formatTime(currentTime);
                    int dayOfWeek = convertDayTypeToDayOfWeek(dayType);
                    detailPanel.updateCurrentTime(timeStr, dayOfWeek);
                    updateHudBoundaries();
                }
            }
        });

        mapGestureListener.setBusLineClickCallback(new MapGestureListener.BusLineClickCallback() {
            @Override
            public void onBusLineClicked(BusLine busLine) {
                if (mapRenderer.getSelectedLine() == busLine) {
                    Gdx.app.log("RasterMapScreen", "Deselecting bus line: " + busLine.lineId);
                    mapRenderer.setSelectedLine(null);

                    hudPanel.selectAllLines();
                    hudPanel.setShowAllStops(true);
                } else {
                    Gdx.app.log("RasterMapScreen", "Map clicked bus line: " + busLine.lineId);
                    mapRenderer.setSelectedLine(busLine);
                    mapRenderer.setSelectedStop(null);

                    hudPanel.selectOnlyLine(busLine.lineId);

                    hudPanel.setShowAllStops(true);
                }
            }
        });

        gestureDetector = new GestureDetector(mapGestureListener);

        if (app.inputMultiplexer != null) {
            app.inputMultiplexer.addProcessor(detailPanel.getStage());
            app.inputMultiplexer.addProcessor(hudPanel.getStage());
            app.inputMultiplexer.addProcessor(gestureDetector);
        } else {
            Gdx.input.setInputProcessor(new InputMultiplexer(
                detailPanel.getStage(),
                hudPanel.getStage(),
                gestureDetector,
                app.cameraController
            ));
        }

        updateHudBoundaries();
    }

    private void updateHudBoundaries() {
        float hudPanelWidth = Gdx.graphics.getWidth() / Constants.HUD_WIDTH;
        float detailPanelWidth = detailPanel.getEffectiveWidth();
        float totalHudWidth = hudPanelWidth + detailPanelWidth;

        app.cameraController.setHudWidth(totalHudWidth);
        mapGestureListener.setHudWidth(totalHudWidth);
    }

    private int convertDayTypeToDayOfWeek(int dayType) {
        switch (dayType) {
            case 0: return 1;
            case 1: return 6;
            case 2: return 7;
            default: return 1;
        }
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
