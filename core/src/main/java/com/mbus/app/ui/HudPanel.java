package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.BusLine;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.BusLineColors;

import java.util.*;
import java.util.List;

public class HudPanel {
    private Stage stage;
    private Skin skin;
    private Table mainPanel;

    // UI Components
    private TextField searchField;
    private ScrollPane busStopsScrollPane;
    private TextButton allStopsBtn;

    // Data
    private List<BusStop> busStops;
    private List<BusLine> busLines;
    private Set<Integer> visibleLineIds; // Track which lines are currently visible
    private boolean showingAllStops = true;

    // Callback interfaces
    public interface ShowAllStopsCallback {
        void onShowAllStopsChanged(boolean showAll);
    }

    public interface BusStopClickCallback {
        void onBusStopClicked(BusStop busStop);
    }

    public interface BusLineVisibilityCallback {
        void onBusLineVisibilityChanged(Set<Integer> visibleLineIds);
    }

    private ShowAllStopsCallback showAllStopsCallback;
    private BusStopClickCallback busStopClickCallback;
    private BusLineVisibilityCallback busLineVisibilityCallback;

    public HudPanel(Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        this.busStops = new ArrayList<BusStop>();
        this.busLines = new ArrayList<BusLine>();
        this.visibleLineIds = new HashSet<Integer>();

        createUI();
    }

    private void createUI() {
        // Main container table
        mainPanel = new Table();
        mainPanel.setFillParent(false);

        // Calculate panel width (1/6th of screen)
        float panelWidth = Gdx.graphics.getWidth() / Constants.HUD_WIDTH;
        mainPanel.setSize(panelWidth, Gdx.graphics.getHeight());
        mainPanel.setPosition(0, 0);

        // Set background
        mainPanel.setBackground(skin.getDrawable("panel-maroon"));

        // Create header
        Label headerLabel = new Label("MBus", skin, "title-black");
        headerLabel.setWrap(true);
        headerLabel.setAlignment(Align.center);

        mainPanel.add(headerLabel).width(panelWidth - 20).pad(10).row();

        // Create search field
        searchField = new TextField("", skin, "search");
        searchField.setMessageText("Išči postajališče...");
        mainPanel.add(searchField).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).row();

        // Create bus lines section
        Label linesLabel = new Label("Linije", skin, "title-black");
        linesLabel.setFontScale(0.8f);
        mainPanel.add(linesLabel).left().padLeft(10).padTop(15).padBottom(15).row();

        // Create bus line buttons container
        Table busLinesTable = createBusLinesTable();
        mainPanel.add(busLinesTable).width(panelWidth - 20).padLeft(10).padBottom(20).padRight(10).row();

        // "Vse linije" and "Vse postaje" buttons
        Table allButtonsTable = new Table();
        TextButton allLinesBtn = new TextButton("Vse linije", skin, "orange-small-toggle");
        allLinesBtn.setColor(0.663f, 0.620f, 0.58f, 1f);

        // Add click listener to "Vse linije" button
        allLinesBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleAllLines();
            }
        });

        allStopsBtn = new TextButton("Vse postaje", skin, "orange-small");
        allStopsBtn.setColor(0.663f, 0.620f, 0.58f, 1f);

        // Add click listener to "Vse postaje" button
        allStopsBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleShowAllStops();
            }
        });

        allButtonsTable.add(allLinesBtn).width((panelWidth - 30) / 2f).padRight(5);
        allButtonsTable.add(allStopsBtn).width((panelWidth - 30) / 2f).padLeft(5);

        mainPanel.add(allButtonsTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(10).row();

        // Create bus stops section
        Label stopsLabel = new Label("Postajalisce", skin, "title-black");
        stopsLabel.setFontScale(0.8f);
        mainPanel.add(stopsLabel).left().padLeft(10).padTop(30).padBottom(25).row();

        // Create bus stops list - this should expand to fill remaining space
        Table busStopsTable = createBusStopsTable();
        busStopsScrollPane = new ScrollPane(busStopsTable, skin, "no-bg");
        busStopsScrollPane.setFadeScrollBars(false);
        busStopsScrollPane.setScrollingDisabled(true, false);
        mainPanel.add(busStopsScrollPane).width(panelWidth - 20).expand().fill().padLeft(10).padRight(10).padBottom(10).row();

        stage.addActor(mainPanel);
    }

    private void toggleAllLines() {
        if (visibleLineIds.isEmpty()) {
            // Show all lines
            for (BusLine line : busLines) {
                visibleLineIds.add(line.lineId);
            }
        } else {
            // Hide all lines
            visibleLineIds.clear();
        }

        refreshBusLinesTable();

        if (busLineVisibilityCallback != null) {
            busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
        }
    }

    private void toggleShowAllStops() {
        showingAllStops = !showingAllStops;
        refreshBusStopsTable();

        if (showAllStopsCallback != null) {
            showAllStopsCallback.onShowAllStopsChanged(showingAllStops);
        }
    }

    private Table createBusLinesTable() {
        Table table = new Table();

        if (busLines == null || busLines.isEmpty()) {
            Label emptyLabel = new Label("Ni podatkov o linijah", skin, "default");
            emptyLabel.setFontScale(0.8f);
            table.add(emptyLabel).pad(10);
            return table;
        }

        // Get unique line IDs and sort them
        Set<Integer> uniqueLineIds = new TreeSet<Integer>();
        for (BusLine line : busLines) {
            uniqueLineIds.add(line.lineId);
        }

        float buttonSize = 45f;
        int buttonsPerRow = 6;
        int count = 0;

        for (final Integer lineId : uniqueLineIds) {
            final TextButton btn = new TextButton(String.valueOf(lineId), skin, "orange-small-toggle");

            // Set the color for this line
            Color lineColor = BusLineColors.getButtonColor(lineId);
            btn.setColor(lineColor);

            // Set checked state based on visibility
            btn.setChecked(visibleLineIds.contains(lineId));

            // Add click listener to toggle line visibility
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (visibleLineIds.contains(lineId)) {
                        visibleLineIds.remove(lineId);
                        btn.setChecked(false);
                    } else {
                        visibleLineIds.add(lineId);
                        btn.setChecked(true);
                    }

                    if (busLineVisibilityCallback != null) {
                        busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
                    }
                }
            });

            table.add(btn).size(buttonSize).pad(4);
            count++;

            if (count % buttonsPerRow == 0) {
                table.row();
            }
        }

        return table;
    }

    private Table createBusStopsTable() {
        Table table = new Table();
        table.align(Align.top | Align.left);

        if (showingAllStops && busStops != null && !busStops.isEmpty()) {
            for (int i = 0; i < busStops.size(); i++) {
                final BusStop stop = busStops.get(i);

                TextButton idBtn = new TextButton(String.valueOf(stop.idAvpost), skin, "maroon-small");
                Label stopLabel = new Label(stop.name, skin, "black");
                stopLabel.setEllipsis(true);
                TextButton arrowBtn = new TextButton(">", skin, "maroon-small");

                arrowBtn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (busStopClickCallback != null) {
                            busStopClickCallback.onBusStopClicked(stop);
                        }
                    }
                });

                table.add(idBtn).width(30).height(35).padRight(5).left();
                table.add(stopLabel).expandX().fillX().left().padRight(5).minWidth(0);
                table.add(arrowBtn).width(30).height(30).right().padRight(15).minWidth(30);
                table.row().padTop(8).padBottom(8);

                if (i < busStops.size() - 1) {
                    Image separator = new Image(skin.getDrawable("white"));
                    separator.setColor(0.8f, 0.8f, 0.8f, 0.3f);
                    table.add(separator).colspan(3).height(1).fillX().padTop(20).padBottom(20).expandX();
                    table.row();
                }
            }
        } else if (!showingAllStops) {
            Label emptyLabel = new Label("Postajališča so skrita", skin, "default");
            emptyLabel.setFontScale(0.8f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
        } else {
            Label emptyLabel = new Label("Ni podatkov o postajališčih", skin, "default");
            emptyLabel.setFontScale(0.8f);
            table.add(emptyLabel).pad(10);
        }

        return table;
    }

    public void setBusStops(List<BusStop> stops) {
        this.busStops = stops;
        refreshBusStopsTable();
    }

    public void setBusLines(List<BusLine> lines) {
        this.busLines = lines;

        // Initially show all lines
        visibleLineIds.clear();
        for (BusLine line : lines) {
            visibleLineIds.add(line.lineId);
        }

        refreshBusLinesTable();

        if (busLineVisibilityCallback != null) {
            busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
        }
    }

    private void refreshBusStopsTable() {
        Table newBusStopsTable = createBusStopsTable();
        busStopsScrollPane.setActor(newBusStopsTable);
    }

    private void refreshBusLinesTable() {
        // Find the bus lines table in the mainPanel and rebuild it
        mainPanel.clear();
        stage.clear();
        createUI();
    }

    public void setShowAllStopsCallback(ShowAllStopsCallback callback) {
        this.showAllStopsCallback = callback;
    }

    public void setBusStopClickCallback(BusStopClickCallback callback) {
        this.busStopClickCallback = callback;
    }

    public void setBusLineVisibilityCallback(BusLineVisibilityCallback callback) {
        this.busLineVisibilityCallback = callback;
    }

    public boolean isShowingAllStops() {
        return showingAllStops;
    }

    public Set<Integer> getVisibleLineIds() {
        return new HashSet<Integer>(visibleLineIds);
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);

        float panelWidth = width / Constants.HUD_WIDTH;
        mainPanel.setSize(panelWidth, height);

        mainPanel.clear();
        stage.clear();
        createUI();
    }

    public void render() {
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    public void dispose() {
        stage.dispose();
    }

    public Stage getStage() {
        return stage;
    }
}
