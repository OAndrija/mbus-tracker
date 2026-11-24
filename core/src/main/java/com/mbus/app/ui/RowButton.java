package com.mbus.app.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** A row of a list that lights up under the pointer. */
class RowButton extends Button {

    RowButton(Theme theme) {
        super(new ButtonStyle());
        getStyle().over = theme.roundRect(10, Palette.SUBTLE);
        getStyle().down = theme.roundRect(10, Palette.BORDER);
        setProgrammaticChangeEvents(false);
    }
}
