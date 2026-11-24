package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** A borderless button that shows an icon and lights up under the pointer. */
class IconButton extends ImageButton {

    private final Theme theme;
    private final float iconSize;

    IconButton(Theme theme, Icon icon, float iconSize, final Runnable action) {
        super(new ImageButtonStyle());
        this.theme = theme;
        this.iconSize = iconSize;

        ImageButtonStyle style = getStyle();
        style.over = theme.roundRect(8, Palette.SUBTLE);
        style.down = theme.roundRect(8, Palette.BORDER);
        setIcon(icon);

        addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
    }

    void setIcon(Icon icon) {
        setIcon(icon, Palette.MUTED, Palette.INK);
    }

    void setIcon(Icon icon, Color color, Color hoverColor) {
        ImageButtonStyle style = getStyle();
        style.imageUp = theme.icon(icon, iconSize, color);
        style.imageOver = theme.icon(icon, iconSize, hoverColor);
        style.imageDisabled = theme.icon(icon, iconSize, Palette.BORDER);
        setStyle(style);
    }
}
