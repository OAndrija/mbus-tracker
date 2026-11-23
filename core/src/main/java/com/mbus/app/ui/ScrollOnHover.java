package com.mbus.app.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;

/** Lets the mouse wheel scroll a pane as soon as the pointer is over it, without a click first. */
final class ScrollOnHover {

    private ScrollOnHover() {
    }

    static void attach(final ScrollPane pane) {
        pane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pane.getStage() != null) pane.getStage().setScrollFocus(pane);
            }
        });
    }
}
