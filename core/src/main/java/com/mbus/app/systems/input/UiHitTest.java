package com.mbus.app.systems.input;

/** Tells whether a screen position is covered by the interface. */
public interface UiHitTest {
    boolean isOverUi(int screenX, int screenY);
}
