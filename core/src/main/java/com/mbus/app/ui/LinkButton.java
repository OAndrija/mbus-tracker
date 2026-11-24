package com.mbus.app.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** A text-only button. */
class LinkButton extends TextButton {

    LinkButton(Theme theme, String text, final Runnable action) {
        super(text, style(theme));
        addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
    }

    private static TextButtonStyle style(Theme theme) {
        TextButtonStyle style = new TextButtonStyle();
        style.font = theme.captionStrong;
        style.fontColor = Palette.BRAND;
        style.overFontColor = Palette.BRAND_STRONG;
        return style;
    }
}
