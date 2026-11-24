package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.CityTime;

import java.time.LocalDate;
import java.util.List;

/** One trip of a bus: all its stops, and how far it has come. */
public class TripPanel extends FloatingPanel {

    public interface Listener {
        void onTripPanelClosed();

        void onTripStopChosen(BusStop stop);

        void onFollowChanged(boolean follow);
    }

    private static final float ROW_HEIGHT = 40f;

    private final AppClock clock;
    private final Listener listener;

    private Trip trip;
    private LocalDate date;
    private boolean openedFromStop;
    private boolean following;
    private int shownMinute;
    private LocalDate shownDate;

    public TripPanel(Theme theme, AppClock clock, Listener listener) {
        super(theme);
        this.clock = clock;
        this.listener = listener;
    }

    /** The date is the day the trip runs on. */
    public void show(Trip trip, LocalDate date, boolean openedFromStop, boolean following) {
        this.trip = trip;
        this.date = date;
        this.openedFromStop = openedFromStop;
        this.following = following;
        rebuild(false);
        open();

        int next = getNextStop();
        if (isRunning()) scrollTo((next - 2) * ROW_HEIGHT);
    }

    public void setFollowing(boolean following) {
        if (this.following == following) return;
        this.following = following;
        if (isOpen()) rebuild(true);
    }

    public void update() {
        if (isOpen() && (clock.getMinute() != shownMinute || !clock.getDate().equals(shownDate))) {
            rebuild(true);
        }
    }

    private boolean isRunning() {
        return getState() == 0;
    }

    /** -1 before the trip, 0 during it, 1 after it. */
    private int getState() {
        int byDate = clock.getDate().compareTo(date);
        if (byDate != 0) return byDate < 0 ? -1 : 1;
        if (clock.getMinute() < trip.getStart()) return -1;
        return clock.getMinute() > trip.getEnd() ? 1 : 0;
    }

    /** Index of the first stop the bus has not left yet. */
    private int getNextStop() {
        int state = getState();
        if (state != 0) return state < 0 ? 0 : trip.getStopCount();
        for (int i = 0; i < trip.getStopCount(); i++) {
            if (trip.getDeparture(i) >= clock.getMinute()) return i;
        }
        return trip.getStopCount() - 1;
    }

    @Override
    protected void build() {
        shownMinute = clock.getMinute();
        shownDate = clock.getDate();

        card.add(createHeader()).growX().pad(PADDING, PADDING - 4, 12, PADDING - 6).row();
        card.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();

        Table stops = new Table();
        stops.top();
        int next = getNextStop();
        for (int i = 0; i < trip.getStopCount(); i++) {
            stops.add(createStop(i, next)).growX().height(ROW_HEIGHT).padLeft(PADDING - 8).padRight(PADDING - 8).row();
        }
        card.add().height(8).row();
        addScrolling(stops);
    }

    private Table createHeader() {
        Label destination = theme.label(trip.pattern.getDestination(), theme.title, Palette.INK);
        destination.setWrap(true);

        Table text = new Table();
        text.left();
        text.add(destination).growX().left().row();
        text.add(theme.label(describeState(), theme.caption, isRunning() ? Palette.BRAND : Palette.MUTED)).left().padTop(2);

        Table header = new Table();
        if (openedFromStop) {
            IconButton back = new IconButton(theme, Icon.CHEVRON_LEFT, 18, listener::onTripPanelClosed);
            Tips.attach(theme, back, "Nazaj na postajo");
            header.add(back).size(32).padRight(2).top();
        }
        header.add(StopPanel.linePill(theme, trip.pattern.line)).minWidth(46).height(28).padRight(10).padTop(2).padLeft(4).top();
        header.add(text).growX().top();
        if (isRunning()) {
            IconButton follow = new IconButton(theme, Icon.FOLLOW, 18, () -> listener.onFollowChanged(!following));
            if (following) follow.setIcon(Icon.FOLLOW, Palette.BRAND, Palette.BRAND_STRONG);
            Tips.attach(theme, follow, following ? "Ne sledi avtobusu" : "Sledi avtobusu");
            header.add(follow).size(32).top();
        }
        if (!openedFromStop) {
            header.add(new IconButton(theme, Icon.CLOSE, 16, listener::onTripPanelClosed)).size(32).top();
        }
        return header;
    }

    private String describeState() {
        int state = getState();
        if (state < 0) {
            String day = date.equals(clock.getDate()) ? "" : CityTime.describe(date, clock.getRealDate()) + " ";
            return day + (day.isEmpty() ? "Odhod ob " : "ob ") + CityTime.format(trip.getStart());
        }
        if (state > 0) return "Vožnja je končana";
        return "Na poti  ·  do " + CityTime.format(trip.getEnd());
    }

    private Button createStop(final int index, int next) {
        final BusStop stop = trip.pattern.getStops().get(index);
        boolean passed = index < next;
        boolean isNext = index == next && isRunning();

        Label name = theme.label(stop.name, isNext ? theme.bodyStrong : theme.body,
            passed ? Palette.FAINT : Palette.INK);
        name.setEllipsis(true);
        Label time = theme.label(CityTime.format(trip.getDeparture(index)), isNext ? theme.bodyStrong : theme.body,
            passed ? Palette.FAINT : isNext ? Palette.BRAND : Palette.TEXT);

        Button row = new RowButton(theme);
        row.add(new Mark(index, next)).width(24).growY().padLeft(8);
        row.add(name).growX().minWidth(0).prefWidth(0).left().padLeft(8);
        row.add(time).padLeft(8).padRight(8);
        row.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onTripStopChosen(stop);
            }
        });
        return row;
    }

    /** The piece of the line next to a stop, with a dot for the stop. */
    private class Mark extends Widget {

        private static final float LINE = 4f;

        private final boolean first, last, passed, isNext;
        private final Drawable line, pastLine, dot, dotCenter;

        Mark(int index, int next) {
            BusLine busLine = trip.pattern.line;
            first = index == 0;
            last = index == trip.getStopCount() - 1;
            passed = index < next;
            isNext = index == next && isRunning();
            Color past = new Color(busLine.color).lerp(Palette.SURFACE, 0.65f);
            line = theme.fill(busLine.color, 1, 1);
            pastLine = theme.fill(past, 1, 1);
            dot = theme.roundRect(isNext ? 8 : 6, passed ? past : busLine.color);
            dotCenter = theme.roundRect(isNext ? 4.5f : 3, Palette.SURFACE);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.setColor(1, 1, 1, getColor().a * parentAlpha);
            float centerX = getX() + getWidth() / 2f;
            float centerY = getY() + getHeight() / 2f;
            float half = getHeight() / 2f;

            if (!first) {
                (passed || isNext ? pastLine : line).draw(batch, centerX - LINE / 2, centerY, LINE, half);
            }
            if (!last) {
                (passed ? pastLine : line).draw(batch, centerX - LINE / 2, centerY - half, LINE, half);
            }
            float radius = isNext ? 8f : 6f;
            float inner = isNext ? 4.5f : 3f;
            dot.draw(batch, centerX - radius, centerY - radius, 2 * radius, 2 * radius);
            dotCenter.draw(batch, centerX - inner, centerY - inner, 2 * inner, 2 * inner);
        }
    }
}
