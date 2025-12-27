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
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.input.BusLineClickHandler;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.input.MarkerClickHandler;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.ui.HudPanel;
import com.mbus.app.ui.BusStopDetailPanel;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.BusLineStopRelationshipBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class RasterMapScreen implements Screen {

    private final MBusTracker app;

    private Texture[] mapTiles;
    private Texture markerTexture;
    private Texture titleIcon;
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

    // Camera animation
    private boolean animatingCamera = false;
    private float animationProgress = 0f;
    private float animationDuration = 1.5f;
    private float startX, startY, startZoom;
    private float targetX, targetY, targetZoom;

    private final Geolocation CENTER_GEOLOCATION =
        new Geolocation(46.557314, 15.637771);

    public RasterMapScreen(MBusTracker app) {
        this.app = app;
    }

    @Override
    public void show() {
        // Load skin and marker texture from AssetManager
        skin = app.getAssetManager().get(AssetDescriptors.SKIN);
        markerTexture = app.getAssetManager().get(AssetDescriptors.BUS_ICON);
        titleIcon = app.getAssetManager().get(AssetDescriptors.TITLE_ICON);

        loadTiles();
        loadData();

        // Create HUD panels FIRST
        hudPanel = new HudPanel(skin, titleIcon);
        detailPanel = new BusStopDetailPanel(skin);

        // Set bus lines data in HUD panel
        hudPanel.setBusLines(busLines);
        hudPanel.setBusStops(stops);

        // NOW create mapRenderer
        mapRenderer = new MapRenderer(app.camera);
        mapRenderer.loadTiles(mapTiles, beginTile);
        mapRenderer.setMarkerTexture(markerTexture);
        mapRenderer.setStops(stops);
        mapRenderer.setBusLines(busLines);
        mapRenderer.setVisibleLineIds(hudPanel.getVisibleLineIds());

        // Add callback for filtered stops changes
        hudPanel.setFilteredStopsCallback(new HudPanel.FilteredStopsCallback() {
            @Override
            public void onFilteredStopsChanged(List<BusStop> filteredStops) {
                Gdx.app.log("RasterMapScreen", "Filtered stops count: " + filteredStops.size());
                mapRenderer.setFilteredStops(filteredStops);
                markerClickHandler.setStops(filteredStops);
            }
        });

        // Add callback for bus line visibility changes
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

        // Set callback for when detail panel visibility changes
        detailPanel.setVisibilityChangeCallback(new BusStopDetailPanel.VisibilityChangeCallback() {
            @Override
            public void onVisibilityChanged(boolean visible) {
                updateHudBoundaries();
                if (!visible) {
                    mapRenderer.setSelectedStop(null);
                }
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
                mapRenderer.setSelectedStop(busStop);
                detailPanel.showBusStop(busStop);
                zoomToBusStop(busStop);
            }
        });

        setupInput();
    }

    private void loadData() {
        List<BusStop> rawStops = GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json");
        List<BusLine> rawLines = GeoJSONLoader.loadBusLinesFromFile("data/int_mob_marprom_linije.json");

        Gdx.app.log("RasterMapScreen", "Loaded " + rawStops.size() + " raw bus stops and " +
            rawLines.size() + " raw bus lines");

        double proximityThreshold = 50.0;

        BusLineStopRelationshipBuilder.RelationshipResult result =
            BusLineStopRelationshipBuilder.buildRelationships(rawLines, rawStops, proximityThreshold);

        this.busLines = result.lines;
        this.stops = result.stops;

        // Log statistics
        int totalStopsWithLines = 0;
        int totalLinesWithStops = 0;

        for (BusStop stop : stops) {
            if (stop.getLineCount() > 0) {
                totalStopsWithLines++;
            }
        }

        for (BusLine line : busLines) {
            if (line.getStopCount() > 0) {
                totalLinesWithStops++;
            }
        }

        Gdx.app.log("RasterMapScreen",
            "Built relationships: " + totalStopsWithLines + "/" + stops.size() + " stops have lines");
        Gdx.app.log("RasterMapScreen",
            totalLinesWithStops + "/" + busLines.size() + " lines have stops");
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

        mapRenderer.render(delta);

        hudPanel.render();
        detailPanel.render();
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

        // Check for marker hover first (priority over lines)
        if (mapRenderer.isShowingMarkers() && !animatingCamera) {
            BusStop hoveredStop = markerClickHandler.checkMarkerClick(mouseX, mouseY);
            mapRenderer.setHoveredStop(hoveredStop);

            // Only check line hover if no marker is hovered
            if (hoveredStop == null && lineClickHandler != null) {
                BusLine hoveredLine = lineClickHandler.checkLineClick(mouseX, mouseY);
                mapRenderer.setHoveredLine(hoveredLine);
            } else {
                mapRenderer.setHoveredLine(null);
            }
        } else {
            mapRenderer.setHoveredStop(null);

            // Check for line hover even when markers are hidden
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

        // Set callback for when a bus stop is clicked on the map
        mapGestureListener.setBusStopClickCallback(new MapGestureListener.BusStopClickCallback() {
            @Override
            public void onBusStopClicked(BusStop busStop) {
                if (mapRenderer.isShowingMarkers()) {
                    Gdx.app.log("RasterMapScreen", "Map clicked bus stop: " + busStop.name);
                    mapRenderer.setSelectedStop(busStop);
                    mapRenderer.setSelectedLine(null); // Clear line selection
                    detailPanel.showBusStop(busStop);
                    updateHudBoundaries();
                }
            }
        });

        // Set callback for when a bus line is clicked on the map
        mapGestureListener.setBusLineClickCallback(new MapGestureListener.BusLineClickCallback() {
            @Override
            public void onBusLineClicked(BusLine busLine) {
                // Toggle selection - if clicking the same line, deselect it
                if (mapRenderer.getSelectedLine() == busLine) {
                    Gdx.app.log("RasterMapScreen", "Deselecting bus line: " + busLine.lineId);
                    mapRenderer.setSelectedLine(null);
                } else {
                    Gdx.app.log("RasterMapScreen", "Map clicked bus line: " + busLine.lineId);
                    mapRenderer.setSelectedLine(busLine);
                    mapRenderer.setSelectedStop(null); // Clear stop selection
                    // TODO: You could show a detail panel for the line here
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
