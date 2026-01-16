package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.BusLineColors;
import com.mbus.app.utils.BusPositionCalculator;
import com.mbus.app.utils.Constants;

import java.util.List;

public class BusStopDetailPanel {

    private Stage stage;
    private Skin skin;
    private Table mainPanel;
    private boolean visible;

    private BusStop currentStop;
    private List<BusLine> allLines;
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
        float panelWidth = Gdx.graphics.getWidth() / 70f;
        mainPanel.setSize(panelWidth, Gdx.graphics.getHeight());
        mainPanel.setPosition(panelWidth, 0); // Position next to left panel

        // Set background to match HudPanel
        mainPanel.setBackground(skin.getDrawable("panel-maroon"));

        mainPanel.setVisible(visible);
        stage.addActor(mainPanel);
    }

    public void showBusStop(BusStop busStop) {
        this.currentStop = busStop;
        this.visible = true;

        // Clear and rebuild panel content
        mainPanel.clear();
        float panelWidth = mainPanel.getWidth();

        // Header with title and close button
        Table headerTable = new Table();
        Label titleLabel = new Label("Postajališče", skin, "title-black");
        titleLabel.setFontScale(0.9f);
        titleLabel.setAlignment(Align.left);

        TextButton closeBtn = new TextButton("X", skin, "maroon-small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hide();
            }
        });

        headerTable.add(titleLabel).expandX().left().padLeft(15);
        headerTable.add(closeBtn).size(35, 35).padRight(10);

        mainPanel.add(headerTable).width(panelWidth).padTop(10).padBottom(5).row();

        // Bus stop name
        Label stopNameLabel = new Label(busStop.name, skin, "black");
        stopNameLabel.setWrap(true);
        stopNameLabel.setAlignment(Align.left);
        stopNameLabel.setFontScale(1.1f);
        mainPanel.add(stopNameLabel).width(panelWidth - 30).padLeft(15).padRight(15).padTop(5).padBottom(10).row();

        // Separator
        Image separator1 = new Image(skin.getDrawable("white"));
        separator1.setColor(0.8f, 0.8f, 0.8f, 0.3f);
        mainPanel.add(separator1).width(panelWidth - 30).height(1).padLeft(15).padRight(15).padBottom(15).row();

        // Bus stop ID info
        Table idTable = new Table();
        Label idLabel = new Label("ID: " + busStop.idAvpost, skin, "black");
        idLabel.setFontScale(0.75f);
        idLabel.setColor(Color.GRAY);
        idTable.add(idLabel).left();

        mainPanel.add(idTable).width(panelWidth - 30).padLeft(15).padBottom(15).row();

        // Arrivals section header
        Label arrivalsHeaderLabel = new Label("Prihodi", skin, "title-black");
        arrivalsHeaderLabel.setFontScale(0.8f);
        mainPanel.add(arrivalsHeaderLabel).left().padLeft(15).padTop(5).padBottom(15).row();

        // Get upcoming arrivals
        Table arrivalsTable = createArrivalsTable(busStop, panelWidth);
        ScrollPane arrivalsScrollPane = new ScrollPane(arrivalsTable, skin, "no-bg");
        arrivalsScrollPane.setFadeScrollBars(false);
        arrivalsScrollPane.setScrollingDisabled(true, false);
        mainPanel.add(arrivalsScrollPane).width(panelWidth - 20).expand().fill().padLeft(10).padRight(10).padBottom(15).row();

        mainPanel.setVisible(true);

        // Notify visibility change
        if (visibilityCallback != null) {
            visibilityCallback.onVisibilityChanged(true);
        }
    }

    private Table createArrivalsTable(BusStop busStop, float panelWidth) {
        Table table = new Table();
        table.align(Align.top | Align.left);

        if (allLines == null || allLines.isEmpty()) {
            Label emptyLabel = new Label("Ni podatkov o voznem redu", skin, "black");
            emptyLabel.setFontScale(0.8f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
            return table;
        }

        // Get current time and day type
        int currentTime = BusPositionCalculator.getCurrentTimeMinutes();
        int dayType = BusPositionCalculator.getCurrentDayType();

        // Get upcoming arrivals (next 10)
        List<BusStop.StopArrival> upcomingArrivals =
            busStop.getUpcomingArrivals(allLines, currentTime, dayType, 10);

        if (upcomingArrivals.isEmpty()) {
            Label emptyLabel = new Label("Ni več prihodov danes", skin, "black");
            emptyLabel.setFontScale(0.8f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
            return table;
        }

        // Display each arrival
        for (int i = 0; i < upcomingArrivals.size(); i++) {
            BusStop.StopArrival arrival = upcomingArrivals.get(i);
            BusLine line = arrival.line;

            // Line number button
            TextButton lineBtn = new TextButton(String.valueOf(line.lineId), skin, "orange-small-toggle");
            Color lineColor = BusLineColors.getButtonColor(line.lineId);
            lineBtn.setColor(lineColor);
            lineBtn.setChecked(false); // Not toggleable in this context

            // Line destination/name
            String lineName = line.name;
            if (lineName.length() > 35) {
                lineName = lineName.substring(0, 32) + "...";
            }

            Label nameLabel = new Label(lineName, skin, "black");
            nameLabel.setWrap(false);
            nameLabel.setEllipsis(true);
            nameLabel.setFontScale(0.8f);

            // Time info
            int minutesUntil = arrival.getMinutesUntilArrival(currentTime);
            String timeText;
            Color timeColor;

            if (minutesUntil == 0) {
                timeText = "Zdaj";
                timeColor = new Color(1f, 0.3f, 0.3f, 1f); // Red
            } else if (minutesUntil <= 5) {
                timeText = minutesUntil + " min";
                timeColor = new Color(1f, 0.6f, 0.2f, 1f); // Orange
            } else if (minutesUntil <= 15) {
                timeText = minutesUntil + " min";
                timeColor = new Color(0.8f, 0.8f, 0.8f, 1f); // Light gray
            } else {
                timeText = arrival.getArrivalTimeFormatted();
                timeColor = Color.GRAY;
            }

            Label timeLabel = new Label(timeText, skin, "black");
            timeLabel.setFontScale(0.85f);
            timeLabel.setColor(timeColor);
            timeLabel.setAlignment(Align.right);

            // Layout
            table.add(lineBtn).size(45, 35).padRight(8).left();
            table.add(nameLabel).expandX().left().padRight(8).minWidth(0);
            table.add(timeLabel).width(60).right().padRight(15);
            table.row().padTop(8).padBottom(8);

            // Add separator between items (except after last item)
            if (i < upcomingArrivals.size() - 1) {
                Image separator = new Image(skin.getDrawable("white"));
                separator.setColor(0.8f, 0.8f, 0.8f, 0.3f);
                table.add(separator).colspan(3).height(1).fillX().padTop(8).padBottom(8).expandX();
                table.row();
            }
        }

        // Add footer with day type and current time
        String dayTypeStr;
        switch (dayType) {
            case 0: dayTypeStr = "Delovnik"; break;
            case 1: dayTypeStr = "Sobota"; break;
            case 2: dayTypeStr = "Nedelja/Praznik"; break;
            default: dayTypeStr = "Neznano";
        }

        Label footerLabel = new Label(
            dayTypeStr + " • " + BusPositionCalculator.formatTimeDifference(currentTime),
            skin,
            "black"
        );
        footerLabel.setFontScale(0.65f);
        footerLabel.setColor(Color.GRAY);
        footerLabel.setAlignment(Align.center);

        table.add(footerLabel).colspan(3).padTop(20).padBottom(10).center();
        table.row();

        return table;
    }

    /**
     * Set the list of all bus lines (needed for schedule queries)
     */
    public void setBusLines(List<BusLine> lines) {
        this.allLines = lines;

        // Refresh if currently showing a stop
        if (visible && currentStop != null) {
            showBusStop(currentStop);
        }
    }

    /**
     * Refresh the timetable display (call this periodically to update times)
     */
    public void refresh() {
        if (visible && currentStop != null) {
            showBusStop(currentStop);
        }
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
