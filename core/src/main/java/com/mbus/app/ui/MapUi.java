package com.mbus.app.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.Settings;

/** Everything that floats above the map, and where it goes. */
public class MapUi implements Disposable {

    public interface MapActions {
        void zoomIn();

        void zoomOut();

        void showWholeNetwork();

        void toggleTimeBar();
    }

    private static final String ATTRIBUTION =
        "Vozni red: Marprom  ·  Powered by Geoapify  ·  © OpenMapTiles  ·  © OpenStreetMap contributors";

    private static final float MARGIN = 16f;
    private static final float GAP = 12f;
    private static final float SIDEBAR_WIDTH = 344f;
    private static final float STOP_PANEL_WIDTH = 320f;
    /** Below this width the stop panel covers the sidebar instead of sitting next to it. */
    private static final float COMPACT_WIDTH = 900f;
    private static final float CONTROL_SIZE = 40f;
    private static final float MIN_TIME_BAR_WIDTH = 300f;
    private static final float MAX_TIME_BAR_WIDTH = 640f;

    private final Stage stage = new Stage(new ScreenViewport());
    private final Vector2 tmp = new Vector2();

    private final Sidebar sidebar;
    private final StopPanel stopPanel;
    private final TripPanel tripPanel;
    private final TimeBar timeBar;
    private final MapTooltip tooltip;
    private final Card zoomControls;
    private final Card fitControl;
    private final Card timeControl;
    private final IconButton timeButton;
    private final AppClock clock;
    private Color shownTimeColor = Palette.MUTED;
    private final Table attribution = new Table();
    private Cursor.SystemCursor cursor = Cursor.SystemCursor.Arrow;

    public MapUi(Theme theme, Timetable timetable, AppClock clock, Settings settings, Sidebar.Listener sidebarListener,
                 StopPanel.Listener stopListener, TripPanel.Listener tripListener, final MapActions actions) {
        this.clock = clock;
        sidebar = new Sidebar(theme, timetable, clock, settings, sidebarListener);
        stopPanel = new StopPanel(theme, timetable, clock, settings, stopListener);
        tripPanel = new TripPanel(theme, clock, tripListener);
        timeBar = new TimeBar(theme, timetable, clock, actions::toggleTimeBar);
        tooltip = new MapTooltip(theme);

        IconButton zoomIn = new IconButton(theme, Icon.PLUS, 18, actions::zoomIn);
        IconButton zoomOut = new IconButton(theme, Icon.MINUS, 18, actions::zoomOut);
        IconButton fit = new IconButton(theme, Icon.FIT, 18, actions::showWholeNetwork);
        timeButton = new IconButton(theme, Icon.CLOCK, 18, actions::toggleTimeBar);
        Tips.attach(theme, zoomIn, "Približaj");
        Tips.attach(theme, zoomOut, "Oddalji");
        Tips.attach(theme, fit, "Celotno omrežje");
        Tips.attach(theme, timeButton, "Izberi čas (T)");

        zoomControls = new Card(theme);
        zoomControls.setBackground(theme.card(12));
        zoomControls.add(zoomIn).size(CONTROL_SIZE).row();
        zoomControls.add(new Image(theme.fill(Palette.BORDER, 1, 1))).width(24).height(1).row();
        zoomControls.add(zoomOut).size(CONTROL_SIZE);

        fitControl = new Card(theme);
        fitControl.setBackground(theme.card(12));
        fitControl.add(fit).size(CONTROL_SIZE);

        timeControl = new Card(theme);
        timeControl.setBackground(theme.card(12));
        timeControl.add(timeButton).size(CONTROL_SIZE);

        attribution.setTouchable(Touchable.disabled);
        attribution.setBackground(theme.roundRect(4, new Color(1f, 1f, 1f, 0.8f)));
        attribution.add(theme.label(ATTRIBUTION, theme.caption, Palette.MUTED)).pad(2, 8, 2, 8);

        stage.addActor(attribution);
        stage.addActor(zoomControls);
        stage.addActor(fitControl);
        stage.addActor(timeControl);
        stage.addActor(timeBar.getActor());
        stage.addActor(sidebar.getActor());
        stage.addActor(stopPanel.getActor());
        stage.addActor(tripPanel.getActor());
        stage.addActor(tooltip.getActor());
        layout();
    }

    public Stage getStage() {
        return stage;
    }

    public Sidebar getSidebar() {
        return sidebar;
    }

    public StopPanel getStopPanel() {
        return stopPanel;
    }

    public TripPanel getTripPanel() {
        return tripPanel;
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        layout();
    }

    public void layout() {
        float width = stage.getWidth();
        float height = stage.getHeight();
        boolean compact = width < COMPACT_WIDTH;
        float panelHeight = height - 2 * MARGIN;
        float sidebarWidth = Math.min(SIDEBAR_WIDTH, width - 2 * MARGIN);

        sidebar.layout(MARGIN, height - MARGIN, sidebarWidth, panelHeight);
        float panelX = compact ? MARGIN : MARGIN + sidebarWidth + GAP;
        float panelWidth = compact ? sidebarWidth : STOP_PANEL_WIDTH;
        stopPanel.layout(panelX, height - MARGIN, panelWidth, panelHeight);
        tripPanel.layout(panelX, height - MARGIN, panelWidth, panelHeight);

        attribution.pack();
        attribution.setPosition(Math.round(width - attribution.getWidth()), 0);

        zoomControls.pack();
        zoomControls.setPosition(
            Math.round(width - MARGIN - zoomControls.getWidth()),
            Math.round(attribution.getHeight() + GAP));
        fitControl.pack();
        fitControl.setPosition(zoomControls.getX(), zoomControls.getTop() + GAP);
        timeControl.pack();
        timeControl.setPosition(zoomControls.getX(), fitControl.getTop() + GAP);
        layoutTimeBar();
    }

    /** In the middle of what is left between the panels and the buttons. */
    private void layoutTimeBar() {
        boolean compact = stage.getWidth() < COMPACT_WIDTH;
        float left = (compact ? MARGIN + SIDEBAR_WIDTH : getCoveredWidth()) + GAP;
        float right = zoomControls.getX() - GAP;
        if (right - left < MIN_TIME_BAR_WIDTH) left = Math.max(MARGIN, right - MIN_TIME_BAR_WIDTH);
        float barWidth = Math.min(MAX_TIME_BAR_WIDTH, right - left);
        timeBar.layout(Math.round((left + right - barWidth) / 2f),
            Math.round(attribution.getHeight() + GAP + TimeBar.HEIGHT), barWidth, TimeBar.HEIGHT);
    }

    /** How much of the left side of the window is covered by panels. */
    public float getCoveredWidth() {
        boolean compact = stage.getWidth() < COMPACT_WIDTH;
        if (compact) return 0f;
        float covered = MARGIN + SIDEBAR_WIDTH;
        return stopPanel.isOpen() || tripPanel.isOpen() ? covered + GAP + STOP_PANEL_WIDTH : covered;
    }

    public boolean isOver(int screenX, int screenY) {
        return actorAt(screenX, screenY) != null;
    }

    private Actor actorAt(int screenX, int screenY) {
        stage.screenToStageCoordinates(tmp.set(screenX, screenY));
        return stage.hit(tmp.x, tmp.y, true);
    }

    /** Updates everything that depends on the pointer. */
    public void updatePointer(Trip hoveredTrip, BusStop hoveredStop, BusLine hoveredLine, boolean overCluster) {
        int x = Gdx.input.getX();
        int y = Gdx.input.getY();

        tooltip.show(hoveredTrip, hoveredStop, hoveredLine);
        stage.screenToStageCoordinates(tmp.set(x, y));
        tooltip.moveTo(tmp.x, tmp.y, stage.getWidth(), stage.getHeight());

        Cursor.SystemCursor wanted = Cursor.SystemCursor.Arrow;
        if (hoveredTrip != null || hoveredStop != null || hoveredLine != null || overCluster) {
            wanted = Cursor.SystemCursor.Hand;
        }
        for (Actor actor = actorAt(x, y); actor != null; actor = actor.getParent()) {
            if (actor instanceof TextField) {
                wanted = Cursor.SystemCursor.Ibeam;
                break;
            }
            if (actor instanceof Button || actor instanceof StopListView) {
                wanted = Cursor.SystemCursor.Hand;
                break;
            }
        }
        if (wanted != cursor) {
            cursor = wanted;
            Gdx.graphics.setSystemCursor(wanted);
        }
    }

    public void render(float delta) {
        sidebar.update();
        stopPanel.update();
        tripPanel.update();
        if (timeBar.isOpen()) {
            timeBar.update();
            layoutTimeBar();
        }
        Color timeColor = clock.isLive() ? Palette.MUTED : Palette.BRAND;
        if (timeColor != shownTimeColor) {
            shownTimeColor = timeColor;
            timeButton.setIcon(Icon.CLOCK, timeColor, clock.isLive() ? Palette.INK : Palette.BRAND_DARK);
        }
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
