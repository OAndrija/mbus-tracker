package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** Names the stop or line under the pointer. */
class MapTooltip {

    private static final float MAX_TEXT_WIDTH = 320f;
    private static final float POINTER_GAP = 16f;

    private final Theme theme;
    private final Table table = new Table();
    private Object shown;

    MapTooltip(Theme theme) {
        this.theme = theme;
        table.setBackground(theme.roundRect(8, new Color(Palette.INK).lerp(Color.CLEAR, 0.06f)));
        table.setTouchable(Touchable.disabled);
        table.setVisible(false);
    }

    Actor getActor() {
        return table;
    }

    void show(Trip trip, BusStop stop, BusLine line) {
        Object target = trip != null ? trip : stop != null ? stop : line;
        if (target == shown) return;

        shown = target;
        table.clearChildren();
        table.setVisible(target != null);
        if (trip != null) {
            table.add(StopPanel.linePill(theme, trip.pattern.line)).minWidth(34).height(24).pad(6, 6, 6, 0);
            addText("Smer " + trip.pattern.getDestination(), 8);
        } else if (stop != null) {
            addText(stop.name, 12);
        } else if (line != null) {
            table.add(StopPanel.linePill(theme, line)).minWidth(34).height(24).pad(6, 6, 6, 0);
            addText(line.description, 8);
        }
    }

    private void addText(String text, float padLeft) {
        Label label = theme.label(text, theme.body, Color.WHITE);
        label.setEllipsis(true);
        table.add(label).width(Math.min(label.getPrefWidth(), MAX_TEXT_WIDTH)).pad(8, padLeft, 8, 12);
    }

    /** Keeps the tooltip next to the pointer and inside the window. */
    void moveTo(float pointerX, float pointerY, float stageWidth, float stageHeight) {
        if (shown == null) return;

        table.pack();
        float x = pointerX + POINTER_GAP;
        if (x + table.getWidth() > stageWidth - 8) x = pointerX - POINTER_GAP - table.getWidth();
        float y = pointerY + POINTER_GAP;
        if (y + table.getHeight() > stageHeight - 8) y = pointerY - POINTER_GAP - table.getHeight();
        table.setPosition(Math.round(x), Math.round(y));
    }
}
