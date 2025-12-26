package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.Constants;

public class BusStopDetailPanel {

    private Stage stage;
    private Skin skin;
    private Table mainPanel;
    private boolean visible;

    private BusStop currentStop;
    private VisibilityChangeCallback visibilityCallback;

    public BusStopDetailPanel(Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        this.visible = false;

        createUI();
    }

    private void createUI() {
        // Main container table
        mainPanel = new Table();
        mainPanel.setFillParent(false);

        // Calculate panel width (1/6th of screen) and position
        float panelWidth = Gdx.graphics.getWidth() / Constants.HUD_WIDTH;
        mainPanel.setSize(panelWidth, Gdx.graphics.getHeight());
        mainPanel.setPosition(panelWidth, 0); // Position next to left panel

        // Set background
        mainPanel.setBackground(skin.getDrawable("window-maroon"));

        mainPanel.setVisible(visible);
        stage.addActor(mainPanel);
    }

    public void showBusStop(BusStop busStop) {
        this.currentStop = busStop;
        this.visible = true;

        // Clear and rebuild panel content
        mainPanel.clear();
        float panelWidth = mainPanel.getWidth();

        // Close button
        Table headerTable = new Table();
        Label titleLabel = new Label("Postajališče", skin, "white");
        titleLabel.setAlignment(Align.left);

        TextButton closeBtn = new TextButton("X", skin, "maroon-small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hide();
            }
        });

        headerTable.add(titleLabel).expandX().left().padLeft(10);
        headerTable.add(closeBtn).size(40, 40).padRight(10);

        mainPanel.add(headerTable).width(panelWidth).height(50).row();

        // Bus stop name
        Label stopNameLabel = new Label(busStop.name, skin);
        stopNameLabel.setWrap(true);
        stopNameLabel.setAlignment(Align.center);
        mainPanel.add(stopNameLabel).width(panelWidth - 20).pad(15).row();

        // Separator
        mainPanel.add(new Image(skin, "white")).width(panelWidth - 40).height(2).pad(5).row();

        // Bus stop details section
        Label detailsHeaderLabel = new Label("Informacije", skin);
        mainPanel.add(detailsHeaderLabel).left().padLeft(10).padTop(10).padBottom(5).row();

        // ID and coordinates info
        Table infoTable = new Table();
        infoTable.align(Align.topLeft);

        addInfoRow(infoTable, "ID Avpost:", String.valueOf(busStop.idAvpost), panelWidth);
        addInfoRow(infoTable, "ID Marprom:", busStop.idMarprom, panelWidth);
        addInfoRow(infoTable, "Lat:", String.format("%.6f", busStop.geo.lat), panelWidth);
        addInfoRow(infoTable, "Lng:", String.format("%.6f", busStop.geo.lng), panelWidth);

        ScrollPane infoScrollPane = new ScrollPane(infoTable, skin);
        infoScrollPane.setFadeScrollBars(false);
        mainPanel.add(infoScrollPane).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).row();

        // Bus lines section
        Label busLinesHeaderLabel = new Label("Linije na postaji", skin);
        mainPanel.add(busLinesHeaderLabel).left().padLeft(10).padTop(20).padBottom(5).row();

        // Static bus lines data for demonstration
        Table busLinesTable = createBusLinesForStop();
        ScrollPane busLinesScrollPane = new ScrollPane(busLinesTable, skin);
        busLinesScrollPane.setFadeScrollBars(false);
        mainPanel.add(busLinesScrollPane).width(panelWidth - 20).height(200).expandY().padLeft(10).padRight(10).row();

        // Action buttons at bottom
        Table actionButtonsTable = new Table();
        TextButton routeBtn = new TextButton("Prikaži poti", skin, "maroon-small");
        TextButton scheduleBtn = new TextButton("Vozni red", skin, "maroon-small");

        actionButtonsTable.add(routeBtn).width((panelWidth - 30) / 2f).height(40).padRight(5);
        actionButtonsTable.add(scheduleBtn).width((panelWidth - 30) / 2f).height(40).padLeft(5);

        mainPanel.add(actionButtonsTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(15).padBottom(15).row();

        mainPanel.setVisible(true);

        // Notify visibility change
        if (visibilityCallback != null) {
            visibilityCallback.onVisibilityChanged(true);
        }
    }

    private void addInfoRow(Table table, String label, String value, float panelWidth) {
        Label labelWidget = new Label(label, skin);
        labelWidget.setFontScale(0.85f);

        Label valueWidget = new Label(value, skin, "peach");
        valueWidget.setFontScale(0.85f);

        table.add(labelWidget).left().padLeft(10).padRight(10).padTop(5);
        table.add(valueWidget).expandX().left().padTop(5);
        table.row();
    }

    private Table createBusLinesForStop() {
        Table table = new Table();
        table.align(Align.top | Align.left);

        // Static bus lines - in reality, you'd get this from your data model
        String[] busLines = {"1", "3", "9", "12", "362", "388"};
        String[] destinations = {
            "Pokopališka - Brezje",
            "Gosposvetska - Turnerjeva",
            "Glavni trg - Vetrinjska",
            "Duplek - Tezensko",
            "Cesta XIV.divizije",
            "Dogošta - vrtec"
        };
        String[] times = {"5 min", "8 min", "12 min", "15 min", "20 min", "25 min"};

        for (int i = 0; i < busLines.length; i++) {
            // Bus line number button
            TextButton lineBtn = new TextButton(busLines[i], skin, "maroon-small");
            lineBtn.getLabel().setFontScale(0.7f);

            // Container for destination and time
            Table infoTable = new Table();
            Label destLabel = new Label(destinations[i], skin, "white");
            destLabel.setFontScale(0.75f);
            destLabel.setWrap(true);

            Label timeLabel = new Label(times[i], skin, "peach");
            timeLabel.setFontScale(0.7f);

            infoTable.add(destLabel).left().row();
            infoTable.add(timeLabel).left().padTop(2);

            table.add(lineBtn).size(50, 35).padRight(8).left();
            table.add(infoTable).expandX().left().padRight(5);
            table.row().padBottom(8);
        }

        return table;
    }

    public void hide() {
        this.visible = false;
        mainPanel.setVisible(false);

        // Notify visibility change
        if (visibilityCallback != null) {
            visibilityCallback.onVisibilityChanged(false);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public float getEffectiveWidth() {
        return visible ? mainPanel.getWidth() : 0f;
    }

    /**
     * Set callback for when panel visibility changes
     */
    public void setVisibilityChangeCallback(VisibilityChangeCallback callback) {
        this.visibilityCallback = callback;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);

        // Update panel width and position
        float panelWidth = width / Constants.HUD_WIDTH;
        mainPanel.setSize(panelWidth, height);
        mainPanel.setPosition(panelWidth, 0);

        // Rebuild UI if currently showing a stop
        if (visible && currentStop != null) {
            showBusStop(currentStop);
        }
    }

    public void render() {
        if (visible) {
            stage.act(Gdx.graphics.getDeltaTime());
            stage.draw();
        }
    }

    public void dispose() {
        stage.dispose();
    }

    public Stage getStage() {
        return stage;
    }

    /**
     * Callback interface for panel visibility changes
     */
    public interface VisibilityChangeCallback {
        void onVisibilityChanged(boolean visible);
    }
}
