package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class HudPanel {
    private static final float WIDTH_RATIO = 5f;

    private Stage stage;
    private Skin skin;
    private Table mainPanel;

    // UI Components
    private TextField searchField;
    private ScrollPane busLinesScrollPane;
    private ScrollPane busStopsScrollPane;

    public HudPanel(Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());

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

        // Create bus line buttons container
        Table busLinesTable = createBusLinesTable();
        ScrollPane linesScrollPane = new ScrollPane(busLinesTable, skin, "no-bg");
        linesScrollPane.setFadeScrollBars(false);
        mainPanel.add(linesScrollPane).width(panelWidth - 20).height(150).padLeft(10).padRight(10).row();

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

        // Create bus stops list
        Table busStopsTable = createBusStopsTable();
        busStopsScrollPane = new ScrollPane(busStopsTable, skin, "no-bg");
        busStopsScrollPane.setFadeScrollBars(false);
        mainPanel.add(busStopsScrollPane).width(panelWidth - 20).height(200).expandY().padLeft(10).padRight(10).row();

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

        // Static bus stop data
        String[] busStops = {
            "Brezje",
            "Cesta XIV.divizije",
            "Dogošta - vrtec",
            "Dravograd",
            "Dravograd - Poljane",
            "Dravograd - Qlandia",
            "Dravograd - Sokolska",
            "Duplek - Jančevo",
            "Duplek - kanal",
            "Duplek - Tezensko",
            "Glavni trg - Vetrinjska",
            "Gosposvetska - rondo",
            "Gosposvetska - STŠ",
            "Gosposvetska - Turnerjeva",
            "Gosposvetska - Vrbanska",
            "Greenwich",
            "KŠ Silvije Tomassini",
            "Magdalena"
        };

        // Bus line numbers with colors
        String[][] busLineNumbers = {
            {"539"},
            {"362"},
            {"388"},
            {"111"},
            {"112"},
            {"103"},
            {"113"},
            {"357"},
            {"342"},
            {"360"},
            {"9"},
            {"23"},
            {"29"},
            {"25"},
            {"28"},
            {"298"},
            {"90"},
            {"228"}
        };

        for (int i = 0; i < busStops.length; i++) {
            // Bus line number button (cyan/blue color)
            TextButton lineBtn = new TextButton(busLineNumbers[i][0], skin, "maroon-small");
            lineBtn.getLabel().setFontScale(0.7f);

            // Bus stop label
            Label stopLabel = new Label(busStops[i], skin, "default");
            stopLabel.setFontScale(0.8f);

            // Arrow button
            TextButton arrowBtn = new TextButton(">", skin, "orange-small");
            arrowBtn.getLabel().setFontScale(0.9f);

            table.add(lineBtn).size(40, 30).padRight(5).left();
            table.add(stopLabel).expandX().left().padRight(5);
            table.add(arrowBtn).size(30, 30).right();
            table.row().padBottom(3);
        }

        return table;
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
