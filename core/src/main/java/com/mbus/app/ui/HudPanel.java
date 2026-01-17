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
    private TextField timeField;
    private ScrollPane busStopsScrollPane;
    private TextButton allStopsBtn;
    private Label currentTimeLabel;

    // Data
    private List<BusStop> allBusStops;
    private List<BusLine> busLines;
    private Set<Integer> visibleLineIds;
    private boolean showingAllStops = true;

    // Time management
    private String currentTime = "";
    private int currentDayOfWeek = 1; // 1=Monday, 7=Sunday

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

    public interface TimeChangedCallback {
        void onTimeChanged(String time, int dayOfWeek);
    }

    private ShowAllStopsCallback showAllStopsCallback;
    private BusStopClickCallback busStopClickCallback;
    private BusLineVisibilityCallback busLineVisibilityCallback;
    private FilteredStopsCallback filteredStopsCallback;
    private TimeChangedCallback timeChangedCallback;

    public HudPanel(Skin skin, Texture titleIcon) {
        this.skin = skin;
        this.titleIcon = titleIcon;
        this.stage = new Stage(new ScreenViewport());
        this.allBusStops = new ArrayList<BusStop>();
        this.busLines = new ArrayList<BusLine>();
        this.visibleLineIds = new HashSet<Integer>();

        // Initialize with current time
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = calendar.get(java.util.Calendar.MINUTE);
        this.currentTime = String.format("%02d:%02d", hour, minute);
        this.currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        // Convert from Sunday=1 to Monday=1
        this.currentDayOfWeek = (this.currentDayOfWeek == 1) ? 7 : this.currentDayOfWeek - 1;

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

//        // Create time input section
//        Table timeTable = new Table();
//
//        timeField = new TextField(currentTime, skin, "search");
//        timeField.setMessageText("HH:MM");
//        timeField.setMaxLength(5);
//        timeField.addListener(new ChangeListener() {
//            @Override
//            public void changed(ChangeEvent event, Actor actor) {
//                String text = timeField.getText();
//                if (isValidTimeFormat(text)) {
//                    currentTime = text;
//                    if (timeChangedCallback != null) {
//                        timeChangedCallback.onTimeChanged(currentTime, currentDayOfWeek);
//                    }
//                    Gdx.app.log("HudPanel", "Time changed to: " + currentTime);
//                }
//            }
//        });
//        timeTable.add(timeField).width(120);
//
//        // Day of week buttons
//        TextButton prevDayBtn = new TextButton("<", skin, "maroon-small");
//        prevDayBtn.addListener(new ChangeListener() {
//            @Override
//            public void changed(ChangeEvent event, Actor actor) {
//                currentDayOfWeek--;
//                if (currentDayOfWeek < 1) currentDayOfWeek = 7;
//                updateDayLabel();
//                if (timeChangedCallback != null) {
//                    timeChangedCallback.onTimeChanged(currentTime, currentDayOfWeek);
//                }
//            }
//        });
//
//        currentTimeLabel = new Label(getDayName(currentDayOfWeek), skin, "default");
//        currentTimeLabel.setFontScale(0.65f);
//        currentTimeLabel.setAlignment(Align.center);
//
//        TextButton nextDayBtn = new TextButton(">", skin, "maroon-small");
//        nextDayBtn.addListener(new ChangeListener() {
//            @Override
//            public void changed(ChangeEvent event, Actor actor) {
//                currentDayOfWeek++;
//                if (currentDayOfWeek > 7) currentDayOfWeek = 1;
//                updateDayLabel();
//                if (timeChangedCallback != null) {
//                    timeChangedCallback.onTimeChanged(currentTime, currentDayOfWeek);
//                }
//            }
//        });
//
//        timeTable.add(prevDayBtn).width(25).height(25).padLeft(8);
//        timeTable.add(currentTimeLabel).width(50).padLeft(3).padRight(3);
//        timeTable.add(nextDayBtn).width(25).height(25);
//
//        mainPanel.add(timeTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).padBottom(5).row();

        // Create search field (commented out as before)
//        searchField = new TextField("", skin, "search");
//        searchField.setMessageText("Išči postajališče...");
//        mainPanel.add(searchField).width(panelWidth - 20).padLeft(10).padRight(10).padTop(5).row();

        Label linesLabel = new Label("Linije", skin, "title-black");
        linesLabel.setFontScale(0.8f);
        mainPanel.add(linesLabel).left().padLeft(10).padTop(15).padBottom(15).row();

        Table busLinesTable = createBusLinesTable();
        mainPanel.add(busLinesTable).width(panelWidth - 20).padLeft(10).padBottom(20).padRight(10).row();

        Table allButtonsTable = new Table();
        TextButton allLinesBtn = new TextButton("Linije", skin, "orange-small-toggle");
        allLinesBtn.setColor(0.663f, 0.620f, 0.58f, 1f);
        allLinesBtn.setChecked(!visibleLineIds.isEmpty());

        allLinesBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleAllLines();
            }
        });

        allStopsBtn = new TextButton("Postaje", skin, "orange-small-toggle");
        allStopsBtn.setColor(0.663f, 0.620f, 0.58f, 1f);
        allStopsBtn.setChecked(showingAllStops); // Set initial checked state

        allStopsBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleShowAllStops();
            }
        });

        allButtonsTable.add(allLinesBtn).width((panelWidth - 30) / 2f).padRight(5);
        allButtonsTable.add(allStopsBtn).width((panelWidth - 30) / 2f).padLeft(5);

        mainPanel.add(allButtonsTable).width(panelWidth - 20).padLeft(10).padRight(10).padTop(10).row();

        Label stopsLabel = new Label("Postajalisca", skin, "title-black");
        stopsLabel.setFontScale(0.8f);
        mainPanel.add(stopsLabel).left().padLeft(10).padTop(30).padBottom(25).row();

        Table busStopsTable = createBusStopsTable();
        busStopsScrollPane = new ScrollPane(busStopsTable, skin, "no-bg");
        busStopsScrollPane.setFadeScrollBars(false);
        busStopsScrollPane.setScrollingDisabled(true, false);
        mainPanel.add(busStopsScrollPane).width(panelWidth - 20).expand().fill().padLeft(10).padRight(10).padBottom(10).row();

        stage.addActor(mainPanel);
    }

    private List<BusStop> getFilteredStops() {
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

        List<BusStop> filtered = new ArrayList<BusStop>();

        for (BusStop stop : allBusStops) {
            for (Integer lineId : stop.getLineIds()) {
                if (visibleLineIds.contains(lineId)) {
                    filtered.add(stop);
                    break;
                }
            }
        }

        return filtered;
    }

    private void toggleAllLines() {
        if (visibleLineIds.isEmpty()) {
            for (BusLine line : busLines) {
                visibleLineIds.add(line.lineId);
            }
        } else {
            visibleLineIds.clear();
        }

        refreshBusLinesTable();
        onLineVisibilityChanged();
    }

    private void toggleShowAllStops() {
        if (updatingProgrammatically) {
            return;
        }

        showingAllStops = !showingAllStops;

        if (allStopsBtn != null) {
            allStopsBtn.setChecked(showingAllStops);
        }

        refreshBusStopsTable();

        if (showAllStopsCallback != null) {
            showAllStopsCallback.onShowAllStopsChanged(showingAllStops);
        }
    }

    private void onLineVisibilityChanged() {
        refreshBusStopsTable();

        if (busLineVisibilityCallback != null) {
            busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
        }

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

        Set<Integer> uniqueLineIds = new TreeSet<Integer>();
        for (BusLine line : busLines) {
            uniqueLineIds.add(line.lineId);
        }

        float buttonSize = 45f;
        int buttonsPerRow = 6;
        int count = 0;

        for (final Integer lineId : uniqueLineIds) {
            final TextButton btn = new TextButton(String.valueOf(lineId), skin, "orange-small-toggle");

            Color lineColor = BusLineColors.getButtonColor(lineId);
            btn.setColor(lineColor);

            btn.setChecked(visibleLineIds.contains(lineId));

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

        List<BusStop> stopsToShow = getFilteredStops();

        boolean isFiltering = stopsToShow.size() < allBusStops.size();

        if (showingAllStops && stopsToShow != null && !stopsToShow.isEmpty()) {
            for (int i = 0; i < stopsToShow.size(); i++) {
                final BusStop stop = stopsToShow.get(i);

                TextButton idBtn = new TextButton(String.valueOf(stop.idAvpost), skin, "maroon-small");

                String labelText = stop.name;
                if (isFiltering && stop.getLineCount() > 0) {
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

        visibleLineIds.clear();
        for (BusLine line : this.busLines) {
            visibleLineIds.add(line.lineId);
        }

        refreshBusLinesTable();

        if (busLineVisibilityCallback != null) {
            busLineVisibilityCallback.onBusLineVisibilityChanged(new HashSet<Integer>(visibleLineIds));
        }
    }

    public void selectOnlyLine(int lineId) {
        visibleLineIds.clear();
        visibleLineIds.add(lineId);

        if (!showingAllStops) {
            setShowAllStops(true);
        }

        refreshBusLinesTable();
        onLineVisibilityChanged();
    }

    public void selectAllLines() {
        visibleLineIds.clear();
        for (BusLine line : busLines) {
            visibleLineIds.add(line.lineId);
        }

        if (!showingAllStops) {
            setShowAllStops(true);
        }

        refreshBusLinesTable();
        onLineVisibilityChanged();
    }

    private boolean updatingProgrammatically = false;

    public void setShowAllStops(boolean show) {
        if (showingAllStops != show && !updatingProgrammatically) {
            updatingProgrammatically = true;

            showingAllStops = show;

            if (allStopsBtn != null) {
                allStopsBtn.setChecked(showingAllStops);
            }

            refreshBusStopsTable();

            if (showAllStopsCallback != null) {
                showAllStopsCallback.onShowAllStopsChanged(showingAllStops);
            }

            updatingProgrammatically = false;
        }
    }

    private void refreshBusStopsTable() {
        Table newBusStopsTable = createBusStopsTable();
        busStopsScrollPane.setActor(newBusStopsTable);
    }

    private void refreshBusLinesTable() {
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

    public void setTimeChangedCallback(TimeChangedCallback callback) {
        this.timeChangedCallback = callback;
    }

    public String getCurrentTime() {
        return currentTime;
    }

    public int getCurrentDayOfWeek() {
        return currentDayOfWeek;
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || time.length() != 5) return false;
        if (time.charAt(2) != ':') return false;

        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        } catch (Exception e) {
            return false;
        }
    }

    private String getDayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1: return "Pon";
            case 2: return "Tor";
            case 3: return "Sre";
            case 4: return "Čet";
            case 5: return "Pet";
            case 6: return "Sob";
            case 7: return "Ned";
            default: return "Pon";
        }
    }

    private void updateDayLabel() {
        if (currentTimeLabel != null) {
            currentTimeLabel.setText(getDayName(currentDayOfWeek));
        }
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
