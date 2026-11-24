package com.mbus.app.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.CityTime;
import com.mbus.app.utils.StopSearch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The main panel: search, line filter and stop list. */
public class Sidebar {

    public interface Listener {
        void onVisibleLinesChanged(Set<BusLine> visibleLines, List<BusStop> stopsOnThem);

        void onShowStopsChanged(boolean show);

        void onStopChosen(BusStop stop);

        void onCollapsedChanged();
    }

    private static final float PADDING = 16f;
    private static final int CHIPS_PER_ROW = 6;
    private static final float CHIP_GAP = 6f;

    private final Theme theme;
    private final Listener listener;
    private final Card card;
    private final Table content = new Table();
    private final Cell<?> contentCell;

    private final Label clock;
    private final IconButton collapseButton;
    private final TextField searchField;
    private final IconButton clearButton;
    private final LinkButton allLinesButton;
    private final Switch showStopsSwitch;
    private final Label stopCount;
    private final Label emptyLabel;
    private final StopListView stopList;
    private final ScrollPane stopScroll;
    private final Map<BusLine, TextButton> chips = new LinkedHashMap<>();
    private final Label status;
    private final Timetable timetable;

    private final List<BusStop> allStops;
    private final List<BusLine> allLines;
    private final Set<BusLine> visibleLines = new HashSet<>();
    private List<BusStop> stopsOnVisibleLines;
    private List<BusStop> listedStops = new ArrayList<>();
    private boolean collapsed = false;
    private int shownMinute = -1;

    public Sidebar(Theme theme, Timetable timetable, Listener listener) {
        this.theme = theme;
        this.listener = listener;
        this.timetable = timetable;
        this.allStops = timetable.getStops();
        this.allLines = timetable.getLines();
        visibleLines.addAll(allLines);
        stopsOnVisibleLines = allStops;

        status = theme.label("", theme.caption, Palette.MUTED);
        clock = theme.label("", theme.bodyStrong, Palette.TEXT);
        collapseButton = new IconButton(theme, Icon.CHEVRON_UP, 18, this::toggleCollapsed);
        searchField = createSearchField();
        clearButton = new IconButton(theme, Icon.CLOSE, 14, this::clearSearch);
        clearButton.setVisible(false);
        allLinesButton = new LinkButton(theme, "Skrij vse", this::toggleAllLines);
        showStopsSwitch = new Switch(theme, true);
        showStopsSwitch.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Sidebar.this.listener.onShowStopsChanged(showStopsSwitch.isChecked());
            }
        });
        stopCount = theme.label("", theme.caption, Palette.FAINT);
        emptyLabel = theme.label("", theme.body, Palette.MUTED);
        emptyLabel.setAlignment(Align.center);
        emptyLabel.setWrap(true);

        stopList = new StopListView(theme, this::choose);
        stopScroll = new ScrollPane(stopList, theme.scrollPane());
        stopScroll.setScrollingDisabled(true, false);
        stopScroll.setOverscroll(false, false);
        stopScroll.setScrollbarsOnTop(true);
        ScrollOnHover.attach(stopScroll);

        card = new Card(theme);
        card.top();
        card.add(createHeader()).growX().pad(PADDING, PADDING, PADDING, PADDING - 6).row();
        contentCell = card.add(content).grow();
        fillContent();

        refreshStops();
        update();
    }

    public Actor getActor() {
        return card;
    }

    // Layout

    private Table createHeader() {
        Label name = theme.label("MBus", theme.brand, Palette.INK);

        Table names = new Table();
        names.left();
        names.add(name).left().row();
        names.add(status).left().padTop(-2);

        Table header = new Table();
        header.add(new Image(theme.logo(40))).size(40).padRight(12);
        header.add(names).growX();
        header.add(clock).padRight(8);
        header.add(collapseButton).size(32);
        return header;
    }

    private void fillContent() {
        content.clear();
        content.top();

        content.add(createSearch()).growX().height(42).pad(0, PADDING, 20, PADDING).row();

        Table linesHeader = new Table();
        linesHeader.add(theme.label("Linije", theme.bodyStrong, Palette.INK)).left().expandX();
        linesHeader.add(allLinesButton);
        content.add(linesHeader).growX().pad(0, PADDING, 10, PADDING).row();
        content.add(createChips()).growX().pad(0, PADDING - CHIP_GAP / 2f, 18, PADDING - CHIP_GAP / 2f).row();

        content.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();

        Table stopsHeader = new Table();
        stopsHeader.add(theme.label("Postaje", theme.bodyStrong, Palette.INK)).padRight(8);
        stopsHeader.add(stopCount).left().expandX();
        stopsHeader.add(theme.label("Na zemljevidu", theme.caption, Palette.MUTED)).padRight(8);
        stopsHeader.add(showStopsSwitch);
        content.add(stopsHeader).growX().pad(16, PADDING, 8, PADDING).row();

        Container<Label> empty = new Container<>(emptyLabel);
        empty.fillX().top().padTop(32);
        empty.setTouchable(Touchable.disabled);

        Stack list = new Stack(stopScroll, empty);
        content.add(list).grow().pad(0, 4, 0, 4).row();

        boolean expired = timetable.isExpired(CityTime.today());
        Label validity = theme.label(
            (expired ? "Vozni red je potekel " : "Po voznem redu Marproma do ") + CityTime.format(timetable.getValidUntil()),
            theme.caption, expired ? Palette.NOW : Palette.FAINT);
        content.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();
        content.add(validity).left().pad(9, PADDING, 11, PADDING);
    }

    private Stack createSearch() {
        Table icons = new Table();
        icons.setTouchable(Touchable.childrenOnly);
        Image magnifier = new Image(theme.icon(Icon.SEARCH, 18, Palette.MUTED));
        magnifier.setTouchable(Touchable.disabled);
        icons.add(magnifier).size(18).padLeft(12);
        icons.add().growX();
        icons.add(clearButton).size(28).padRight(7);
        return new Stack(searchField, icons);
    }

    private TextField createSearchField() {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = theme.body;
        style.fontColor = Palette.INK;
        style.messageFont = theme.body;
        style.messageFontColor = Palette.MUTED;
        style.background = padded(theme.roundRect(10, Palette.SUBTLE));
        style.focusedBackground = padded(theme.roundRect(10, Palette.SURFACE, Palette.BRAND, 1.5f));
        style.cursor = theme.fill(Palette.BRAND, 1.5f, 1);
        style.selection = theme.fill(Color.valueOf("f3c9d8"), 1, 1);

        final TextField field = new TextField("", style);
        field.setMessageText("Išči postajo ali številko");
        field.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshStops();
            }
        });
        field.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    clearSearch();
                    stopTyping();
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (!field.getText().trim().isEmpty() && !listedStops.isEmpty()) {
                        stopTyping();
                        choose(listedStops.get(0));
                    }
                    return true;
                }
                return false;
            }
        });
        return field;
    }

    private static Drawable padded(Drawable drawable) {
        drawable.setLeftWidth(40);
        drawable.setRightWidth(38);
        return drawable;
    }

    private Table createChips() {
        Table table = new Table();
        table.defaults().height(34).growX().uniformX().pad(CHIP_GAP / 2f);

        int column = 0;
        for (final BusLine line : allLines) {
            final TextButton chip = new TextButton(line.name, chipStyle(line));
            chip.setProgrammaticChangeEvents(false);
            chip.setChecked(true);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (chip.isChecked()) {
                        visibleLines.add(line);
                    } else {
                        visibleLines.remove(line);
                    }
                    onVisibleLinesChanged();
                }
            });
            chips.put(line, chip);
            table.add(chip).minWidth(0).prefWidth(0);
            if (++column % CHIPS_PER_ROW == 0) table.row();
        }
        while (column++ % CHIPS_PER_ROW != 0) table.add();
        return table;
    }

    private TextButton.TextButtonStyle chipStyle(BusLine line) {
        Color color = line.color;
        Color hover = new Color(color).lerp(Color.WHITE, 0.14f);
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = theme.bodyStrong;
        style.fontColor = Palette.FAINT;
        style.overFontColor = Palette.TEXT;
        style.checkedFontColor = line.textColor;
        style.checkedOverFontColor = line.textColor;
        style.up = theme.roundRect(8, Palette.SURFACE, Palette.BORDER, 1);
        style.over = theme.roundRect(8, Palette.SUBTLE, Palette.BORDER, 1);
        style.checked = theme.roundRect(8, color);
        style.checkedOver = theme.roundRect(8, hover);
        return style;
    }

    public void layout(float x, float top, float width, float maxHeight) {
        float height = collapsed ? card.getPrefHeight() : maxHeight;
        card.setBounds(x, top - height, width, height);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        collapseButton.setIcon(collapsed ? Icon.CHEVRON_DOWN : Icon.CHEVRON_UP);
        contentCell.setActor(collapsed ? null : content);
        card.invalidate();
        stopTyping();
        listener.onCollapsedChanged();
    }

    // Search

    public boolean isTyping() {
        return card.getStage() != null && card.getStage().getKeyboardFocus() == searchField;
    }

    public void stopTyping() {
        if (card.getStage() != null) card.getStage().setKeyboardFocus(null);
    }

    private void clearSearch() {
        searchField.setText("");
        refreshStops();
    }

    private void refreshStops() {
        String query = searchField.getText();
        boolean searching = !query.trim().isEmpty();

        if (searching) {
            listedStops = new ArrayList<>();
            for (BusStop stop : stopsOnVisibleLines) {
                if (StopSearch.matches(stop, query)) listedStops.add(stop);
            }
        } else {
            listedStops = stopsOnVisibleLines;
        }

        stopList.setStops(listedStops);
        stopScroll.setScrollY(0);
        stopScroll.updateVisualScroll();
        clearButton.setVisible(!query.isEmpty());
        stopCount.setText(listedStops.size() == allStops.size()
            ? String.valueOf(allStops.size())
            : listedStops.size() + " od " + allStops.size());
        emptyLabel.setText(!listedStops.isEmpty() ? ""
            : searching ? "Ni zadetkov za »" + query.trim() + "«" : "Na izbranih linijah ni postaj");
    }

    private void choose(BusStop stop) {
        setShowStops(true);
        listener.onStopChosen(stop);
    }

    // Lines

    private void toggleAllLines() {
        boolean showAll = visibleLines.size() < allLines.size();
        visibleLines.clear();
        if (showAll) visibleLines.addAll(allLines);
        onVisibleLinesChanged();
    }

    public void showOnlyLine(BusLine line) {
        visibleLines.clear();
        visibleLines.add(line);
        onVisibleLinesChanged();
    }

    public void showAllLines() {
        visibleLines.addAll(allLines);
        onVisibleLinesChanged();
    }

    private void onVisibleLinesChanged() {
        for (Map.Entry<BusLine, TextButton> chip : chips.entrySet()) {
            chip.getValue().setChecked(visibleLines.contains(chip.getKey()));
        }
        boolean all = visibleLines.size() == allLines.size();
        allLinesButton.setText(all ? "Skrij vse" : "Pokaži vse");

        stopsOnVisibleLines = all || visibleLines.isEmpty() ? allStops : new ArrayList<>();
        if (stopsOnVisibleLines != allStops) {
            for (BusStop stop : allStops) {
                for (BusLine line : stop.getLines()) {
                    if (visibleLines.contains(line)) {
                        stopsOnVisibleLines.add(stop);
                        break;
                    }
                }
            }
        }
        refreshStops();
        listener.onVisibleLinesChanged(getVisibleLines(), stopsOnVisibleLines);
    }

    public Set<BusLine> getVisibleLines() {
        return new HashSet<>(visibleLines);
    }

    // Stops

    public void setShowStops(boolean show) {
        showStopsSwitch.setChecked(show);
    }

    /** Highlights a stop in the list and scrolls to it. */
    public void setSelectedStop(BusStop stop) {
        stopList.setSelected(stop);
        float top = stop == null ? -1 : stopList.rowTop(stop);
        if (top < 0) return;

        float viewTop = stopScroll.getScrollY();
        float viewHeight = stopScroll.getScrollHeight();
        if (top < viewTop || top + stopList.rowHeight() > viewTop + viewHeight) {
            stopScroll.setScrollY(MathUtils.clamp(
                top - (viewHeight - stopList.rowHeight()) / 2f, 0, Math.max(0, stopScroll.getMaxY())));
        }
    }

    public void update() {
        int minute = CityTime.minutes();
        if (minute != shownMinute) {
            shownMinute = minute;
            clock.setText(CityTime.format(minute));
            status.setText(describeTraffic(minute));
        }
    }

    private String describeTraffic(int minute) {
        LocalDate today = CityTime.today();
        int running = 0;
        for (Trip trip : timetable.getTrips(today)) {
            if (trip.getStart() > minute) {
                if (running == 0) return "Naslednji avtobus ob " + CityTime.format(trip.getStart());
                break;
            }
            if (trip.getEnd() >= minute) running++;
        }
        if (running > 0) return running + " " + buses(running) + " na poti";

        List<Trip> tomorrow = timetable.getTrips(today.plusDays(1));
        return tomorrow.isEmpty() ? "Maribor" : "Naslednji avtobus jutri ob " + CityTime.format(tomorrow.get(0).getStart());
    }

    /** Slovene has a singular, a dual and two plurals. */
    static String buses(int count) {
        switch (count % 100) {
            case 1: return "avtobus";
            case 2: return "avtobusa";
            case 3:
            case 4: return "avtobusi";
            default: return "avtobusov";
        }
    }
}
