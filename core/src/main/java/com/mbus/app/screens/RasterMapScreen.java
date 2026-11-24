package com.mbus.app.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;

import com.mbus.app.MBusTracker;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.utils.ZoomXY;
import com.mbus.app.systems.input.BusLineClickHandler;
import com.mbus.app.systems.input.MapGestureListener;
import com.mbus.app.systems.map.MapRenderer;
import com.mbus.app.systems.map.MapTileLayer;
import com.mbus.app.systems.map.MarkerCluster;
import com.mbus.app.systems.map.WorldGeometry;
import com.mbus.app.ui.MapUi;
import com.mbus.app.ui.Sidebar;
import com.mbus.app.ui.StopPanel;
import com.mbus.app.ui.TripPanel;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.Settings;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class RasterMapScreen implements Screen {

    private static final float STOP_ZOOM = 0.08f;
    private static final float FOLLOW_SMOOTHING = 5f;
    private static final float BUTTON_ZOOM_STEP = 1.5f;

    private final MBusTracker app;
    private final Pixmap overview;
    private final ZoomXY beginTile;
    private final AppClock clock = new AppClock();

    private Timetable timetable;
    private WorldGeometry geometry;
    private MapRenderer mapRenderer;
    private MapUi ui;

    private GestureDetector gestureDetector;
    private BusLineClickHandler lineClickHandler;
    private InputProcessor shortcuts;

    private Trip selectedTrip;
    private LocalDate selectedTripDate;
    /** The stop to go back to when the trip is closed, or null. */
    private BusStop tripStop;
    private boolean following = false;

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
        timetable = app.getTimetable();

        geometry = new WorldGeometry(timetable, beginTile);
        mapRenderer = new MapRenderer(app.camera, app.getTheme());
        mapRenderer.setMap(new MapTileLayer(app.camera, beginTile, overview), geometry);
        mapRenderer.setStops(timetable.getStops());
        mapRenderer.setBusLines(timetable.getLines());

        Settings settings = app.getSettings();
        ui = new MapUi(app.getTheme(), timetable, clock, settings, createSidebarListener(),
            createStopListener(), createTripListener(), createMapActions());
        mapRenderer.setVisibleLines(ui.getSidebar().getVisibleLines());
        mapRenderer.setFilteredStops(ui.getSidebar().getStopsOnVisibleLines());
        mapRenderer.setShowMarkers(settings.isShowStops());

        if (settings.hasCamera()) {
            app.camera.zoom = app.cameraController.clampZoom(settings.getCameraZoom());
            app.camera.position.set(settings.getCameraX(), settings.getCameraY(), 0);
            clampCamera();
        }

        setupInput();
    }

    private Sidebar.Listener createSidebarListener() {
        return new Sidebar.Listener() {
            @Override
            public void onVisibleLinesChanged(Set<BusLine> visibleLines, List<BusStop> stopsOnThem) {
                mapRenderer.setVisibleLines(visibleLines);
                mapRenderer.setFilteredStops(stopsOnThem);
                lineClickHandler.setVisibleLines(visibleLines);

                BusLine selected = mapRenderer.getSelectedLine();
                if (selected != null
                    && (visibleLines.size() != 1 || !visibleLines.contains(selected))) {
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

            @Override
            public void onFavoriteChanged(BusStop stop) {
                if (ui.getStopPanel().isOpen() && ui.getStopPanel().getStop() == stop) {
                    ui.getStopPanel().show(stop);
                }
            }
        };
    }

    private StopPanel.Listener createStopListener() {
        return new StopPanel.Listener() {
            @Override
            public void onStopPanelClosed() {
                selectStop(null);
            }

            @Override
            public void onFavoriteChanged() {
                ui.getSidebar().refreshFavorites();
            }

            @Override
            public void onDepartureChosen(Trip trip, LocalDate date) {
                selectTrip(trip, date, ui.getStopPanel().getStop());
            }
        };
    }

    private TripPanel.Listener createTripListener() {
        return new TripPanel.Listener() {
            @Override
            public void onTripPanelClosed() {
                closeTrip();
            }

            @Override
            public void onTripStopChosen(BusStop stop) {
                selectStop(stop);
                zoomToBusStop(stop);
            }

            @Override
            public void onFollowChanged(boolean follow) {
                setFollowing(follow);
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
                setFollowing(false);
                flyTo(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0.4f);
            }

            @Override
            public void toggleTimeBar() {
                if (ui.getTimeBar().isOpen()) {
                    ui.getTimeBar().close();
                    clock.goLive();
                } else {
                    ui.getTimeBar().show();
                    ui.layout();
                }
            }
        };
    }

    private void zoomAtCenter(float factor) {
        int x = (int) (ui.getCoveredWidth() + Gdx.graphics.getWidth()) / 2;
        app.cameraController.zoomBy(factor, x, Gdx.graphics.getHeight() / 2);
    }

    /** Shows a stop's departures, or closes them when given null. */
    private void selectStop(BusStop stop) {
        clearTrip();
        mapRenderer.setSelectedStop(stop);
        ui.getSidebar().setSelectedStop(stop);
        if (stop != null) {
            ui.getStopPanel().show(stop);
        } else {
            ui.getStopPanel().close();
        }
    }

    /** Shows one trip. The stop is where it was chosen, or null. */
    private void selectTrip(Trip trip, LocalDate date, BusStop fromStop) {
        setFollowing(false);
        selectedTrip = trip;
        selectedTripDate = date;
        tripStop = fromStop;
        if (fromStop == null) {
            mapRenderer.setSelectedStop(null);
            ui.getSidebar().setSelectedStop(null);
        }
        ui.getStopPanel().close();
        ui.getTripPanel().show(trip, date, fromStop != null, false);
        updateSelectedTrip();
    }

    /** The highlighted bus of a trip is only there on the day of the trip. */
    private void updateSelectedTrip() {
        boolean today = selectedTrip != null && clock.getDate().equals(selectedTripDate);
        mapRenderer.setSelectedTrip(selectedTrip, today);
        if (following && (!today || mapRenderer.getBusPosition(selectedTrip) == null)) {
            setFollowing(false);
        }
    }

    private void clearTrip() {
        setFollowing(false);
        selectedTrip = null;
        selectedTripDate = null;
        tripStop = null;
        mapRenderer.setSelectedTrip(null, false);
        ui.getTripPanel().close();
    }

    private void closeTrip() {
        selectStop(tripStop);
    }

    private void setFollowing(boolean follow) {
        following = follow && selectedTrip != null;
        if (following) animatingCamera = false;
        ui.getTripPanel().setFollowing(following);
    }

    private void followBus(float delta) {
        Vector2 bus = mapRenderer.getBusPosition(selectedTrip);
        if (bus == null) return;
        float worldPerPixel = app.camera.viewportWidth * app.camera.zoom / Gdx.graphics.getWidth();
        float x = bus.x - ui.getCoveredWidth() / 2f * worldPerPixel;
        float t = 1f - (float) Math.exp(-FOLLOW_SMOOTHING * delta);
        app.camera.position.x += (x - app.camera.position.x) * t;
        app.camera.position.y += (bus.y - app.camera.position.y) * t;
        clampCamera();
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

        clock.update();
        updateSelectedTrip();
        updateHoverState();

        if (animatingCamera) {
            updateCameraAnimation(delta);
        } else {
            updateCamera(delta);
        }
        if (following) followBus(delta);

        app.viewport.apply();
        app.camera.update();

        mapRenderer.setTrips(timetable.getTrips(clock.getDate()), clock.getMinutes());
        mapRenderer.render(delta);

        ui.render(delta);
    }

    private void updateHoverState() {
        int x = Gdx.input.getX();
        int y = Gdx.input.getY();

        Trip trip = null;
        BusStop stop = null;
        BusLine line = null;
        boolean overCluster = false;

        if (!animatingCamera && !ui.isOver(x, y)) {
            trip = mapRenderer.findBus(x, y);
            overCluster = trip == null && mapRenderer.findCluster(x, y) != null;
            if (trip == null && !overCluster) {
                stop = mapRenderer.findStop(x, y);
                if (stop == null) line = lineClickHandler.checkLineClick(x, y);
            }
        }

        mapRenderer.setHoveredTrip(trip);
        mapRenderer.setHoveredStop(stop);
        mapRenderer.setHoveredLine(line);
        ui.updatePointer(trip, stop, line, overCluster);
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
        lineClickHandler.setBusLines(timetable.getLines());
        lineClickHandler.setGeometry(geometry);
        lineClickHandler.setVisibleLines(ui.getSidebar().getVisibleLines());
        gestures.setLineClickHandler(lineClickHandler);

        gestures.setStopFinder(mapRenderer::findStop);
        gestures.setBusStopClickCallback(this::selectStop);

        gestures.setBusLineClickCallback(busLine -> {
            if (mapRenderer.getSelectedLine() == busLine) {
                mapRenderer.setSelectedLine(null);
                ui.getSidebar().showAllLines();
            } else {
                ui.getSidebar().showOnlyLine(busLine);
                mapRenderer.setSelectedLine(busLine);
            }
        });

        gestures.setBusClickHandler((x, y) -> {
            Trip trip = mapRenderer.findBus(x, y);
            if (trip == null) return false;
            selectTrip(trip, clock.getDate(), null);
            return true;
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
            setFollowing(false);
        });
        gestures.setUiHitTest(ui::isOver);

        app.cameraController.setUserInputListener(cancelAnimation);
        app.cameraController.setUiHitTest(ui::isOver);

        gestureDetector = new GestureDetector(gestures);
        gestureDetector.setTapSquareSize(8f);

        app.inputMultiplexer.addProcessor(app.cameraController);
        app.inputMultiplexer.addProcessor(ui.getStage());
        app.inputMultiplexer.addProcessor(gestureDetector);
        shortcuts = createShortcuts();
        app.inputMultiplexer.addProcessor(shortcuts);
    }

    private InputProcessor createShortcuts() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) || Gdx.input.isKeyPressed(Input.Keys.SYM);
                if (control && keycode == Input.Keys.F) {
                    ui.getSidebar().startSearch();
                    return true;
                }
                if (control || ui.getSidebar().isTyping()) return false;

                switch (keycode) {
                    case Input.Keys.ESCAPE:
                        if (ui.getTripPanel().isOpen()) closeTrip();
                        else if (ui.getStopPanel().isOpen()) selectStop(null);
                        else if (ui.getTimeBar().isOpen()) createMapActions().toggleTimeBar();
                        return true;
                    case Input.Keys.F:
                        if (ui.getStopPanel().isOpen()) ui.getStopPanel().toggleFavorite();
                        return true;
                    case Input.Keys.T:
                        createMapActions().toggleTimeBar();
                        return true;
                    case Input.Keys.HOME:
                        createMapActions().showWholeNetwork();
                        return true;
                    default:
                        return false;
                }
            }
        };
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
        app.inputMultiplexer.removeProcessor(shortcuts);
        app.getSettings().setCamera(app.camera.position.x, app.camera.position.y, app.camera.zoom);
    }

    @Override
    public void dispose() {
        hide();
        mapRenderer.dispose();
        ui.dispose();
    }
}
