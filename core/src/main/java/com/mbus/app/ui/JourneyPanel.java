package com.mbus.app.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Trip;
import com.mbus.app.systems.journey.Journey;
import com.mbus.app.systems.journey.JourneyState;
import com.mbus.app.systems.journey.Place;
import com.mbus.app.systems.journey.Places;
import com.mbus.app.systems.streets.Addresses;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.CityTime;
import com.mbus.app.utils.Settings;
import com.mbus.app.utils.StopSearch;
import com.mbus.app.utils.Texts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Plans a journey: start, destination and time, the journeys found, and the steps of one of them. */
public class JourneyPanel extends FloatingPanel {

    public interface Listener {
        void onJourneyPanelClosed();

        /** Something about the question has changed. */
        void onJourneyChanged();

        /** Null to go back to the list. */
        void onJourneySelected(Journey journey);

        void onRideChosen(Trip trip, LocalDate date);

        void onJourneyTimeClicked();
    }

    private static final int MAX_SUGGESTIONS = 8;
    /** The rest of the suggestions are addresses. */
    private static final int MAX_STOPS = 4;
    private static final float TIME_WIDTH = 44f;

    private final Places places;
    private final JourneyState state;
    private final AppClock clock;
    private final Settings settings;
    private final Listener listener;

    private final Table form = new Table();
    private final Table body = new Table();
    private final TextField fromField, toField;
    private final List<RowButton> resultRows = new ArrayList<>();
    private Cell<Table> timeCell;
    private ScrollPane bodyScroll;

    private List<Place> suggestions = new ArrayList<>();
    private boolean editing = false;
    private boolean editingStart;
    private boolean typed = false;
    private boolean updatingTexts = false;

    private int searchedMinute = -1;
    private LocalDate searchedDate;

    public JourneyPanel(Theme theme, Places places, JourneyState state, AppClock clock, Settings settings,
                        Listener listener) {
        super(theme);
        this.places = places;
        this.state = state;
        this.clock = clock;
        this.settings = settings;
        this.listener = listener;
        fromField = createField(true);
        toField = createField(false);
    }

    /** Starts with the keyboard in the first empty field, if there is one. */
    public void show() {
        rebuild(false);
        open();
        if (state.isComplete()) {
            if (state.results.isEmpty()) changed();
        } else if (card.getStage() != null) {
            card.getStage().setKeyboardFocus(state.from == null ? fromField : toField);
        }
    }

    /** True for the start, false for the destination, null if a click on the map is not for either. */
    public Boolean getWantedField() {
        if (!isOpen()) return null;
        if (editing) return editingStart;
        if (state.from == null) return true;
        if (state.to == null) return false;
        return null;
    }

    public void setPlace(boolean start, Place place) {
        state.set(start, place);
        stopTyping();
        refreshTexts();
        changed();
    }

    /** Shows what has changed in the state. */
    public void refresh() {
        if (!isOpen()) return;
        refreshTexts();
        float scrollY = bodyScroll.getScrollY();
        fillBody();
        if (state.selected == null) {
            card.validate();
            bodyScroll.setScrollY(Math.min(scrollY, Math.max(0, bodyScroll.getMaxY())));
            bodyScroll.updateVisualScroll();
        }
    }

    /** The journey under the pointer, or null. */
    public Journey getHoveredJourney() {
        if (!isOpen() || state.selected != null) return null;
        for (int i = 0; i < resultRows.size() && i < state.results.size(); i++) {
            if (resultRows.get(i).isOver()) return state.results.get(i);
        }
        return null;
    }

    public void update() {
        if (!isOpen() || !state.isComplete() || state.selected != null || editing) return;
        if (clock.getMinute() != searchedMinute || !clock.getDate().equals(searchedDate)) changed();
    }

    private void changed() {
        searchedMinute = clock.getMinute();
        searchedDate = clock.getDate();
        rebuildForm();
        listener.onJourneyChanged();
        fillBody();
    }

    private void stopTyping() {
        if (card.getStage() != null) card.getStage().setKeyboardFocus(null);
        editing = false;
    }

    // Form

    @Override
    protected void build() {
        Table header = new Table();
        header.add(new Image(theme.icon(Icon.ROUTE, 20, Palette.BRAND))).size(20).padRight(10);
        header.add(theme.label(Texts.get("journey"), theme.title, Palette.INK)).left().expandX();
        header.add(new IconButton(theme, Icon.CLOSE, 16, listener::onJourneyPanelClosed)).size(32);
        card.add(header).growX().pad(PADDING, PADDING, 10, PADDING - 6).row();

        rebuildForm();
        card.add(form).growX().pad(0, PADDING, 12, PADDING).row();
        card.add(new Image(theme.fill(Palette.BORDER, 1, 1))).growX().height(1).row();

        bodyScroll = new ScrollPane(body, theme.scrollPane());
        bodyScroll.setScrollingDisabled(true, false);
        bodyScroll.setOverscroll(false, false);
        bodyScroll.setScrollbarsOnTop(true);
        ScrollOnHover.attach(bodyScroll);
        card.add(bodyScroll).growX().expandY().top().padBottom(8);
        refreshTexts();
        fillBody();
    }

    private void rebuildForm() {
        if (form.getChildren().size > 0) {
            // The fields must stay where they are, as removing one takes the keyboard away from it.
            timeCell.setActor(createTimeRow());
            return;
        }
        Table fields = new Table();
        fields.add(createFieldRow(fromField, Icon.ORIGIN)).growX().height(38).padBottom(6).row();
        fields.add(createFieldRow(toField, Icon.PIN)).growX().height(38);

        IconButton swap = new IconButton(theme, Icon.SWAP, 18, () -> {
            stopTyping();
            state.swap();
            refreshTexts();
            changed();
        });
        Tips.attach(theme, swap, Texts.get("swap"));

        form.add(fields).growX();
        form.add(swap).size(34).padLeft(6).row();
        timeCell = form.add(createTimeRow()).growX().colspan(2).padTop(10);
    }

    private Stack createFieldRow(TextField field, Icon icon) {
        Table icons = new Table();
        icons.setTouchable(Touchable.disabled);
        icons.left();
        icons.add(new Image(theme.icon(icon, 18, icon == Icon.PIN ? Palette.BRAND : Palette.MUTED))).size(18).padLeft(11);
        return new Stack(field, icons);
    }

    private TextField createField(final boolean start) {
        final TextField field = new TextField("", theme.textField(37, 10));
        field.setMessageText(Texts.get(start ? "journeyFrom" : "journeyTo"));
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (focused) {
                    editing = true;
                    editingStart = start;
                    typed = false;
                    field.selectAll();
                } else {
                    if (editing && editingStart == start) editing = false;
                    refreshTexts();
                }
                fillBody();
            }
        });
        field.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (updatingTexts) return;
                typed = true;
                fillBody();
            }
        });
        field.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    stopTyping();
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (!suggestions.isEmpty()) choose(start, suggestions.get(0));
                    return true;
                }
                return false;
            }
        });
        return field;
    }

    private void choose(boolean start, Place place) {
        setPlace(start, place);
        boolean otherIsEmpty = state.get(!start) == null;
        if (otherIsEmpty && card.getStage() != null) {
            card.getStage().setKeyboardFocus(start ? toField : fromField);
        }
    }

    private void refreshTexts() {
        updatingTexts = true;
        if (!(editing && editingStart)) fromField.setText(describe(state.from));
        if (!(editing && !editingStart)) toField.setText(describe(state.to));
        updatingTexts = false;
    }

    private static String describe(Place place) {
        if (place == null) return "";
        return place.isPoint() ? Texts.format("near", place.name) : place.name;
    }

    private Table createTimeRow() {
        Table row = new Table();
        row.left();
        row.add(createTimeChoice("leaveAt", false)).height(28).padRight(4);
        row.add(createTimeChoice("arriveBy", true)).height(28);
        String time = clock.isLive() && !state.arriveBy ? Texts.get("now")
            : Texts.format("dayAt", Texts.day(clock.getDate(), clock.getRealDate()), CityTime.format(clock.getMinute()));
        LinkButton timeButton = new LinkButton(theme, time, listener::onJourneyTimeClicked);
        Tips.attach(theme, timeButton, Texts.get("chooseTime"));
        row.add(timeButton).right().expandX().height(28);
        return row;
    }

    private TextButton createTimeChoice(String key, final boolean arriveBy) {
        boolean on = state.arriveBy == arriveBy;
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = theme.captionStrong;
        style.fontColor = on ? Palette.BRAND : Palette.MUTED;
        style.up = on ? theme.roundRect(7, Palette.BRAND_SOFT) : null;
        style.over = on ? style.up : theme.roundRect(7, Palette.SUBTLE);
        TextButton button = new TextButton(Texts.get(key), style);
        button.padLeft(10).padRight(10);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (state.arriveBy == arriveBy) return;
                state.arriveBy = arriveBy;
                state.selected = null;
                JourneyPanel.this.changed();
            }
        });
        return button;
    }

    // Body

    private void fillBody() {
        if (bodyScroll == null) return;
        body.clear();
        body.top();
        resultRows.clear();

        if (editing) addSuggestions();
        else if (!state.isComplete()) addMessage(Icon.ROUTE, Texts.get("journeyHint"));
        else if (state.selected != null) addSteps(state.selected);
        else if (!state.results.isEmpty()) addResults();
        else addMessage(Icon.CLOCK, Texts.get(state.searching ? "searching" : "noJourney"));

        body.invalidateHierarchy();
        applyBounds();
        bodyScroll.setScrollY(0);
        bodyScroll.updateVisualScroll();
    }

    private void addMessage(Icon icon, String text) {
        Label label = theme.label(text, theme.body, Palette.MUTED);
        label.setAlignment(Align.center);
        label.setWrap(true);
        body.add(new Image(theme.icon(icon, 30, Palette.FAINT))).size(30).padTop(26).padBottom(10).row();
        body.add(label).growX().pad(0, PADDING + 8, 28, PADDING + 8);
    }

    private void addSuggestions() {
        String query = typed ? (editingStart ? fromField : toField).getText() : "";
        boolean empty = query.trim().isEmpty();
        List<Place> stops = new ArrayList<>();
        // Favorites first, and without a query only those
        for (boolean favorites : new boolean[]{true, false}) {
            for (Place place : places.getAll()) {
                if (stops.size() == (empty ? MAX_SUGGESTIONS : MAX_STOPS)) break;
                if (isFavorite(place) != favorites || place == state.get(!editingStart)) continue;
                if (empty ? favorites : matches(place, query)) stops.add(place);
            }
        }
        List<Addresses.Match> addresses = empty
            ? new ArrayList<>() : places.searchAddresses(query, MAX_SUGGESTIONS - stops.size());

        suggestions = new ArrayList<>();
        body.add().height(6).row();
        // Who types a house number wants the address, whatever is called similarly.
        boolean addressesFirst = Addresses.hasNumber(query);
        if (addressesFirst) addAddresses(addresses);
        for (Place place : stops) {
            boolean favorite = isFavorite(place);
            addSuggestion(place, favorite ? Icon.STAR_FILLED : Icon.BUS, favorite ? Palette.STAR : Palette.FAINT, "");
        }
        if (!addressesFirst) addAddresses(addresses);
        Label hint = theme.label(
            suggestions.isEmpty() && !empty ? Texts.format("noResults", query.trim()) : Texts.get("orClickMap"),
            theme.caption, Palette.MUTED);
        hint.setWrap(true);
        body.add(hint).growX().pad(8, PADDING, 10, PADDING);
    }

    private void addAddresses(List<Addresses.Match> addresses) {
        for (Addresses.Match match : addresses) {
            addSuggestion(places.of(match.address), Icon.PIN, Palette.FAINT, describe(match));
        }
    }

    private void addSuggestion(Place place, Icon icon, Color iconColor, String note) {
        suggestions.add(place);
        body.add(createSuggestion(place, icon, iconColor, note))
            .growX().height(note.isEmpty() ? 40 : 46).padLeft(PADDING - 8).padRight(PADDING - 8).row();
    }

    private boolean isFavorite(Place place) {
        for (BusStop stop : place.stops) {
            if (settings.isFavorite(stop.id)) return true;
        }
        return false;
    }

    private static boolean matches(Place place, String query) {
        for (BusStop stop : place.stops) {
            if (StopSearch.matches(stop, query)) return true;
        }
        return false;
    }

    /** Where an address is, and what is used if it is not exactly the one asked for. */
    private String describe(Addresses.Match match) {
        Addresses.Address address = match.address;
        String where = address.city.isEmpty()
            ? Texts.format("near", places.getNearestStop(address.geo).name) : address.city;
        String instead;
        if (address.number.isEmpty()) instead = Texts.get(match.insteadOf.isEmpty() ? "streetMiddle" : "numberUnknown");
        else if (!match.insteadOf.isEmpty()) instead = Texts.format("closestNumber", match.insteadOf);
        else return where;
        return instead + "  ·  " + where; // in this order, as the end may not fit
    }

    private Button createSuggestion(final Place place, Icon icon, Color iconColor, String note) {
        Button row = new RowButton(theme);
        row.add(new Image(theme.icon(icon, 16, iconColor))).size(16).padLeft(8).padRight(10);
        Label name = theme.label(place.name, theme.body, Palette.INK);
        name.setEllipsis(true);
        Table text = new Table();
        text.left();
        text.add(name).growX().minWidth(0).prefWidth(0).left().row();
        if (!note.isEmpty()) {
            Label noteLabel = theme.label(note, theme.caption, Palette.MUTED);
            noteLabel.setEllipsis(true);
            text.add(noteLabel).growX().minWidth(0).prefWidth(0).left();
        }
        row.add(text).growX().padRight(8);
        row.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // Already here, as the field loses the keyboard now and the suggestions disappear with it.
                event.stop();
                choose(editingStart, place);
                return true;
            }
        });
        return row;
    }

    private void addResults() {
        LocalDate day = clock.getDate();
        body.add().height(4).row();
        for (final Journey journey : state.results) {
            if (!journey.date.equals(day)) {
                day = journey.date;
                body.add(theme.label(Texts.day(day, clock.getRealDate()), theme.captionStrong, Palette.MUTED))
                    .left().pad(10, PADDING, 4, PADDING).row();
            }
            RowButton row = createResult(journey);
            resultRows.add(row);
            body.add(row).growX().padLeft(PADDING - 8).padRight(PADDING - 8).row();
        }
    }

    private RowButton createResult(final Journey journey) {
        Table top = new Table();
        top.add(theme.label(times(journey), theme.bodyStrong, Palette.INK)).left().expandX();
        top.add(theme.label(Texts.format("minutes", journey.getMinutes()), theme.bodyStrong, Palette.INK));

        Table legs = new Table();
        legs.left();
        boolean first = true;
        for (Journey.Leg leg : journey.getLegs()) {
            if (!leg.isRide() && journey.getRides() > 0 && leg.getMinutes() < 2) continue;
            if (!first) legs.add(new Image(theme.icon(Icon.CHEVRON_RIGHT, 12, Palette.FAINT))).size(12).padLeft(2).padRight(2);
            first = false;
            if (leg.isRide()) {
                legs.add(smallPill(leg.trip)).minWidth(34).height(22);
            } else {
                legs.add(new Image(theme.icon(Icon.WALK, 15, Palette.MUTED))).size(15);
                legs.add(theme.label(String.valueOf(leg.getMinutes()), theme.caption, Palette.MUTED)).padLeft(1);
            }
        }

        RowButton row = new RowButton(theme);
        row.add(top).growX().pad(10, 8, 6, 8).row();
        row.add(legs).growX().pad(0, 8, 4, 8).row();
        row.add(theme.label(summary(journey), theme.caption, Palette.MUTED)).left().pad(0, 8, 10, 8);
        row.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onJourneySelected(journey);
            }
        });
        return row;
    }

    private Table smallPill(Trip trip) {
        Table pill = new Table();
        pill.setBackground(theme.linePill(6, trip.pattern.line));
        pill.add(theme.label(trip.pattern.line.name, theme.captionStrong, Palette.onLine(trip.pattern.line))).padLeft(5).padRight(5);
        return pill;
    }

    private static String times(Journey journey) {
        return CityTime.format(journey.getDeparture()) + " – " + CityTime.format(journey.getArrival());
    }

    private static String summary(Journey journey) {
        if (journey.getRides() == 0) return Texts.get("walkAllTheWay");
        int changes = journey.getChanges();
        String text = changes == 0 ? Texts.get("noChange")
            : Texts.format("changes", changes, Texts.plural("change", changes));
        int walk = journey.getWalkMinutes();
        return walk == 0 ? text : text + "  ·  " + Texts.format("walkMinutes", walk);
    }

    // Steps of a journey

    private void addSteps(final Journey journey) {
        Table header = new Table();
        IconButton back = new IconButton(theme, Icon.CHEVRON_LEFT, 18, () -> listener.onJourneySelected(null));
        Tips.attach(theme, back, Texts.get("allJourneys"));
        Table text = new Table();
        text.left();
        text.add(theme.label(times(journey), theme.bodyStrong, Palette.INK)).left().row();
        String day = journey.date.equals(clock.getRealDate()) ? "" : Texts.day(journey.date, clock.getRealDate()) + "  ·  ";
        text.add(theme.label(day + Texts.format("minutes", journey.getMinutes()) + "  ·  " + summary(journey),
            theme.caption, Palette.MUTED)).left();
        header.add(back).size(32).padRight(6);
        header.add(text).growX();
        body.add(header).growX().pad(8, PADDING - 8, 8, PADDING).row();

        Color walkColor = Palette.QUIET;
        Color before = null;
        List<Journey.Leg> legs = journey.getLegs();
        for (int i = 0; i < legs.size(); i++) {
            final Journey.Leg leg = legs.get(i);
            Color color = leg.isRide() ? Palette.line(leg.trip.pattern.line) : walkColor;
            boolean walked = i > 0 && !legs.get(i - 1).isRide();

            // Where this leg starts; after a ride also when that one ends.
            if (i > 0 && leg.isRide() && !walked) {
                addPoint(legs.get(i - 1).end, legs.get(i - 1).toStop.name, before, null);
                addPoint(leg.start, leg.fromStop.name, null, color);
            } else {
                int time = i > 0 && !leg.isRide() ? legs.get(i - 1).end : leg.start;
                String name = leg.fromStop != null ? leg.fromStop.name : describe(journey.from);
                addPoint(time, name, before, color);
            }

            if (leg.isRide()) addRide(journey, leg, color);
            else addWalk(leg, color);
            before = color;
        }
        Journey.Leg last = legs.get(legs.size() - 1);
        addPoint(last.end, last.toStop != null ? last.toStop.name : describe(journey.to), before, null);
        body.add().height(10);
    }

    private void addPoint(int time, String name, Color above, Color below) {
        Label label = theme.label(name, theme.bodyStrong, Palette.INK);
        label.setEllipsis(true);
        Table row = new Table();
        row.add(theme.label(CityTime.format(time), theme.bodyStrong, Palette.INK)).width(TIME_WIDTH).left();
        row.add(new Track(above, below, true)).width(22).growY();
        row.add(label).growX().minWidth(0).prefWidth(0).left().padLeft(8);
        body.add(row).growX().height(32).padLeft(PADDING).padRight(PADDING).row();
    }

    private void addRide(final Journey journey, final Journey.Leg leg, Color color) {
        Label direction = theme.label(
            leg.trip.pattern.getDestination(), theme.body, Palette.TEXT);
        direction.setEllipsis(true);
        int count = leg.alightIndex - leg.boardIndex;
        Label info = theme.label(
            Texts.format("ride", count, Texts.plural("stop", count), leg.getMinutes()), theme.caption, Palette.MUTED);

        Table text = new Table();
        text.left();
        text.add(direction).growX().minWidth(0).prefWidth(0).left().row();
        text.add(info).left();

        RowButton row = new RowButton(theme);
        row.add(StopPanel.linePill(theme, leg.trip.pattern.line)).minWidth(44).height(26).padLeft(8).padRight(10);
        row.add(text).growX().padRight(4);
        row.add(new Image(theme.icon(Icon.CHEVRON_RIGHT, 14, Palette.FAINT))).size(14).padRight(6);
        row.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onRideChosen(leg.trip, journey.date);
            }
        });
        addBetween(row, color, 54);
    }

    private void addWalk(Journey.Leg leg, Color color) {
        Table row = new Table();
        row.left();
        row.add(new Image(theme.icon(Icon.WALK, 18, Palette.MUTED))).size(18).padLeft(8).padRight(8);
        row.add(theme.label(Texts.format("walkMinutes", leg.getMinutes()), theme.body, Palette.TEXT));
        addBetween(row, color, 40);
    }

    private void addBetween(Table content, Color color, float height) {
        Table row = new Table();
        row.add().width(TIME_WIDTH);
        row.add(new Track(color, color, false)).width(22).growY();
        row.add(content).growX().growY().pad(2, 0, 2, 0);
        body.add(row).growX().height(height).padLeft(PADDING).padRight(PADDING - 8).row();
    }

    /** The piece of the route next to a step. */
    private class Track extends Widget {

        private static final float LINE = 4f;
        private static final float DOT = 6f;

        private final Drawable above, below, dot, dotCenter;

        Track(Color above, Color below, boolean point) {
            this.above = above == null ? null : theme.fill(above, 1, 1);
            this.below = below == null ? null : theme.fill(below, 1, 1);
            this.dot = point ? theme.roundRect(DOT, below != null ? below : above != null ? above : Palette.QUIET) : null;
            this.dotCenter = theme.roundRect(DOT - 3, Palette.SURFACE);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.setColor(1, 1, 1, getColor().a * parentAlpha);
            float x = getX() + getWidth() / 2f;
            float y = getY() + getHeight() / 2f;
            if (above != null) above.draw(batch, x - LINE / 2, y, LINE, getHeight() / 2f);
            if (below != null) below.draw(batch, x - LINE / 2, getY(), LINE, getHeight() / 2f);
            if (dot != null) {
                dot.draw(batch, x - DOT, y - DOT, 2 * DOT, 2 * DOT);
                dotCenter.draw(batch, x - DOT + 3, y - DOT + 3, 2 * DOT - 6, 2 * DOT - 6);
            }
        }
    }
}
