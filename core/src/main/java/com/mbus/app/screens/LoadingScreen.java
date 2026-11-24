package com.mbus.app.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.MBusTracker;
import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Geolocation;
import com.mbus.app.utils.ZoomXY;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.systems.map.TileOverview;
import com.mbus.app.systems.streets.Streets;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.Display;
import com.mbus.app.utils.Texts;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadingScreen implements Screen {
    private static final String TAG = "LoadingScreen";

    private final MBusTracker app;
    private ScreenViewport viewport;
    private Stage stage;

    private Label statusLabel;
    private ProgressBar progressBar;

    private float progress = 0f;

    private Thread dataLoadingThread;
    private volatile boolean dataLoadingComplete = false;
    private volatile Timetable timetable;
    private volatile Streets streets;

    private ZoomXY beginTile;
    private Pixmap overview;
    private int totalTiles;
    private ExecutorService tileExecutor;
    // Counts tiles that could not be loaded too, so a missing tile cannot block startup.
    private final AtomicInteger tilesProcessed = new AtomicInteger();
    private volatile String dataError = null;

    private static final Geolocation CENTER_GEOLOCATION =
        new Geolocation(46.557314, 15.637771);

    public LoadingScreen(MBusTracker app) {
        this.app = app;
    }

    @Override
    public void show() {
        Theme theme = app.getTheme();
        viewport = new ScreenViewport();
        viewport.setUnitsPerPixel(1f / Display.getWindowScale());
        stage = new Stage(viewport);

        ProgressBar.ProgressBarStyle barStyle = new ProgressBar.ProgressBarStyle();
        barStyle.background = theme.roundRect(3, Palette.BORDER);
        barStyle.background.setMinHeight(6);
        barStyle.knobBefore = theme.roundRect(3, Palette.BRAND);
        barStyle.knobBefore.setMinHeight(6);
        progressBar = new ProgressBar(0f, 1f, 0.01f, false, barStyle);
        progressBar.setAnimateDuration(0.15f);

        statusLabel = theme.label("", theme.body, Palette.MUTED);

        Table root = new Table();
        root.setFillParent(true);
        root.add(new Image(theme.logo(72))).size(72).padBottom(20).row();
        root.add(theme.label("MBus", theme.display, Palette.INK)).row();
        root.add(theme.label("Maribor", theme.body, Palette.MUTED)).padBottom(36).row();
        root.add(progressBar).width(280).height(6).padBottom(16).row();
        root.add(statusLabel);
        stage.addActor(root);

        startTileLoading();
        startDataLoading();
    }

    private void startTileLoading() {
        try {
            final ZoomXY centerTile = MapRasterTiles.getTileNumber(
                CENTER_GEOLOCATION.lat,
                CENTER_GEOLOCATION.lng,
                Constants.ZOOM
            );

            final int size = Constants.NUM_TILES;
            totalTiles = size * size;

            beginTile = new ZoomXY(
                Constants.ZOOM,
                centerTile.x - ((size - 1) / 2),
                centerTile.y - ((size - 1) / 2)
            );

            overview = TileOverview.create();

            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            tileExecutor = Executors.newFixedThreadPool(threads, task -> {
                Thread thread = new Thread(task, "tile-loader");
                thread.setDaemon(true);
                return thread;
            });

            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    final int tileColumn = column;
                    final int tileRow = row;
                    tileExecutor.execute(() -> loadTile(tileColumn, tileRow));
                }
            }
            tileExecutor.shutdown();

        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to start tile loading", e);
            dataError = e.getMessage();
        }
    }

    private void loadTile(int column, int row) {
        try {
            byte[] data = MapRasterTiles.getTileData(
                Constants.ZOOM, beginTile.x + column, beginTile.y + row);
            TileOverview.addTile(overview, data, column, row);
        } catch (Exception e) {
            Gdx.app.error(TAG, "Tile " + column + "," + row + " not available: " + e.getMessage());
        } finally {
            tilesProcessed.incrementAndGet();
        }
    }

    /** The app is usable without them, with walks as straight lines and without addresses. */
    private static Streets loadStreets() {
        try {
            return Streets.load(Gdx.files.internal("data/maribor_streets.txt.gz").read());
        } catch (Exception e) {
            Gdx.app.error(TAG, "Streets not available: " + e.getMessage());
            return null;
        }
    }

    private void startDataLoading() {
        dataLoadingThread = new Thread(() -> {
            try {
                timetable = app.getTimetableFiles().load();
                if (app.getStreets() == null) streets = loadStreets();
                dataLoadingComplete = true;
            } catch (final Exception e) {
                Gdx.app.error(TAG, "Error loading the timetable", e);
                dataError = String.valueOf(e.getMessage());
            }
        });
        dataLoadingThread.setDaemon(true);
        dataLoadingThread.start();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Palette.SURFACE);

        int tilesLoaded = tilesProcessed.get();
        float tileProgress = totalTiles > 0 ? (float) tilesLoaded / totalTiles : 0f;
        float dataProgress = dataLoadingComplete ? 1f : 0f;

        progress = (tileProgress * 0.4f) + (dataProgress * 0.6f);

        progressBar.setValue(Math.max(progress, 0.04f)); // never narrower than its round ends
        if (dataError != null) {
            statusLabel.setText(Texts.format("loadingFailed", dataError));
        } else if (tilesLoaded < totalTiles) {
            statusLabel.setText(Texts.format("loadingMap", Math.round(progress * 100)));
        } else {
            statusLabel.setText(Texts.format("loadingTimetable", Math.round(progress * 100)));
        }

        stage.act(delta);
        stage.draw();

        if (dataError == null && dataLoadingComplete && tilesLoaded >= totalTiles) {
            Gdx.app.log(TAG, "Everything loaded, transitioning to map screen");
            app.setTimetable(timetable);
            if (streets != null) app.setStreets(streets);
            app.setScreen(new RasterMapScreen(app, overview, beginTile));
            dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.setUnitsPerPixel(1f / Display.getWindowScale());
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        if (dataLoadingThread != null && dataLoadingThread.isAlive()) {
            try {
                dataLoadingThread.join(1000);
            } catch (InterruptedException e) {
                Gdx.app.error(TAG, "Interrupted while waiting for data loading thread", e);
            }
        }

        if (tileExecutor != null) {
            tileExecutor.shutdownNow();
            try {
                tileExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Gdx.app.error(TAG, "Interrupted while waiting for tile loading", e);
            }
        }
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        // The overview belongs to the map screen. If loading was cut short it is left alone, since a
        // worker may still be writing to it and the app is closing anyway.
    }
}
