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
import com.mbus.app.utils.Texts;

import java.util.Collections;
import java.util.List;

/** The list of stops. Draws only the rows in view, so hundreds of stops cost no more than ten. */
class StopListView extends Widget implements Cullable {

    interface Listener {
        void onStopClicked(BusStop stop);

        void onStarClicked(BusStop stop);

        boolean isFavorite(BusStop stop);
    }

    private static final float ROW_HEIGHT = 60f;
    private static final float SECTION_HEIGHT = 30f;
    private static final float STAR_SIZE = 18f;
    private static final float STAR_AREA = 40f;
    private static final float PADDING = 12f;
    private static final float RIGHT_SPACE = 70f;
    private static final float PILL_HEIGHT = 18f;
    private static final float PILL_GAP = 4f;

    private final Theme theme;
    private final Drawable hoverBackground;
    private final Drawable selectedBackground;
    private final Drawable separator;
    private final Drawable star;
    private final Drawable starHovered;
    private final Drawable starFilled;
    private final Listener listener;
    private final GlyphLayout layout = new GlyphLayout();

    private List<BusStop> stops = Collections.emptyList();
    private BusStop selected;
    /** The first stops of the list are favorites and get their own section. */
    private int favoriteCount = 0;
    private int hovered = -1;
    private boolean starIsHovered = false;
    private Rectangle cullingArea;

    StopListView(Theme theme, final Listener listener) {
        this.theme = theme;
        hoverBackground = theme.roundRect(10, Palette.SUBTLE);
        selectedBackground = theme.roundRect(10, Palette.BRAND_SOFT);
        separator = theme.fill(Palette.SUBTLE, 1, 1);
        star = theme.icon(Icon.STAR, STAR_SIZE, Palette.QUIET);
        starHovered = theme.icon(Icon.STAR, STAR_SIZE, Palette.STAR);
        starFilled = theme.icon(Icon.STAR_FILLED, STAR_SIZE, Palette.STAR);
        this.listener = listener;

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int row = rowAt(y);
                if (row < 0) return;
                if (isOnStar(x)) listener.onStarClicked(stops.get(row));
                else listener.onStopClicked(stops.get(row));
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                hovered = rowAt(y);
                starIsHovered = isOnStar(x);
                return false;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) hovered = -1;
            }
        });
    }

    void setStops(List<BusStop> stops, int favoriteCount) {
        this.stops = stops;
        this.favoriteCount = favoriteCount;
        hovered = -1;
        invalidateHierarchy();
    }

    void setSelected(BusStop stop) {
        this.selected = stop;
    }

    private boolean isOnStar(float x) {
        return x > getWidth() - STAR_AREA;
    }

    /** Where a stop's row starts, counted from the top of the list, or -1 if it is not listed. */
    float rowTop(BusStop stop) {
        int row = stops.indexOf(stop);
        return row < 0 ? -1 : topOf(row);
    }

    float rowHeight() {
        return ROW_HEIGHT;
    }

    private float topOf(int row) {
        float top = row * ROW_HEIGHT;
        if (favoriteCount > 0) {
            top += SECTION_HEIGHT;
            if (row >= favoriteCount) top += SECTION_HEIGHT;
        }
        return top;
    }

    private int rowAt(float y) {
        float fromTop = getHeight() - y;
        for (int row = 0; row < stops.size(); row++) {
            float top = topOf(row);
            if (fromTop < top) return -1;
            if (fromTop < top + ROW_HEIGHT) return row;
        }
        return -1;
    }

    @Override
    public void setCullingArea(Rectangle cullingArea) {
        this.cullingArea = cullingArea;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        float alpha = getColor().a * parentAlpha;

        if (favoriteCount > 0) {
            drawSection(batch, Texts.get("favorites"), 0, alpha);
            if (favoriteCount < stops.size()) {
                drawSection(batch, Texts.get("allStops"), topOf(favoriteCount) - SECTION_HEIGHT, alpha);
            }
        }

        for (int i = 0; i < stops.size(); i++) {
            float bottom = getHeight() - topOf(i) - ROW_HEIGHT;
            if (cullingArea != null
                && (bottom > cullingArea.y + cullingArea.height || bottom + ROW_HEIGHT < cullingArea.y)) {
                continue;
            }
            drawRow(batch, i, getX(), getY() + bottom, alpha);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawSection(Batch batch, String title, float top, float alpha) {
        text(batch, theme.captionStrong, title, Palette.MUTED, getX() + PADDING,
            getY() + getHeight() - top - SECTION_HEIGHT / 2f, getWidth() - 2 * PADDING, Align.left, alpha);
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
        } else if (row > 0 && row != favoriteCount) {
            separator.draw(batch, x + PADDING, y + ROW_HEIGHT - 1, width - 2 * PADDING, 1);
        }

        float textWidth = width - PADDING - RIGHT_SPACE;
        text(batch, theme.bodyStrong, stop.name, isSelected ? Palette.BRAND : Palette.INK,
            x + PADDING, y + ROW_HEIGHT - 20, textWidth, Align.left, alpha);

        text(batch, theme.caption, stop.id, Palette.FAINT,
            x + width - RIGHT_SPACE, y + ROW_HEIGHT / 2f, RIGHT_SPACE - 38, Align.right, alpha);

        boolean onStar = row == hovered && starIsHovered;
        Drawable icon = listener.isFavorite(stop) ? starFilled : onStar ? starHovered : star;
        batch.setColor(1, 1, 1, alpha);
        icon.draw(batch, x + width - 12 - STAR_SIZE, y + ROW_HEIGHT / 2f - STAR_SIZE / 2f, STAR_SIZE, STAR_SIZE);

        drawPills(batch, stop, x + PADDING, y + 10, textWidth, alpha);
    }

    private void drawPills(Batch batch, BusStop stop, float x, float y, float width, float alpha) {
        List<BusLine> lines = stop.getLines();
        if (lines.isEmpty()) {
            text(batch, theme.caption, Texts.get("noLines"), Palette.FAINT,
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
            theme.linePill(5, line).draw(batch, left, y, pillWidth, PILL_HEIGHT);
            text(batch, theme.captionStrong, label, Palette.onLine(line),
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
    @Override
    public float getPrefHeight() {
        return stops.isEmpty() ? 0 : topOf(stops.size() - 1) + ROW_HEIGHT;
    }
}
