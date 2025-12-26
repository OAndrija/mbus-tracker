package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
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
    private Texture titleIcon;

    // UI Components
    private TextField searchField;
    private ScrollPane busStopsScrollPane;
    private TextButton allStopsBtn;

    // Data
    private List<BusStop> allBusStops;
    private List<BusLine> busLines;
    private Set<Integer> visibleLineIds;
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

    public interface FilteredStopsCallback {
        void onFilteredStopsChanged(List<BusStop> filteredStops);
    }

    private ShowAllStopsCallback showAllStopsCallback;
    private BusStopClickCallback busStopClickCallback;
    private BusLineVisibilityCallback busLineVisibilityCallback;
    private FilteredStopsCallback filteredStopsCallback;

    public HudPanel(Skin skin, Texture titleIcon) {
        this.skin = skin;
        this.titleIcon = titleIcon;
        this.stage = new Stage(new ScreenViewport());
        this.allBusStops = new ArrayList<BusStop>();
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

        // Create header with icon and title
        Table headerTable = new Table();

        if (titleIcon != null) {
            Image iconImage = new Image(titleIcon);
            headerTable.add(iconImage).size(35, 35).padRight(5);
        }

        Label headerLabel = new Label("MBus", skin, "title-black");
        headerLabel.setWrap(false);
        headerLabel.setAlignment(Align.center);
        headerLabel.setFontScale(1.3f);
        headerTable.add(headerLabel);

        mainPanel.add(headerTable).width(panelWidth - 20).pad(10).row();

        // Create search field
//        searchField = new TextField("", skin, "search");
//        searchField.setMessageText("Išči postajališče...");
//        mainPanel.add(searchField).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).row();

        // Create bus lines section
        Label linesLabel = new Label("Linije", skin, "title-black");
        linesLabel.setFontScale(0.8f);
        mainPanel.add(linesLabel).left().padLeft(10).padTop(15).padBottom(15).row();

        // Create bus line buttons container
        Table busLinesTable = createBusLinesTable();
        mainPanel.add(busLinesTable).width(panelWidth - 20).padLeft(10).padBottom(20).padRight(10).row();

        // "Vse linije" and "Vse postaje" buttons
        Table allButtonsTable = new Table();
        TextButton allLinesBtn = new TextButton("Linije", skin, "orange-small-toggle");
        allLinesBtn.setColor(0.663f, 0.620f, 0.58f, 1f);
        allLinesBtn.setChecked(!visibleLineIds.isEmpty());

        // Add click listener to "Vse linije" button
        allLinesBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleAllLines();
            }
        });

        allStopsBtn = new TextButton("Postaje", skin, "orange-small-toggle");
        allStopsBtn.setColor(0.663f, 0.620f, 0.58f, 1f);
        allStopsBtn.setChecked(showingAllStops); // Set initial checked state

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
        Label stopsLabel = new Label("Postajalisca", skin, "title-black");
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

    /**
     * Get stops filtered by currently visible lines
     */
    private List<BusStop> getFilteredStops() {
        // If all lines are visible, show all stops
        boolean allLinesVisible = true;
        if (busLines != null && !busLines.isEmpty()) {
            Set<Integer> allLineIds = new HashSet<Integer>();
            for (BusLine line : busLines) {
                allLineIds.add(line.lineId);
            }
            allLinesVisible = visibleLineIds.containsAll(allLineIds) &&
                allLineIds.containsAll(visibleLineIds);
        }

        if (allLinesVisible || visibleLineIds.isEmpty()) {
            return allBusStops;
        }

        // Filter stops by visible lines
        List<BusStop> filtered = new ArrayList<BusStop>();

        for (BusStop stop : allBusStops) {
            // Check if this stop has any visible lines
            for (Integer lineId : stop.getLineIds()) {
                if (visibleLineIds.contains(lineId)) {
                    filtered.add(stop);
                    break; // Stop is included, no need to check more lines
                }
            }
        }

        return filtered;
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
        onLineVisibilityChanged();
    }

    private void toggleShowAllStops() {
        showingAllStops = !showingAllStops;

        // Update button checked state
        if (allStopsBtn != null) {
            allStopsBtn.setChecked(showingAllStops);
        }

        refreshBusStopsTable();

        if (showAllStopsCallback != null) {
            showAllStopsCallback.onShowAllStopsChanged(showingAllStops);
        }
    }

    /**
     * Called when line visibility changes
     */
    private void onLineVisibilityChanged() {
        refreshBusStopsTable();

        if (busLineVisibilityCallback != null) {
            busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
        }

        // Notify about filtered stops for map rendering
        if (filteredStopsCallback != null) {
            List<BusStop> filtered = getFilteredStops();
            filteredStopsCallback.onFilteredStopsChanged(filtered);
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

                    onLineVisibilityChanged();
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

        // Get the appropriate stops list
        List<BusStop> stopsToShow = getFilteredStops();

        // Check if we're filtering
        boolean isFiltering = stopsToShow.size() < allBusStops.size();

        if (showingAllStops && stopsToShow != null && !stopsToShow.isEmpty()) {
            for (int i = 0; i < stopsToShow.size(); i++) {
                final BusStop stop = stopsToShow.get(i);

                TextButton idBtn = new TextButton(String.valueOf(stop.idAvpost), skin, "maroon-small");

                // Create label with stop name and line info if filtering
                String labelText = stop.name;
                if (isFiltering && stop.getLineCount() > 0) {
                    // Show which visible lines pass through this stop
                    List<Integer> visibleStopLines = new ArrayList<Integer>();
                    for (Integer lineId : stop.getLineIds()) {
                        if (visibleLineIds.contains(lineId)) {
                            visibleStopLines.add(lineId);
                        }
                    }

                    if (!visibleStopLines.isEmpty()) {
                        StringBuilder lineStr = new StringBuilder();
                        for (int j = 0; j < visibleStopLines.size(); j++) {
                            lineStr.append(visibleStopLines.get(j));
                            if (j < visibleStopLines.size() - 1) {
                                lineStr.append(", ");
                            }
                        }
                        labelText += " (" + lineStr.toString() + ")";
                    }
                }

                Label stopLabel = new Label(labelText, skin, "black");
                stopLabel.setEllipsis(true);
                stopLabel.setWrap(false);

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

                if (i < stopsToShow.size() - 1) {
                    Image separator = new Image(skin.getDrawable("white"));
                    separator.setColor(0.8f, 0.8f, 0.8f, 0.3f);
                    table.add(separator).colspan(3).height(1).fillX().padTop(20).padBottom(20).expandX();
                    table.row();
                }
            }

            // Show count info if filtered
            if (isFiltering) {
                Label infoLabel = new Label(
                    stopsToShow.size() + " od " + allBusStops.size() + " postajališč",
                    skin,
                    "default"
                );
                infoLabel.setFontScale(0.7f);
                infoLabel.setColor(Color.GRAY);
                table.add(infoLabel).colspan(3).pad(10).center();
                table.row();
            }

        } else if (!showingAllStops) {
            Label emptyLabel = new Label("Postajališča so skrita", skin, "default");
            emptyLabel.setFontScale(0.8f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
        } else {
            Label emptyLabel = new Label("Ni postajališč za izbrane linije", skin, "default");
            emptyLabel.setFontScale(0.8f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
        }

        return table;
    }

    public void setBusStops(List<BusStop> stops) {
        this.allBusStops = stops != null ? stops : new ArrayList<BusStop>();
        refreshBusStopsTable();
    }

    public void setBusLines(List<BusLine> lines) {
        this.busLines = lines != null ? lines : new ArrayList<BusLine>();

        // Initially show all lines
        visibleLineIds.clear();
        for (BusLine line : this.busLines) {
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

    public void setFilteredStopsCallback(FilteredStopsCallback callback) {
        this.filteredStopsCallback = callback;
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
