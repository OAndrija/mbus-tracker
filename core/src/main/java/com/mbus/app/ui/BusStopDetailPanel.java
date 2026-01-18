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
    private Texture timeIcon;

    private String scheduleTime;
    private int scheduleDayOfWeek;

    public BusStopDetailPanel(Skin skin, Texture timeIcon) {
        this.skin = skin;
        this.timeIcon = timeIcon;
        this.stage = new Stage(new ScreenViewport());
        this.visible = false;

        createUI();
    }

    private void createUI() {
        mainPanel = new Table();
        mainPanel.setFillParent(false);

        float panelWidth = Gdx.graphics.getWidth() / 7f;
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

    public void updateCurrentTime(String time, int dayOfWeek) {
        this.scheduleTime = time;
        this.scheduleDayOfWeek = dayOfWeek;
    }

    public void setScheduleTime(String time, int dayOfWeek) {
        this.scheduleTime = time;
        this.scheduleDayOfWeek = dayOfWeek;
    }

    private void rebuildUI() {
        mainPanel.clear();
        float panelWidth = mainPanel.getWidth();

        Table headerTable = new Table();

        Label titleLabel = new Label("Postajalisce", skin, "title-black");
        titleLabel.setFontScale(0.8f);
        titleLabel.setAlignment(Align.left);

        TextButton closeBtn = new TextButton("<", skin, "maroon-small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hide();
            }
        });

        headerTable.add(titleLabel).expandX().left().padLeft(10);
        headerTable.add(closeBtn).size(25, 25).padRight(10);

        mainPanel.add(headerTable).width(panelWidth).padTop(20).padBottom(15).row();

        Table stopInfoTable = new Table();

        TextButton idBtn = new TextButton(String.valueOf(currentStop.idAvpost), skin, "maroon-small");

        Label stopNameLabel = new Label(currentStop.name, skin, "black");
        stopNameLabel.setWrap(true);
        stopNameLabel.setAlignment(Align.left);

        stopInfoTable.add(idBtn).width(35).height(40).padRight(8).left();
        stopInfoTable.add(stopNameLabel).expandX().fillX().left();

        mainPanel.add(stopInfoTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(10).padBottom(30).row();

        Image separator1 = new Image(skin.getDrawable("white"));
        separator1.setColor(0.8f, 0.8f, 0.8f, 0.3f);
        mainPanel.add(separator1).width(panelWidth - 20).height(1).padLeft(10).padRight(10).padBottom(20).row();

        Table arrivalsHeaderTable = new Table();

        Label arrivalsHeaderLabel = new Label("Prihodi", skin, "title-black");
        arrivalsHeaderLabel.setFontScale(0.8f);

        arrivalsHeaderTable.add(arrivalsHeaderLabel).expandX().left().padLeft(10);

        if (timeIcon != null) {
            Image timeIconImage = new Image(timeIcon);
            timeIconImage.getColor().a = 1f;
            arrivalsHeaderTable.add(timeIconImage).size(25, 25).padRight(40);
        }

        mainPanel.add(arrivalsHeaderTable).width(panelWidth).padTop(5).padBottom(10).row();

        Table arrivalsTable = createUpcomingArrivalsTable(panelWidth);
        ScrollPane arrivalsScrollPane = new ScrollPane(arrivalsTable, skin, "no-bg");
        arrivalsScrollPane.setFadeScrollBars(true);
        arrivalsScrollPane.setScrollingDisabled(true, false);
        arrivalsScrollPane.setOverscroll(false, false);

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

        int currentTime;
        int dayType;

        if (scheduleTime != null) {
            currentTime = parseTimeToMinutes(scheduleTime);
            dayType = convertDayOfWeekToDayType(scheduleDayOfWeek);
        } else {
            currentTime = BusPositionCalculator.getCurrentTimeMinutes();
            dayType = BusPositionCalculator.getCurrentDayType();
        }

        List<BusStop.StopArrival> upcomingArrivals =
            currentStop.getUpcomingArrivals(allLines, currentTime, dayType, 20);

        if (upcomingArrivals.isEmpty()) {
            Label emptyLabel = new Label("Ni vec prihodov danes", skin, "black");
            emptyLabel.setColor(Color.GRAY);
            table.add(emptyLabel).pad(10);
            return table;
        }

        for (int i = 0; i < upcomingArrivals.size(); i++) {
            BusStop.StopArrival arrival = upcomingArrivals.get(i);
            BusLine line = arrival.line;

            TextButton lineBtn = new TextButton(String.valueOf(line.lineId), skin, "orange-small");
            Color lineColor = BusLineColors.getButtonColor(line.lineId);
            lineBtn.setColor(lineColor);
            lineBtn.setChecked(false);

            int minutesUntil = arrival.getMinutesUntilArrival(currentTime);
            String timeText;
            Color timeColor;

            if (minutesUntil == 0) {
                timeText = "Zdaj";
                timeColor = new Color(1f, 0.3f, 0.3f, 1f);
            } else if (minutesUntil <= 5) {
                timeText = minutesUntil + " min";
                timeColor = new Color(1f, 0.6f, 0.2f, 1f);
            } else if (minutesUntil <= 15) {
                timeText = minutesUntil + " min";
                timeColor = new Color(0.8f, 0.8f, 0.8f, 1f);
            } else {
                timeText = arrival.getArrivalTimeFormatted();
                timeColor = Color.GRAY;
            }

            Label timeLabel = new Label(timeText, skin, "black");
            timeLabel.setFontScale(0.9f);
            timeLabel.setColor(timeColor);
            timeLabel.setAlignment(Align.right);

            table.add(lineBtn).size(38, 38).padRight(14).padLeft(14).padTop(10).padBottom(10).left();
            table.add(timeLabel).width(55).right().padRight(20);
            table.row().padTop(10).padBottom(10);

            if (i < upcomingArrivals.size() - 1) {
                Image separator = new Image(skin.getDrawable("white"));
                separator.setColor(0.8f, 0.8f, 0.8f, 0.3f);
                table.add(separator).colspan(2).height(1).fillX().padTop(10).padBottom(10).expandX();
                table.row();
            }
        }

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

    private int parseTimeToMinutes(String time) {
        try {
            String[] parts = time.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            return hours * 60 + minutes;
        } catch (Exception e) {
            Gdx.app.error("BusStopDetailPanel", "Failed to parse time: " + time);
            return BusPositionCalculator.getCurrentTimeMinutes();
        }
    }

    private int convertDayOfWeekToDayType(int dayOfWeek) {
        if (dayOfWeek >= 1 && dayOfWeek <= 5) {
            return 0;
        } else if (dayOfWeek == 6) {
            return 1;
        } else if (dayOfWeek == 7) {
            return 2;
        }
        return 0;
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

        float panelWidth = width / 7f;
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
