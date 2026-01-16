package com.mbus.app.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.MBusTracker;
import com.mbus.app.assets.AssetDescriptors;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusSchedule;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.data.GeoJSONLoader;
import com.mbus.app.systems.data.ScheduleLoader;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.utils.BusLineStopRelationshipBuilder;
import com.mbus.app.utils.Constants;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadingScreen implements Screen {
    private static final String TAG = "LoadingScreen";

    private final MBusTracker app;
    private Stage stage;
    private Skin skin;

    private Table rootTable;
    private Label titleLabel;
    private Label statusLabel;
    private Label percentLabel;
    private ProgressBar progressBar;

    private float progress = 0f;

    // Data loading (background thread)
    private Thread dataLoadingThread;
    private volatile boolean dataLoadingComplete = false;
    private List<BusStop> loadedStops;
    private List<BusLine> loadedLines;

    // Tile loading (main thread + background downloading)
    private ZoomXY beginTile;
    private Texture[] mapTiles;
    private int totalTiles;
    private int tilesLoaded = 0;
    private boolean tileLoadingStarted = false;
    private Thread tileDownloadThread;

    // Queue for tile data from background thread
    private static class TileData {
        final int index;
        final byte[] data;

        TileData(int index, byte[] data) {
            this.index = index;
            this.data = data;
        }
    }

    private final ConcurrentLinkedQueue<TileData> tileDataQueue = new ConcurrentLinkedQueue<TileData>();
    private final AtomicBoolean tilesDownloadComplete = new AtomicBoolean(false);

    private static final Geolocation CENTER_GEOLOCATION =
        new Geolocation(46.557314, 15.637771);

    public LoadingScreen(MBusTracker app) {
        this.app = app;
    }

    @Override
    public void show() {
        // Get skin from asset manager
        skin = app.getAssetManager().get(AssetDescriptors.SKIN);

        // Create stage
        stage = new Stage(new ScreenViewport());

        // Create root table with maroon-panel background
        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.setBackground(skin.getDrawable("panel-maroon"));
        stage.addActor(rootTable);

        // Create UI elements
        titleLabel = new Label("MBus", skin, "title");
        statusLabel = new Label("Initializing...", skin);
        percentLabel = new Label("0%", skin);

        // Create progress bar
        progressBar = new ProgressBar(0f, 1f, 0.01f, false, skin);
        progressBar.setAnimateDuration(0.1f);

        // Layout
        rootTable.add(titleLabel).padBottom(40).row();
        rootTable.add(progressBar).width(400).height(30).padBottom(10).row();
        rootTable.add(percentLabel).padBottom(20).row();
        rootTable.add(statusLabel).row();

        // Start BOTH operations simultaneously
        startTileLoading();
        startDataLoading();
    }

    private void startTileLoading() {
        try {
            statusLabel.setText("Preparing map tiles...");

            // Calculate tiles on main thread (fast operation)
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

            mapTiles = new Texture[totalTiles];
            tileLoadingStarted = true;

            Gdx.app.log(TAG, "Starting tile downloads in background...");

            // Calculate tile indices
            final int[] factorY = new int[totalTiles];
            final int[] factorX = new int[totalTiles];

            int value = (size - 1) / -2;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    factorY[i * size + j] = value;
                    factorX[i + j * size] = value;
                }
                value++;
            }

            // Start background thread to download tiles
            tileDownloadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < totalTiles; i++) {
                        int tx = centerTile.x + factorX[i];
                        int ty = centerTile.y + factorY[i];

                        try {
                            byte[] tileData = MapRasterTiles.getTileData(Constants.ZOOM, tx, ty);
                            tileDataQueue.add(new TileData(i, tileData));
                        } catch (Exception e) {
                            Gdx.app.error(TAG, "Failed to download tile " + i, e);
                        }
                    }
                    tilesDownloadComplete.set(true);
                    Gdx.app.log(TAG, "All tiles downloaded");
                }
            });
            tileDownloadThread.start();

        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to start tile loading", e);
            statusLabel.setText("Error loading tiles: " + e.getMessage());
        }
    }

    private void startDataLoading() {
        dataLoadingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Load bus stops
                    Gdx.app.log(TAG, "Loading bus stops...");
                    List<BusStop> rawStops = GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json");
                    Gdx.app.log(TAG, "Loaded " + rawStops.size() + " bus stops");

                    // Load bus lines
                    Gdx.app.log(TAG, "Loading bus lines...");
                    List<BusLine> rawLines = GeoJSONLoader.loadBusLinesFromFile("data/int_mob_marprom_linije.json");
                    Gdx.app.log(TAG, "Loaded " + rawLines.size() + " bus lines");

                    // Build relationships
                    Gdx.app.log(TAG, "Building relationships...");
                    double proximityThreshold = 50.0;
                    BusLineStopRelationshipBuilder.RelationshipResult result =
                        BusLineStopRelationshipBuilder.buildRelationships(rawLines, rawStops, proximityThreshold);

                    // Load or generate schedules
                    Gdx.app.log(TAG, "Loading schedules...");
                    List<BusSchedule> schedules;

                    // Try to load from file first
                    schedules = ScheduleLoader.loadSchedulesFromFile("data/schedules.json");

                    // If no schedules file exists, generate example data
                    if (schedules.isEmpty()) {
                        Gdx.app.log(TAG, "No schedule file found, generating example schedules...");
                        schedules = ScheduleLoader.generateExampleSchedules(result.lines);
                    }

                    Gdx.app.log(TAG, "Loaded/generated " + schedules.size() + " schedules");

                    // Assign schedules to lines
                    Gdx.app.log(TAG, "Assigning schedules to lines...");
                    List<BusLine> linesWithSchedules = ScheduleLoader.assignSchedulesToLines(
                        result.lines, schedules);

                    // Log statistics
                    int totalStopsWithLines = 0;
                    int totalLinesWithStops = 0;
                    int totalLinesWithSchedules = 0;

                    for (BusStop stop : result.stops) {
                        if (stop.getLineCount() > 0) {
                            totalStopsWithLines++;
                        }
                    }

                    for (BusLine line : linesWithSchedules) {
                        if (line.getStopCount() > 0) {
                            totalLinesWithStops++;
                        }
                        if (line.getScheduleCount() > 0) {
                            totalLinesWithSchedules++;
                        }
                    }

                    Gdx.app.log(TAG, "Built relationships: " + totalStopsWithLines + "/" +
                        result.stops.size() + " stops have lines");
                    Gdx.app.log(TAG, totalLinesWithStops + "/" + linesWithSchedules.size() + " lines have stops");
                    Gdx.app.log(TAG, totalLinesWithSchedules + "/" + linesWithSchedules.size() + " lines have schedules");

                    // Store results
                    loadedStops = result.stops;
                    loadedLines = linesWithSchedules;
                    dataLoadingComplete = true;

                    Gdx.app.log(TAG, "Data loading complete");

                } catch (final Exception e) {
                    Gdx.app.error(TAG, "Error loading data", e);
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("Error loading data: " + e.getMessage());
                        }
                    });
                }
            }
        });
        dataLoadingThread.start();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Process tile textures on main thread (2-3 per frame)
        if (tileLoadingStarted) {
            int texturesCreatedThisFrame = 0;
            int maxTexturesPerFrame = 3;

            while (texturesCreatedThisFrame < maxTexturesPerFrame && !tileDataQueue.isEmpty()) {
                TileData tileData = tileDataQueue.poll();
                if (tileData != null) {
                    try {
                        mapTiles[tileData.index] = MapRasterTiles.createTextureFromData(tileData.data);
                        tilesLoaded++;
                        texturesCreatedThisFrame++;
                    } catch (Exception e) {
                        Gdx.app.error(TAG, "Failed to create texture for tile " + tileData.index, e);
                    }
                }
            }
        }

        // Calculate progress (40% tiles, 60% data because data loading includes schedules now)
        float tileProgress = totalTiles > 0 ? (float) tilesLoaded / totalTiles : 0f;
        float dataProgress = dataLoadingComplete ? 1f : 0f;

        progress = (tileProgress * 0.4f) + (dataProgress * 0.6f);

        // Update UI
        progressBar.setValue(progress);
        percentLabel.setText(String.format("%.0f%%", progress * 100));

        // Update status text
        if (!dataLoadingComplete && tilesLoaded < totalTiles) {
            statusLabel.setText("Loading data and map... (" + tilesLoaded + "/" + totalTiles + " tiles)");
        } else if (!dataLoadingComplete) {
            statusLabel.setText("Loading bus data and schedules...");
        } else if (tilesLoaded < totalTiles) {
            statusLabel.setText("Loading map tiles... (" + tilesLoaded + "/" + totalTiles + ")");
        } else {
            statusLabel.setText("Complete!");
        }

        // Update and draw stage
        stage.act(delta);
        stage.draw();

        // When complete, transition to main screen
        if (dataLoadingComplete && tilesLoaded >= totalTiles &&
            tilesDownloadComplete.get() && tileDataQueue.isEmpty()) {

            Gdx.app.log(TAG, "Everything loaded, transitioning to map screen");
            app.setBusData(loadedStops, loadedLines);
            app.setScreen(new RasterMapScreen(app, mapTiles, beginTile));
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        // Wait for loading threads to complete
        if (dataLoadingThread != null && dataLoadingThread.isAlive()) {
            try {
                dataLoadingThread.join(1000);
            } catch (InterruptedException e) {
                Gdx.app.error(TAG, "Interrupted while waiting for data loading thread", e);
            }
        }

        if (tileDownloadThread != null && tileDownloadThread.isAlive()) {
            try {
                tileDownloadThread.join(1000);
            } catch (InterruptedException e) {
                Gdx.app.error(TAG, "Interrupted while waiting for tile download thread", e);
            }
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
    }
}
