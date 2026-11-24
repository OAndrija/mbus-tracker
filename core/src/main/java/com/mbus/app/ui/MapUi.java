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
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** Everything that floats above the map, and where it goes. */
public class MapUi implements Disposable {

    public interface MapActions {
        void zoomIn();

        void zoomOut();

        void showWholeNetwork();
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

    private final Stage stage = new Stage(new ScreenViewport());
    private final Vector2 tmp = new Vector2();

    private final Sidebar sidebar;
    private final StopPanel stopPanel;
    private final MapTooltip tooltip;
    private final Card zoomControls;
    private final Card fitControl;
    private final Table attribution = new Table();
    private Cursor.SystemCursor cursor = Cursor.SystemCursor.Arrow;

    public MapUi(Theme theme, Timetable timetable, Sidebar.Listener listener,
                 final MapActions actions, Runnable onStopPanelClosed) {
        sidebar = new Sidebar(theme, timetable, listener);
        stopPanel = new StopPanel(theme, timetable, onStopPanelClosed);
        tooltip = new MapTooltip(theme);

        zoomControls = new Card(theme);
        zoomControls.setBackground(theme.card(12));
        zoomControls.add(new IconButton(theme, Icon.PLUS, 18, actions::zoomIn)).size(CONTROL_SIZE).row();
        zoomControls.add(new Image(theme.fill(Palette.BORDER, 1, 1))).width(24).height(1).row();
        zoomControls.add(new IconButton(theme, Icon.MINUS, 18, actions::zoomOut)).size(CONTROL_SIZE);

        fitControl = new Card(theme);
        fitControl.setBackground(theme.card(12));
        fitControl.add(new IconButton(theme, Icon.FIT, 18, actions::showWholeNetwork)).size(CONTROL_SIZE);

        attribution.setTouchable(Touchable.disabled);
        attribution.setBackground(theme.roundRect(4, new Color(1f, 1f, 1f, 0.8f)));
        attribution.add(theme.label(ATTRIBUTION, theme.caption, Palette.MUTED)).pad(2, 8, 2, 8);

        stage.addActor(attribution);
        stage.addActor(zoomControls);
        stage.addActor(fitControl);
        stage.addActor(sidebar.getActor());
        stage.addActor(stopPanel.getActor());
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
        stopPanel.layout(
            compact ? MARGIN : MARGIN + sidebarWidth + GAP, height - MARGIN,
            compact ? sidebarWidth : STOP_PANEL_WIDTH, panelHeight);

        attribution.pack();
        attribution.setPosition(Math.round(width - attribution.getWidth()), 0);

        zoomControls.pack();
        zoomControls.setPosition(
            Math.round(width - MARGIN - zoomControls.getWidth()),
            Math.round(attribution.getHeight() + GAP));
        fitControl.pack();
        fitControl.setPosition(zoomControls.getX(), zoomControls.getTop() + GAP);
    }

    /** How much of the left side of the window is covered by panels. */
    public float getCoveredWidth() {
        boolean compact = stage.getWidth() < COMPACT_WIDTH;
        if (compact) return 0f;
        float covered = MARGIN + SIDEBAR_WIDTH;
        return stopPanel.isVisible() ? covered + GAP + STOP_PANEL_WIDTH : covered;
    }

    public boolean isOver(int screenX, int screenY) {
        return actorAt(screenX, screenY) != null;
    }

    private Actor actorAt(int screenX, int screenY) {
        stage.screenToStageCoordinates(tmp.set(screenX, screenY));
        return stage.hit(tmp.x, tmp.y, true);
    }

    /** Updates everything that depends on the pointer. */
    public void updatePointer(BusStop hoveredStop, BusLine hoveredLine, boolean overCluster) {
        int x = Gdx.input.getX();
        int y = Gdx.input.getY();

        tooltip.show(hoveredStop, hoveredLine);
        stage.screenToStageCoordinates(tmp.set(x, y));
        tooltip.moveTo(tmp.x, tmp.y, stage.getWidth(), stage.getHeight());

        Cursor.SystemCursor wanted = Cursor.SystemCursor.Arrow;
        if (hoveredStop != null || hoveredLine != null || overCluster) {
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
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
