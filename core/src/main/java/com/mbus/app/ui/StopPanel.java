package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.BusLineColors;
import com.mbus.app.utils.BusPositionCalculator;

import java.util.List;

/** The upcoming arrivals at one stop. */
public class StopPanel {

    private static final float PADDING = 16f;
    private static final int MAX_ARRIVALS = 20;
    private static final float ROW_HEIGHT = 52f;

    private final Theme theme;
    private final List<BusLine> lines;
    private final Runnable onClose;
    private final Card card;

    private BusStop stop;
    private ScrollPane scroll;
    private int shownMinute = -1;
    private float x, top, width, maxHeight;

    public StopPanel(Theme theme, List<BusLine> lines, Runnable onClose) {
        this.theme = theme;
        this.lines = lines;
        this.onClose = onClose;
        card = new Card(theme);
        card.top();
        card.setVisible(false);
    }

    public Actor getActor() {
        return card;
    }

    public boolean isVisible() {
        return stop != null;
    }

    public void show(BusStop newStop) {
        boolean wasVisible = isVisible();
        stop = newStop;
        scroll = null;
        rebuild();

        if (!wasVisible) {
            card.clearActions();
            card.setVisible(true);
            card.setTouchable(Touchable.enabled);
            card.getColor().a = 0f;
            card.addAction(Actions.fadeIn(0.16f));
        }
    }

    public void hide() {
        if (stop == null) return;
        stop = null;
        card.clearActions();
        card.setTouchable(Touchable.disabled);
        card.addAction(Actions.sequence(Actions.fadeOut(0.12f), Actions.visible(false)));
        onClose.run();
    }

    public void layout(float x, float top, float width, float maxHeight) {
        this.x = x;
        this.top = top;
        this.width = width;
        this.maxHeight = maxHeight;
        applyBounds();
    }

    /** As tall as its content needs, up to the available height. */
    private void applyBounds() {
        card.setWidth(width);
        card.validate(); // wrapped labels need the width before they know their height
        float height = Math.min(card.getPrefHeight(), maxHeight);
        card.setBounds(x, top - height, width, height);
    }

    public void update() {
        if (stop != null && BusPositionCalculator.getCurrentTimeMinutes() != shownMinute) {
            rebuild();
        }
    }

    private void rebuild() {
        float scrollY = scroll != null ? scroll.getScrollY() : 0f;
        int now = BusPositionCalculator.getCurrentTimeMinutes();
        int dayType = BusPositionCalculator.getCurrentDayType();
        shownMinute = now;

        card.clearChildren();
        card.add(createHeader()).growX().pad(PADDING, PADDING, 14, PADDING - 6).row();
        card.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();

        Table arrivalsHeader = new Table();
        arrivalsHeader.add(theme.label("Prihodi", theme.bodyStrong, Palette.INK)).left().expandX();
        arrivalsHeader.add(theme.label(dayName(dayType), theme.caption, Palette.MUTED));
        card.add(arrivalsHeader).growX().pad(14, PADDING, 4, PADDING).row();

        List<BusStop.StopArrival> arrivals = stop.getUpcomingArrivals(lines, now, dayType, MAX_ARRIVALS);
        if (arrivals.isEmpty()) {
            scroll = null;
            card.add(createEmptyState()).growX().pad(28, PADDING, 36, PADDING);
        } else {
            scroll = new ScrollPane(createArrivals(arrivals, now), theme.scrollPane());
            scroll.setScrollingDisabled(true, false);
            scroll.setOverscroll(false, false);
            scroll.setScrollbarsOnTop(true);
            ScrollOnHover.attach(scroll);
            card.add(scroll).growX().expandY().top().padBottom(8);
        }

        applyBounds();
        if (scroll != null) {
            card.validate();
            scroll.setScrollY(scrollY);
            scroll.updateVisualScroll();
        }
    }

    private Table createHeader() {
        Label name = theme.label(stop.name, theme.title, Palette.INK);
        name.setWrap(true);

        Table text = new Table();
        text.left();
        text.add(name).growX().left().row();
        text.add(theme.label("Postaja " + stop.idAvpost, theme.caption, Palette.MUTED)).left().padTop(2);

        Table header = new Table();
        header.add(text).growX().top();
        header.add(new IconButton(theme, Icon.CLOSE, 16, this::hide)).size(32).top();
        return header;
    }

    private Table createArrivals(List<BusStop.StopArrival> arrivals, int now) {
        Table table = new Table();
        table.top();

        for (int i = 0; i < arrivals.size(); i++) {
            if (i > 0) {
                table.add(new Image(theme.fill(Palette.SUBTLE, 1, 1)))
                    .growX().height(1).padLeft(PADDING).padRight(PADDING).row();
            }
            table.add(createArrival(arrivals.get(i), now))
                .growX().height(ROW_HEIGHT).padLeft(PADDING).padRight(PADDING).row();
        }
        return table;
    }

    private Table createArrival(BusStop.StopArrival arrival, int now) {
        int minutes = arrival.getMinutesUntilArrival(now);
        Color timeColor = minutes == 0 ? Palette.NOW : minutes <= 5 ? Palette.SOON : Palette.INK;

        Label direction = theme.label(terminus(arrival.line), theme.body, Palette.TEXT);
        direction.setEllipsis(true);

        Table time = new Table();
        time.right();
        if (minutes > 60) {
            time.add(theme.label(arrival.getArrivalTimeFormatted(), theme.bodyStrong, timeColor)).right();
        } else {
            time.add(theme.label(minutes == 0 ? "Zdaj" : minutes + " min", theme.bodyStrong, timeColor)).right().row();
            time.add(theme.label(arrival.getArrivalTimeFormatted(), theme.caption, Palette.FAINT)).right();
        }

        Table row = new Table();
        row.add(linePill(theme, arrival.line.lineId)).minWidth(42).height(28).padRight(12);
        row.add(direction).growX().minWidth(0).prefWidth(0).left();
        row.add(time).padLeft(8);
        return row;
    }

    static Table linePill(Theme theme, int lineId) {
        Table pill = new Table();
        pill.setBackground(theme.roundRect(7, BusLineColors.getColor(lineId)));
        pill.add(theme.label(String.valueOf(lineId), theme.bodyStrong, Color.WHITE)).padLeft(6).padRight(6);
        return pill;
    }

    private static String terminus(BusLine line) {
        List<BusStop> stops = line.getStops();
        return stops.isEmpty() ? "" : stops.get(stops.size() - 1).name;
    }

    private Table createEmptyState() {
        Label message = theme.label(
            stop.getLineCount() == 0 ? "Na tej postaji ni linij" : "Danes ni več prihodov",
            theme.body, Palette.MUTED);
        message.setAlignment(Align.center);

        Table empty = new Table();
        empty.add(new Image(theme.icon(Icon.CLOCK, 32, Palette.FAINT))).size(32).padBottom(10).row();
        empty.add(message);
        return empty;
    }

    private static String dayName(int dayType) {
        switch (dayType) {
            case 1: return "Sobota";
            case 2: return "Nedelja";
            default: return "Delovnik";
        }
    }
}
