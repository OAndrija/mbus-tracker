package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.StopCall;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.CityTime;
import com.mbus.app.utils.Settings;
import com.mbus.app.utils.Texts;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** The upcoming departures from one stop. */
public class StopPanel extends FloatingPanel {

    public interface Listener {
        void onStopPanelClosed();

        void onFavoriteChanged();

        void onDepartureChosen(Trip trip, LocalDate date);

        void onJourneyToStopWanted(BusStop stop);
    }

    private static final int MAX_DEPARTURES = 20;
    /** With fewer departures left today, tomorrow's are listed too. */
    private static final int FEW_DEPARTURES = 5;
    private static final int TOMORROW_DEPARTURES = 8;
    private static final float ROW_HEIGHT = 52f;
    private static final int FILTERS_PER_ROW = 6;

    private final Timetable timetable;
    private final AppClock clock;
    private final Settings settings;
    private final Listener listener;

    private BusStop stop;
    private BusLine filter;
    private int shownMinute;
    private LocalDate shownDate;

    public StopPanel(Theme theme, Timetable timetable, AppClock clock, Settings settings, Listener listener) {
        super(theme);
        this.timetable = timetable;
        this.clock = clock;
        this.settings = settings;
        this.listener = listener;
    }

    public void show(BusStop newStop) {
        if (newStop != stop) filter = null;
        boolean sameStop = newStop == stop && isOpen();
        stop = newStop;
        rebuild(sameStop);
        open();
    }

    public BusStop getStop() {
        return stop;
    }

    public void update() {
        if (isOpen() && (clock.getMinute() != shownMinute || !clock.getDate().equals(shownDate))) {
            rebuild(true);
        }
    }

    public void toggleFavorite() {
        settings.toggleFavorite(stop.id);
        rebuild(true);
        listener.onFavoriteChanged();
    }

    @Override
    protected void build() {
        int now = clock.getMinute();
        LocalDate date = clock.getDate();
        shownMinute = now;
        shownDate = date;

        card.add(createHeader()).growX().pad(PADDING, PADDING, 14, PADDING - 6).row();
        if (stop.getLines().size() > 1) {
            card.add(createFilters()).growX().pad(0, PADDING - 3, 12, PADDING - 3).row();
        }
        card.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();

        Table departuresHeader = new Table();
        departuresHeader.add(theme.label(Texts.get("departures"), theme.bodyStrong, Palette.INK)).left().expandX();
        if (!clock.isLive()) {
            departuresHeader.add(theme.label(
                Texts.format("dayAt", Texts.day(date, clock.getRealDate()), CityTime.format(now)),
                theme.captionStrong, Palette.BRAND));
        } else if (timetable.isExpired(date)) {
            departuresHeader.add(theme.label(Texts.get("lastKnown"), theme.captionStrong, Palette.SOON));
        } else {
            departuresHeader.add(theme.label(Texts.get("byTimetable"), theme.caption, Palette.MUTED));
        }
        card.add(departuresHeader).growX().pad(14, PADDING, 4, PADDING).row();

        List<StopCall> departures = timetable.getDepartures(stop, date, now, filter, MAX_DEPARTURES);
        List<StopCall> nextDay = departures.size() >= FEW_DEPARTURES ? Collections.emptyList()
            : timetable.getDepartures(stop, date.plusDays(1), 0, filter, TOMORROW_DEPARTURES);

        if (departures.isEmpty() && nextDay.isEmpty()) {
            card.add(createEmptyState()).growX().pad(28, PADDING, 36, PADDING);
        } else {
            addScrolling(createDepartures(departures, nextDay, now, date));
        }
    }

    private Table createHeader() {
        Label name = theme.label(stop.name, theme.title, Palette.INK);
        name.setWrap(true);

        Table text = new Table();
        text.left();
        text.add(name).growX().left().row();
        text.add(theme.label(Texts.format("stop", stop.id), theme.caption, Palette.MUTED)).left().padTop(2);

        boolean favorite = settings.isFavorite(stop.id);
        IconButton star = new IconButton(theme, Icon.STAR, 18, this::toggleFavorite);
        if (favorite) star.setIcon(Icon.STAR_FILLED, Palette.STAR, Palette.STAR);
        Tips.attach(theme, star, Texts.get(favorite ? "removeFavorite" : "addFavorite"));

        Table header = new Table();
        header.add(text).growX().top();
        IconButton route = new IconButton(theme, Icon.ROUTE, 18, () -> listener.onJourneyToStopWanted(stop));
        Tips.attach(theme, route, Texts.get("directionsHere"));
        header.add(route).size(32).top();
        header.add(star).size(32).top();
        header.add(new IconButton(theme, Icon.CLOSE, 16, listener::onStopPanelClosed)).size(32).top();
        return header;
    }

    /** One chip per line; choosing one shows only its departures, choosing it again shows all. */
    private Table createFilters() {
        Table table = new Table();
        table.left();
        int column = 0;
        for (final BusLine line : stop.getLines()) {
            boolean on = filter == null || filter == line;
            TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
            style.font = theme.captionStrong;
            style.fontColor = on ? Palette.onLine(line) : Palette.FAINT;
            style.up = on ? theme.linePill(6, line) : theme.roundRect(6, Palette.SURFACE, Palette.BORDER, 1);

            TextButton chip = new TextButton(line.name, style);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    filter = filter == line ? null : line;
                    rebuild(false);
                }
            });
            table.add(chip).size(43, 26).pad(3);
            if (++column % FILTERS_PER_ROW == 0) table.row();
        }
        return table;
    }

    private Table createDepartures(List<StopCall> today, List<StopCall> nextDay, int now, LocalDate date) {
        Table table = new Table();
        table.top();
        addRows(table, today, now, 0, date);
        if (!nextDay.isEmpty()) {
            LocalDate next = date.plusDays(1);
            String day = Texts.day(next, clock.getRealDate());
            table.add(theme.label(today.isEmpty() ? Texts.format("noMoreDepartures", day) : day,
                theme.captionStrong, Palette.MUTED)).left().pad(14, PADDING, 4, PADDING).row();
            addRows(table, nextDay, now, AppClock.MINUTES_PER_DAY, next);
        }
        return table;
    }

    private void addRows(Table table, List<StopCall> calls, int now, int dayOffset, LocalDate date) {
        for (StopCall call : calls) {
            table.add(createDeparture(call, now, dayOffset, date))
                .growX().height(ROW_HEIGHT).padLeft(PADDING - 8).padRight(PADDING - 8).row();
        }
    }

    private Button createDeparture(final StopCall call, int now, int dayOffset, final LocalDate date) {
        int minutes = call.getTime() + dayOffset - now;
        Color timeColor = minutes == 0 ? Palette.NOW : minutes <= 5 ? Palette.SOON : Palette.INK;
        String clockTime = CityTime.format(call.getTime());

        Label direction = theme.label(call.trip.pattern.getDestination(), theme.body, Palette.TEXT);
        direction.setEllipsis(true);

        Table time = new Table();
        time.right();
        if (minutes > 60) {
            time.add(theme.label(clockTime, theme.bodyStrong, timeColor)).right();
        } else {
            time.add(theme.label(minutes == 0 ? Texts.get("now") : Texts.format("minutes", minutes), theme.bodyStrong, timeColor)).right().row();
            time.add(theme.label(clockTime, theme.caption, Palette.FAINT)).right();
        }

        Button row = new RowButton(theme);
        row.add(linePill(theme, call.trip.pattern.line)).minWidth(46).height(28).padLeft(8).padRight(12);
        row.add(direction).growX().minWidth(0).prefWidth(0).left();
        row.add(time).padLeft(8).padRight(8);
        row.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onDepartureChosen(call.trip, date);
            }
        });
        return row;
    }

    static Table linePill(Theme theme, BusLine line) {
        Table pill = new Table();
        pill.setBackground(theme.linePill(7, line));
        pill.add(theme.label(line.name, theme.bodyStrong, Palette.onLine(line))).padLeft(6).padRight(6);
        return pill;
    }

    private Table createEmptyState() {
        Label message = theme.label(Texts.get("noDepartures"), theme.body, Palette.MUTED);
        message.setAlignment(Align.center);

        Table empty = new Table();
        empty.add(new Image(theme.icon(Icon.CLOCK, 32, Palette.FAINT))).size(32).padBottom(10).row();
        empty.add(message);
        return empty;
    }
}
