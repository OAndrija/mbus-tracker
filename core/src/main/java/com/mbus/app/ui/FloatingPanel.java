package com.mbus.app.ui;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.mbus.app.ui.theme.Theme;

/** A card that fades in and out and is as tall as its content needs, up to the available height. */
abstract class FloatingPanel {

    protected static final float PADDING = 16f;

    protected final Theme theme;
    protected final Card card;
    private ScrollPane scroll;
    private boolean open = false;
    private float x, top, width, maxHeight;

    FloatingPanel(Theme theme) {
        this.theme = theme;
        card = new Card(theme);
        card.top();
        card.setVisible(false);
    }

    public Actor getActor() {
        return card;
    }

    public boolean isOpen() {
        return open;
    }

    protected void open() {
        if (open) return;
        open = true;
        card.clearActions();
        card.setVisible(true);
        card.setTouchable(Touchable.enabled);
        card.getColor().a = 0f;
        card.addAction(Actions.fadeIn(0.16f));
    }

    /** Closes the panel without telling anyone. */
    public void close() {
        if (!open) return;
        open = false;
        card.clearActions();
        card.setTouchable(Touchable.disabled);
        card.addAction(Actions.sequence(Actions.fadeOut(0.12f), Actions.visible(false)));
    }

    public void layout(float x, float top, float width, float maxHeight) {
        this.x = x;
        this.top = top;
        this.width = width;
        this.maxHeight = maxHeight;
        applyBounds();
    }

    private void applyBounds() {
        card.setWidth(width);
        card.validate(); // wrapped labels need the width before they know their height
        float height = Math.min(card.getPrefHeight(), maxHeight);
        card.setBounds(x, top - height, width, height);
    }

    /** Fills the empty card. */
    protected abstract void build();

    protected void rebuild(boolean keepScroll) {
        float scrollY = keepScroll && scroll != null ? scroll.getScrollY() : 0f;
        scroll = null;
        card.clearChildren();
        build();
        applyBounds();
        scrollTo(scrollY);
    }

    protected void scrollTo(float scrollY) {
        if (scroll == null) return;
        card.validate();
        scroll.setScrollY(MathUtils.clamp(scrollY, 0, Math.max(0, scroll.getMaxY())));
        scroll.updateVisualScroll();
    }

    protected float getScrollHeight() {
        return scroll == null ? 0f : scroll.getScrollHeight();
    }

    /** Adds the part of the panel that scrolls. */
    protected void addScrolling(Table content) {
        scroll = new ScrollPane(content, theme.scrollPane());
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setScrollbarsOnTop(true);
        ScrollOnHover.attach(scroll);
        card.add(scroll).growX().expandY().top().padBottom(8);
    }
}
