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
import com.mbus.app.model.BusSchedule;
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
        mainPanel = new Table();
        mainPanel.setFillParent(false);

        // Thinner panel - 1/8th of screen width
        float panelWidth = Gdx.graphics.getWidth() / 8f;
        mainPanel.setSize(panelWidth, Gdx.graphics.getHeight());
        mainPanel.setPosition(Gdx.graphics.getWidth() / Constants.HUD_WIDTH, 0);

        mainPanel.setBackground(skin.getDrawable("panel-maroon"));
        mainPanel.setVisible(visible);
        stage.addActor(mainPanel);
    }

    public void showBusStop(BusStop busStop) {
        this.currentStop = busStop;
        this.visible = true;

        rebuildUI();
    }

    private void rebuildUI() {
        mainPanel.clear();
        float panelWidth = mainPanel.getWidth();

        // Header with close button
        Table headerTable = new Table();

        Label titleLabel = new Label("Postajališče", skin, "title-black");
        titleLabel.setFontScale(0.8f);
        titleLabel.setAlignment(Align.left);

        TextButton closeBtn = new TextButton("X", skin, "maroon-small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hide();
            }
        });

        headerTable.add(titleLabel).expandX().left().padLeft(10);
        headerTable.add(closeBtn).size(35, 35).padRight(5);

        mainPanel.add(headerTable).width(panelWidth).padTop(10).padBottom(5).row();

        // Bus stop name
        Label stopNameLabel = new Label(currentStop.name, skin, "black");
        stopNameLabel.setWrap(true);
        stopNameLabel.setAlignment(Align.left);
        stopNameLabel.setFontScale(0.95f);
        mainPanel.add(stopNameLabel).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).padBottom(8).row();

        // Separator
        Image separator1 = new Image(skin.getDrawable("white"));
        separator1.setColor(0.8f, 0.8f, 0.8f, 0.3f);
        mainPanel.add(separator1).width(panelWidth - 20).height(1).padLeft(10).padRight(10).padBottom(10).row();

        // Arrivals section
        Label arrivalsHeaderLabel = new Label("Prihodi", skin, "title-black");
        arrivalsHeaderLabel.setFontScale(0.75f);
        mainPanel.add(arrivalsHeaderLabel).left().padLeft(10).padTop(5).padBottom(10).row();

        // Arrivals list
        Table arrivalsTable = createUpcomingArrivalsTable(panelWidth);
        ScrollPane arrivalsScrollPane = new ScrollPane(arrivalsTable, skin, "no-bg");
        arrivalsScrollPane.setFadeScrollBars(false);
        arrivalsScrollPane.setScrollingDisabled(true, false);
        mainPanel.add(arrivalsScrollPane).width(panelWidth - 10).expand().fill().padLeft(5).padRight(5).padBottom(10).row();

        mainPanel.setVisible(true);

        if (visibilityCallback != null) {
            visibilityCallback.onVisibilityChanged(true);
        }
    }

    private Table createUpcomingArrivalsTable(float panelWidth) {
        Table table = new Table();
        table.align(Align.top | Align.left);

        if (allLines == null || allLines.isEmpty()) {
            Label emptyLabel = new Label("Ni podatkov o voznem redu", skin, "black");
            emptyLabel.setFontScale(0.75f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
            return table;
        }

        int currentTime = BusPositionCalculator.getCurrentTimeMinutes();
        int dayType = BusPositionCalculator.getCurrentDayType();

        List<BusStop.StopArrival> upcomingArrivals =
            currentStop.getUpcomingArrivals(allLines, currentTime, dayType, 20);

        if (upcomingArrivals.isEmpty()) {
            Label emptyLabel = new Label("Ni več prihodov danes", skin, "black");
            emptyLabel.setFontScale(0.75f);
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
            return table;
        }

        // Display arrivals
        for (int i = 0; i < upcomingArrivals.size(); i++) {
            BusStop.StopArrival arrival = upcomingArrivals.get(i);
            BusLine line = arrival.line;

            // Line number button
            TextButton lineBtn = new TextButton(String.valueOf(line.lineId), skin, "orange-small-toggle");
            Color lineColor = BusLineColors.getButtonColor(line.lineId);
            lineBtn.setColor(lineColor);
            lineBtn.setChecked(false);

            // Time display
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
            timeLabel.setFontScale(0.8f);
            timeLabel.setColor(timeColor);
            timeLabel.setAlignment(Align.right);

            // Layout
            table.add(lineBtn).size(40, 32).padRight(8).left();
            table.add(timeLabel).width(55).right().padRight(10);
            table.row().padTop(6).padBottom(6);

            // Separator between items
            if (i < upcomingArrivals.size() - 1) {
                Image separator = new Image(skin.getDrawable("white"));
                separator.setColor(0.8f, 0.8f, 0.8f, 0.3f);
                table.add(separator).colspan(2).height(1).fillX().padTop(6).padBottom(6).expandX();
                table.row();
            }
        }

        // Footer with day type and time
        String dayTypeStr;
        switch (dayType) {
            case 0: dayTypeStr = "Delovnik"; break;
            case 1: dayTypeStr = "Sobota"; break;
            case 2: dayTypeStr = "Nedelja"; break;
            default: dayTypeStr = "Neznano";
        }

        Label footerLabel = new Label(
            dayTypeStr + " • " + BusSchedule.formatTime(currentTime),
            skin,
            "black"
        );
        footerLabel.setFontScale(0.6f);
        footerLabel.setColor(Color.GRAY);
        footerLabel.setAlignment(Align.center);

        table.add(footerLabel).colspan(2).padTop(15).padBottom(10).center();
        table.row();

        return table;
    }

    public void setBusLines(List<BusLine> lines) {
        this.allLines = lines;
        if (visible && currentStop != null) {
            rebuildUI();
        }
    }

    public void refresh() {
        if (visible && currentStop != null) {
            rebuildUI();
        }
    }

    public void hide() {
        this.visible = false;
        mainPanel.setVisible(false);

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

    public void setVisibilityChangeCallback(VisibilityChangeCallback callback) {
        this.visibilityCallback = callback;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);

        float panelWidth = width / 8f;
        mainPanel.setSize(panelWidth, height);
        mainPanel.setPosition(width / Constants.HUD_WIDTH, 0);

        if (visible && currentStop != null) {
            rebuildUI();
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

    public interface VisibilityChangeCallback {
        void onVisibilityChanged(boolean visible);
    }
}
