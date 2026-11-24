package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

import java.util.Collections;
import java.util.List;

/** The list of stops. Draws only the rows in view, so hundreds of stops cost no more than ten. */
class StopListView extends Widget implements Cullable {

    interface Listener {
        void onStopClicked(BusStop stop);
    }

    private static final float ROW_HEIGHT = 60f;
    private static final float PADDING = 12f;
    private static final float RIGHT_SPACE = 58f;
    private static final float PILL_HEIGHT = 18f;
    private static final float PILL_GAP = 4f;

    private final Theme theme;
    private final Drawable hoverBackground;
    private final Drawable selectedBackground;
    private final Drawable separator;
    private final Drawable chevron;
    private final Drawable chevronSelected;
    private final GlyphLayout layout = new GlyphLayout();

    private List<BusStop> stops = Collections.emptyList();
    private BusStop selected;
    private int hovered = -1;
    private Rectangle cullingArea;

    StopListView(Theme theme, final Listener listener) {
        this.theme = theme;
        hoverBackground = theme.roundRect(10, Palette.SUBTLE);
        selectedBackground = theme.roundRect(10, Palette.BRAND_SOFT);
        separator = theme.fill(Palette.SUBTLE, 1, 1);
        chevron = theme.icon(Icon.CHEVRON_RIGHT, 16, Palette.FAINT);
        chevronSelected = theme.icon(Icon.CHEVRON_RIGHT, 16, Palette.BRAND);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int row = rowAt(y);
                if (row >= 0) listener.onStopClicked(stops.get(row));
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                hovered = rowAt(y);
                return false;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) hovered = -1;
            }
        });
    }

    void setStops(List<BusStop> stops) {
        this.stops = stops;
        hovered = -1;
        invalidateHierarchy();
    }

    void setSelected(BusStop stop) {
        this.selected = stop;
    }

    /** The y range of a stop's row, counted from the top of the list, or -1 if it is not listed. */
    float rowTop(BusStop stop) {
        int row = stops.indexOf(stop);
        return row < 0 ? -1 : row * ROW_HEIGHT;
    }

    float rowHeight() {
        return ROW_HEIGHT;
    }

    private int rowAt(float y) {
        int row = (int) ((getHeight() - y) / ROW_HEIGHT);
        return row >= 0 && row < stops.size() ? row : -1;
    }

    @Override
    public void setCullingArea(Rectangle cullingArea) {
        this.cullingArea = cullingArea;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        float alpha = getColor().a * parentAlpha;

        for (int i = 0; i < stops.size(); i++) {
            float bottom = getHeight() - (i + 1) * ROW_HEIGHT;
            if (cullingArea != null
                && (bottom > cullingArea.y + cullingArea.height || bottom + ROW_HEIGHT < cullingArea.y)) {
                continue;
            }
            drawRow(batch, i, getX(), getY() + bottom, alpha);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawRow(Batch batch, int row, float x, float y, float alpha) {
        BusStop stop = stops.get(row);
        boolean isSelected = stop == selected;
        float width = getWidth();

        batch.setColor(1, 1, 1, alpha);
        if (isSelected) {
            selectedBackground.draw(batch, x, y + 1, width, ROW_HEIGHT - 2);
        } else if (row == hovered) {
            hoverBackground.draw(batch, x, y + 1, width, ROW_HEIGHT - 2);
        } else if (row > 0) {
            separator.draw(batch, x + PADDING, y + ROW_HEIGHT - 1, width - 2 * PADDING, 1);
        }

        float textWidth = width - PADDING - RIGHT_SPACE;
        text(batch, theme.bodyStrong, stop.name, isSelected ? Palette.BRAND : Palette.INK,
            x + PADDING, y + ROW_HEIGHT - 20, textWidth, Align.left, alpha);

        text(batch, theme.caption, stop.id, Palette.FAINT,
            x + width - RIGHT_SPACE, y + ROW_HEIGHT / 2f, RIGHT_SPACE - 30, Align.right, alpha);

        (isSelected ? chevronSelected : chevron)
            .draw(batch, x + width - 24, y + ROW_HEIGHT / 2f - 8, 16, 16);

        drawPills(batch, stop, x + PADDING, y + 10, textWidth, alpha);
    }

    private void drawPills(Batch batch, BusStop stop, float x, float y, float width, float alpha) {
        List<BusLine> lines = stop.getLines();
        if (lines.isEmpty()) {
            text(batch, theme.caption, "Brez linij", Palette.FAINT,
                x, y + PILL_HEIGHT / 2f, width, Align.left, alpha);
            return;
        }

        float left = x;
        for (int i = 0; i < lines.size(); i++) {
            BusLine line = lines.get(i);
            String label = line.name;
            layout.setText(theme.captionStrong, label);
            float pillWidth = Math.max(24f, (float) Math.ceil(layout.width) + 10f);

            int remaining = lines.size() - i;
            float reserve = remaining > 1 ? 24f + PILL_GAP : 0f;
            if (left + pillWidth + reserve > x + width) {
                text(batch, theme.caption, "+" + remaining, Palette.MUTED,
                    left + 2, y + PILL_HEIGHT / 2f, 30, Align.left, alpha);
                return;
            }

            batch.setColor(1, 1, 1, alpha);
            theme.roundRect(5, line.color).draw(batch, left, y, pillWidth, PILL_HEIGHT);
            text(batch, theme.captionStrong, label, line.textColor,
                left, y + PILL_HEIGHT / 2f, pillWidth, Align.center, alpha);
            left += pillWidth + PILL_GAP;
        }
    }

    /** Draws one line of text centered on y and shortened to the given width. */
    private void text(Batch batch, BitmapFont font, String text, Color color,
                      float x, float centerY, float width, int align, float alpha) {
        font.setColor(color.r, color.g, color.b, color.a * alpha);
        layout.setText(font, text, 0, text.length(), font.getColor(), width, align, false, "…");
        font.draw(batch, layout, x, Math.round(centerY + font.getCapHeight() / 2f));
    }

    @Override public float getPrefWidth() { return 0; }
    @Override public float getPrefHeight() { return stops.size() * ROW_HEIGHT; }
}
