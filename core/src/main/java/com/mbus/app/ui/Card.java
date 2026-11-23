package com.mbus.app.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.mbus.app.ui.theme.Theme;

/** A floating panel. Keeps clicks on it from reaching the map. */
class Card extends Table {

    static final float RADIUS = 16f;

    Card(Theme theme) {
        setBackground(theme.card(RADIUS));
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!(event.getTarget() instanceof TextField) && getStage() != null) {
                    getStage().setKeyboardFocus(null);
                }
                return true;
            }
        });
    }
}
