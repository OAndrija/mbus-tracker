package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** Says what a button without a text does. */
final class Tips {

    private static final float DELAY_SECONDS = 0.5f;

    private Tips() {
    }

    static void attach(Theme theme, Actor actor, String text) {
        TooltipManager manager = TooltipManager.getInstance();
        if (manager.initialTime != DELAY_SECONDS) {
            manager.initialTime = DELAY_SECONDS;
            manager.subsequentTime = DELAY_SECONDS;
            manager.animations = false;
            manager.hideAll(); // makes it use the new delay
        }

        Tooltip<Label> tooltip = new Tooltip<>(theme.label(text, theme.caption, Color.WHITE), manager);
        tooltip.getContainer().background(theme.roundRect(6, Palette.INK)).pad(5, 9, 5, 9);
        actor.addListener(tooltip);
    }
}
