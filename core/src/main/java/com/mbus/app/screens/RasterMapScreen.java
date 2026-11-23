package com.mbus.app.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;

import com.mbus.app.MBusTracker;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.ZoomXY;
import com.mbus.app.systems.input.BusLineClickHandler;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.systems.map.MapTileLayer;
import com.mbus.app.systems.map.MarkerCluster;
import com.mbus.app.systems.map.WorldGeometry;
import com.mbus.app.ui.MapUi;
import com.mbus.app.ui.Sidebar;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.utils.BusPositionCalculator;
import com.mbus.app.utils.CityClock;
import com.mbus.app.utils.Constants;

import java.util.List;
import java.util.Set;

public class RasterMapScreen implements Screen {

    private static final float STOP_ZOOM = 0.08f;
    private static final float BUTTON_ZOOM_STEP = 1.5f;

    private final MBusTracker app;
    private final Pixmap overview;
    private final ZoomXY beginTile;
    private final CityClock clock = new CityClock();

    private List<BusStop> stops;
    private List<BusLine> busLines;
    private WorldGeometry geometry;
    private MapRenderer mapRenderer;
    private MapUi ui;

    private GestureDetector gestureDetector;
    private BusLineClickHandler lineClickHandler;

    private boolean animatingCamera = false;
    private float animationProgress = 0f;
    private final float animationDuration = 1.0f;
    private float startX, startY, startZoom;
    private float targetX, targetY, targetZoom;

    public RasterMapScreen(MBusTracker app, Pixmap overview, ZoomXY preloadedBeginTile) {
        this.app = app;
        this.overview = overview;
        this.beginTile = preloadedBeginTile;
    }

    @Override
    public void show() {
        stops = app.getBusStops();
        busLines = app.getBusLines();

        geometry = new WorldGeometry(stops, busLines, beginTile);
        mapRenderer = new MapRenderer(app.camera, app.getTheme());
        mapRenderer.setMap(new MapTileLayer(app.camera, beginTile, overview), geometry);
        mapRenderer.setStops(stops);
        mapRenderer.setBusLines(busLines);

        ui = new MapUi(app.getTheme(), stops, busLines, createSidebarListener(), createMapActions(),
            () -> selectStop(null));
        mapRenderer.setVisibleLineIds(ui.getSidebar().getVisibleLineIds());

        setupInput();
    }

    private Sidebar.Listener createSidebarListener() {
        return new Sidebar.Listener() {
            @Override
            public void onVisibleLinesChanged(Set<Integer> visibleLineIds, List<BusStop> stopsOnThem) {
                mapRenderer.setVisibleLineIds(visibleLineIds);
                mapRenderer.setFilteredStops(stopsOnThem);
                lineClickHandler.setVisibleLineIds(visibleLineIds);

                BusLine selected = mapRenderer.getSelectedLine();
                if (selected != null
                    && (visibleLineIds.size() != 1 || !visibleLineIds.contains(selected.lineId))) {
                    mapRenderer.setSelectedLine(null);
                }
            }

            @Override
            public void onShowStopsChanged(boolean show) {
                mapRenderer.setShowMarkers(show);
            }

            @Override
            public void onStopChosen(BusStop stop) {
                selectStop(stop);
                zoomToBusStop(stop);
            }

            @Override
            public void onCollapsedChanged() {
                ui.layout();
            }
        };
    }

    private MapUi.MapActions createMapActions() {
        return new MapUi.MapActions() {
            @Override
            public void zoomIn() {
                zoomAtCenter(1f / BUTTON_ZOOM_STEP);
            }

            @Override
            public void zoomOut() {
                zoomAtCenter(BUTTON_ZOOM_STEP);
            }

            @Override
            public void showWholeNetwork() {
                flyTo(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0.4f);
            }
        };
    }

    private void zoomAtCenter(float factor) {
        int x = (int) (ui.getCoveredWidth() + Gdx.graphics.getWidth()) / 2;
        app.cameraController.zoomBy(factor, x, Gdx.graphics.getHeight() / 2);
    }

    /** Shows a stop's arrivals, or closes them when given null. */
    private void selectStop(BusStop stop) {
        mapRenderer.setSelectedStop(stop);
        ui.getSidebar().setSelectedStop(stop);
        if (stop != null) {
            ui.getStopPanel().show(stop);
        }
    }

    private void zoomToBusStop(BusStop busStop) {
        Vector2 pos = geometry.getStop(busStop);
        flyTo(pos.x, pos.y, STOP_ZOOM);
    }

    /** Moves a point to the center of the part of the map that is not covered by panels. */
    private void flyTo(float x, float y, float zoom) {
        app.cameraController.stopZooming();

        startX = app.camera.position.x;
        startY = app.camera.position.y;
        startZoom = app.camera.zoom;

        targetZoom = app.cameraController.clampZoom(zoom);
        float worldPerPixel = app.camera.viewportWidth * targetZoom / Gdx.graphics.getWidth();
        targetX = x - ui.getCoveredWidth() / 2f * worldPerPixel;
        targetY = y;

        animatingCamera = true;
        animationProgress = 0f;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Palette.MAP_BACKGROUND);

        updateHoverState();

        if (animatingCamera) {
            updateCameraAnimation(delta);
        } else {
            updateCamera(delta);
        }

        app.viewport.apply();
        app.camera.update();

        mapRenderer.setCurrentTime(
            clock.minutes(),
            BusPositionCalculator.getCurrentDayType());
        mapRenderer.render(delta);

        ui.render(delta);
    }

    private void updateHoverState() {
        int x = Gdx.input.getX();
        int y = Gdx.input.getY();

        BusStop stop = null;
        BusLine line = null;
        boolean overCluster = false;

        if (!animatingCamera && !ui.isOver(x, y)) {
            overCluster = mapRenderer.findCluster(x, y) != null;
            if (!overCluster) {
                stop = mapRenderer.findStop(x, y);
                if (stop == null) line = lineClickHandler.checkLineClick(x, y);
            }
        }

        mapRenderer.setHoveredStop(stop);
        mapRenderer.setHoveredLine(line);
        ui.updatePointer(stop, line, overCluster);
    }

    private void updateCameraAnimation(float delta) {
        animationProgress += delta / animationDuration;

        if (animationProgress >= 1f) {
            animationProgress = 1f;
            animatingCamera = false;
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

    private void updateCamera(float delta) {
        app.cameraController.setKeyboardEnabled(!ui.getSidebar().isTyping());
        app.cameraController.update(delta);
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
        MapGestureListener gestures = new MapGestureListener(app.camera);

        lineClickHandler = new BusLineClickHandler(app.camera);
        lineClickHandler.setBusLines(busLines);
        lineClickHandler.setGeometry(geometry);
        lineClickHandler.setVisibleLineIds(ui.getSidebar().getVisibleLineIds());
        gestures.setLineClickHandler(lineClickHandler);

        gestures.setStopFinder(mapRenderer::findStop);
        gestures.setBusStopClickCallback(this::selectStop);

        gestures.setBusLineClickCallback(busLine -> {
            if (mapRenderer.getSelectedLine() == busLine) {
                mapRenderer.setSelectedLine(null);
                ui.getSidebar().showAllLines();
            } else {
                ui.getSidebar().showOnlyLine(busLine.lineId);
                mapRenderer.setSelectedLine(busLine);
            }
        });

        gestures.setClusterClickHandler((x, y) -> {
            MarkerCluster cluster = mapRenderer.findCluster(x, y);
            if (cluster == null) return false;
            flyTo(cluster.getPosition().x, cluster.getPosition().y, app.camera.zoom * 0.5f);
            return true;
        });

        Runnable cancelAnimation = () -> animatingCamera = false;
        gestures.setMapTouchListener(() -> ui.getSidebar().stopTyping());
        gestures.setPanListener(() -> {
            cancelAnimation.run();
            app.cameraController.stopZooming();
        });
        gestures.setUiHitTest(ui::isOver);

        app.cameraController.setUserInputListener(cancelAnimation);
        app.cameraController.setUiHitTest(ui::isOver);

        gestureDetector = new GestureDetector(gestures);
        gestureDetector.setTapSquareSize(8f);

        app.inputMultiplexer.addProcessor(app.cameraController);
        app.inputMultiplexer.addProcessor(ui.getStage());
        app.inputMultiplexer.addProcessor(gestureDetector);
    }

    @Override
    public void resize(int width, int height) {
        app.viewport.update(width, height, false);
        ui.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        app.inputMultiplexer.removeProcessor(app.cameraController);
        app.inputMultiplexer.removeProcessor(ui.getStage());
        app.inputMultiplexer.removeProcessor(gestureDetector);
    }

    @Override
    public void dispose() {
        hide();
        mapRenderer.dispose();
        ui.dispose();
    }
}
