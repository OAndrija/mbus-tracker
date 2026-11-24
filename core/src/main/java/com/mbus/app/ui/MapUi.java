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
import com.mbus.app.model.Timetable;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.Display;
import com.mbus.app.utils.Settings;
import com.mbus.app.utils.Texts;

/** Everything that floats above the map, and where it goes. */
public class MapUi implements Disposable {

    public interface MapActions {
        void zoomIn();

        void zoomOut();

        void showWholeNetwork();

        void toggleTimeBar();

        void toggleDark();

        void switchLanguage();
    }

    private static final float MARGIN = 16f;
    private static final float GAP = 12f;
    private static final float SIDEBAR_WIDTH = 344f;
    private static final float STOP_PANEL_WIDTH = 320f;
    /** Below this width the stop panel covers the sidebar instead of sitting next to it. */
    private static final float COMPACT_WIDTH = 900f;
    private static final float CONTROL_SIZE = 40f;
    private static final float MIN_TIME_BAR_WIDTH = 300f;
    private static final float MAX_TIME_BAR_WIDTH = 640f;

    private final ScreenViewport viewport = new ScreenViewport();
    private final Stage stage;
    private final Vector2 tmp = new Vector2();

    private final Sidebar sidebar;
    private final StopPanel stopPanel;
    private final TripPanel tripPanel;
    private final TimeBar timeBar;
    private final MapTooltip tooltip;
    private final Card zoomControls;
    private final Card fitControl;
    private final Card timeControl;
    private final Card darkControl;
    private final Card languageControl;
    private final IconButton timeButton;
    private final AppClock clock;
    private boolean shownLive = true;
    private final Table attribution = new Table();
    private Cursor.SystemCursor cursor = Cursor.SystemCursor.Arrow;

    public MapUi(Theme theme, Timetable timetable, AppClock clock, Settings settings, Sidebar.Listener sidebarListener,
                 StopPanel.Listener stopListener, TripPanel.Listener tripListener, final MapActions actions) {
        this.clock = clock;
        viewport.setUnitsPerPixel(1f / Display.getWindowScale());
        stage = new Stage(viewport);
        sidebar = new Sidebar(theme, timetable, clock, settings, sidebarListener);
        stopPanel = new StopPanel(theme, timetable, clock, settings, stopListener);
        tripPanel = new TripPanel(theme, clock, tripListener);
        timeBar = new TimeBar(theme, timetable, clock, actions::toggleTimeBar);
        tooltip = new MapTooltip(theme);

        IconButton zoomIn = new IconButton(theme, Icon.PLUS, 18, actions::zoomIn);
        IconButton zoomOut = new IconButton(theme, Icon.MINUS, 18, actions::zoomOut);
        IconButton fit = new IconButton(theme, Icon.FIT, 18, actions::showWholeNetwork);
        timeButton = new IconButton(theme, Icon.CLOCK, 18, actions::toggleTimeBar);
        Tips.attach(theme, zoomIn, Texts.get("zoomIn"));
        Tips.attach(theme, zoomOut, Texts.get("zoomOut"));
        Tips.attach(theme, fit, Texts.get("wholeNetwork"));
        Tips.attach(theme, timeButton, Texts.get("chooseTime"));

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

        IconButton darkButton = new IconButton(theme, Palette.isDark() ? Icon.SUN : Icon.MOON, 18, actions::toggleDark);
        Tips.attach(theme, darkButton, Texts.get(Palette.isDark() ? "lightMode" : "darkMode"));
        darkControl = new Card(theme);
        darkControl.setBackground(theme.card(12));
        darkControl.add(darkButton).size(CONTROL_SIZE);

        LinkButton languageButton = new LinkButton(theme, Texts.get("languageCode"), actions::switchLanguage);
        languageButton.getStyle().fontColor = Palette.MUTED;
        languageButton.getStyle().overFontColor = Palette.INK;
        languageButton.getStyle().over = theme.roundRect(8, Palette.SUBTLE);
        Tips.attach(theme, languageButton, Texts.get("language"));
        languageControl = new Card(theme);
        languageControl.setBackground(theme.card(12));
        languageControl.add(languageButton).size(CONTROL_SIZE);

        attribution.setTouchable(Touchable.disabled);
        attribution.setBackground(theme.roundRect(4, withAlpha(Palette.SURFACE, 0.8f)));
        attribution.add(theme.label(Texts.get("attribution"), theme.caption, Palette.MUTED)).pad(2, 8, 2, 8);

        stage.addActor(attribution);
        stage.addActor(zoomControls);
        stage.addActor(fitControl);
        stage.addActor(timeControl);
        stage.addActor(darkControl);
        stage.addActor(languageControl);
        stage.addActor(timeBar.getActor());
        stage.addActor(sidebar.getActor());
        stage.addActor(stopPanel.getActor());
        stage.addActor(tripPanel.getActor());
        stage.addActor(tooltip.getActor());
        layout();
    }

    private static Color withAlpha(Color color, float alpha) {
        return new Color(color.r, color.g, color.b, alpha);
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
        viewport.setUnitsPerPixel(1f / Display.getWindowScale());
        viewport.update(width, height, true);
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
        darkControl.pack();
        darkControl.setPosition(zoomControls.getX(), timeControl.getTop() + GAP);
        languageControl.pack();
        languageControl.setPosition(zoomControls.getX(), darkControl.getTop() + GAP);
        layoutTimeBar();
    }

    /** In the middle of what is left between the panels and the buttons. */
    private void layoutTimeBar() {
        boolean compact = stage.getWidth() < COMPACT_WIDTH;
        float left = (compact ? MARGIN + SIDEBAR_WIDTH : getCoveredStageWidth()) + GAP;
        float right = zoomControls.getX() - GAP;
        if (right - left < MIN_TIME_BAR_WIDTH) left = Math.max(MARGIN, right - MIN_TIME_BAR_WIDTH);
        float barWidth = Math.min(MAX_TIME_BAR_WIDTH, right - left);
        timeBar.layout(Math.round((left + right - barWidth) / 2f),
            Math.round(attribution.getHeight() + GAP + TimeBar.HEIGHT), barWidth, TimeBar.HEIGHT);
    }

    /** How much of the left side of the window is covered by panels, in window pixels. */
    public float getCoveredWidth() {
        return getCoveredStageWidth() * Display.getWindowScale();
    }

    private float getCoveredStageWidth() {
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
    public void updatePointer(Trip hoveredTrip, String hoveredStop, BusLine hoveredLine, boolean overCluster) {
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
        if (clock.isLive() != shownLive) {
            shownLive = clock.isLive();
            timeButton.setIcon(Icon.CLOCK, shownLive ? Palette.MUTED : Palette.BRAND,
                shownLive ? Palette.INK : Palette.BRAND_STRONG);
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
