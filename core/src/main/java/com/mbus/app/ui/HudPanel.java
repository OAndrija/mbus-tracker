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

import java.util.List;
import java.util.ArrayList;

public class HudPanel {
    private static final float WIDTH_RATIO = 6f;

    private Stage stage;
    private Skin skin;
    private Table mainPanel;

    // UI Components
    private TextField searchField;
    private ScrollPane busStopsScrollPane;

    // Data
    private List<BusStop> busStops;

    public HudPanel(Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        this.busStops = new ArrayList<BusStop>();

        createUI();
    }

    private void createUI() {
        // Main container table
        mainPanel = new Table();
        mainPanel.setFillParent(false);

        // Calculate panel width (1/6th of screen)
        float panelWidth = Gdx.graphics.getWidth() / WIDTH_RATIO;
        mainPanel.setSize(panelWidth, Gdx.graphics.getHeight());
        mainPanel.setPosition(0, 0);

        // Set background
        mainPanel.setBackground(skin.getDrawable("window-peach"));

        // Create header
        Label headerLabel = new Label("Mestni avtobusni promet", skin, "title");
        headerLabel.setWrap(true);
        headerLabel.setAlignment(Align.center);

        mainPanel.add(headerLabel).width(panelWidth - 20).pad(10).row();

        // Create search field
        searchField = new TextField("", skin, "search");
        searchField.setMessageText("Išči postajališče...");
        mainPanel.add(searchField).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).row();

        // Create bus lines section
        Label linesLabel = new Label("Linije", skin, "default");
        mainPanel.add(linesLabel).left().padLeft(10).padTop(15).padBottom(5).row();

        // Create bus line buttons container (NO ScrollPane)
        Table busLinesTable = createBusLinesTable();
        mainPanel.add(busLinesTable).width(panelWidth - 20).padLeft(10).padRight(10).row();

        // "Vse linije" and "Vse postaje" buttons
        Table allButtonsTable = new Table();
        TextButton allLinesBtn = new TextButton("Vse linije", skin, "orange-small");
        TextButton allStopsBtn = new TextButton("Vse postaje", skin, "orange-small");

        allButtonsTable.add(allLinesBtn).width((panelWidth - 30) / 2f).padRight(5);
        allButtonsTable.add(allStopsBtn).width((panelWidth - 30) / 2f).padLeft(5);

        mainPanel.add(allButtonsTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(10).row();

        // Create bus stops section
        Label stopsLabel = new Label("Postajališče", skin, "default");
        mainPanel.add(stopsLabel).left().padLeft(10).padTop(15).padBottom(5).row();

        // Create bus stops list - this should expand to fill remaining space
        Table busStopsTable = createBusStopsTable();
        busStopsScrollPane = new ScrollPane(busStopsTable, skin, "no-bg");
        busStopsScrollPane.setFadeScrollBars(false);
        busStopsScrollPane.setScrollingDisabled(true, false); // Disable horizontal scrolling
        mainPanel.add(busStopsScrollPane).width(panelWidth - 20).expand().fill().padLeft(10).padRight(10).padBottom(10).row();

        stage.addActor(mainPanel);
    }

    private Table createBusLinesTable() {
        Table table = new Table();

        // Static bus line data - organized in grid
        String[][] busLines = {
            {"1", "2", "3", "4", "6", "7"},
            {"8", "9", "10", "12", "13", "15"},
            {"16", "17", "18", "19", "20", "21"},
            {"151", "k1", "k2"}
        };

        float buttonSize = 45f;

        for (String[] row : busLines) {
            for (String lineNumber : row) {
                TextButton btn = new TextButton(lineNumber, skin, "orange-small-toggle");
                btn.getLabel().setFontScale(0.8f);
                table.add(btn).size(buttonSize).pad(2);
            }
            table.row();
        }

        return table;
    }

    private Table createBusStopsTable() {
        Table table = new Table();
        table.align(Align.top | Align.left);

        // Use real bus stops data
        if (busStops != null && !busStops.isEmpty()) {
            for (int i = 0; i < busStops.size(); i++) {
                BusStop stop = busStops.get(i);

                // Bus stop ID button (using idAvpost)
                TextButton idBtn = new TextButton(String.valueOf(stop.idAvpost), skin, "maroon-small");
                idBtn.getLabel().setFontScale(0.7f);

                // Bus stop name label - with ellipsis for truncation
                Label stopLabel = new Label(stop.name, skin, "default");
                stopLabel.setFontScale(0.8f);
                stopLabel.setEllipsis(true);

                // Arrow button - always visible
                TextButton arrowBtn = new TextButton(">", skin, "orange-small");
                arrowBtn.getLabel().setFontScale(0.9f);

                // Add cells with proper sizing - arrow always visible
                table.add(idBtn).width(30).height(35).padRight(5).left();
                table.add(stopLabel).expandX().fillX().left().padRight(5).minWidth(0);
                table.add(arrowBtn).width(30).height(25).right().padRight(5).minWidth(30);
                table.row().padTop(8).padBottom(8);

                // Add separator line between stops (except after last one)
                if (i < busStops.size() - 1) {
                    Image separator = new Image(skin.getDrawable("white"));
                    separator.setColor(0.8f, 0.8f, 0.8f, 0.3f); // Light gray with transparency
                    table.add(separator).colspan(3).height(1).fillX().expandX();
                    table.row();
                }
            }
        } else {
            // Fallback message if no data
            Label emptyLabel = new Label("Ni podatkov o postajališčih", skin, "default");
            emptyLabel.setFontScale(0.8f);
            table.add(emptyLabel).pad(10);
        }

        return table;
    }

    public void setBusStops(List<BusStop> stops) {
        this.busStops = stops;
        // Refresh the UI to show the new data
        refreshBusStopsTable();
    }

    private void refreshBusStopsTable() {
        // Find and update the bus stops scroll pane content
        Table newBusStopsTable = createBusStopsTable();
        busStopsScrollPane.setActor(newBusStopsTable);
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);

        // Update panel width
        float panelWidth = width / WIDTH_RATIO;
        mainPanel.setSize(panelWidth, height);

        // Rebuild UI with new dimensions
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
