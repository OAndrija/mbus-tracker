package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

/** An animated on/off switch. */
class Switch extends Button {

    private static final float WIDTH = 34f;
    private static final float HEIGHT = 20f;
    private static final float KNOB = 14f;
    private static final float SPEED = 14f;

    private final Drawable trackOff;
    private final Drawable trackOn;
    private final Drawable knob;
    private float position;

    Switch(Theme theme, boolean checked) {
        super(new ButtonStyle());
        trackOff = theme.roundRect(HEIGHT / 2f, Color.valueOf("cbd5e1"));
        trackOn = theme.roundRect(HEIGHT / 2f, Palette.BRAND);
        knob = theme.roundRect(KNOB / 2f, Palette.SURFACE);
        setChecked(checked);
        position = checked ? 1f : 0f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float target = isChecked() ? 1f : 0f;
        position += (target - position) * Math.min(1f, delta * SPEED);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color color = getColor();
        float alpha = color.a * parentAlpha;
        float x = getX() + (getWidth() - WIDTH) / 2f;
        float y = getY() + (getHeight() - HEIGHT) / 2f;

        batch.setColor(1, 1, 1, alpha);
        trackOff.draw(batch, x, y, WIDTH, HEIGHT);
        batch.setColor(1, 1, 1, alpha * position);
        trackOn.draw(batch, x, y, WIDTH, HEIGHT);

        float inset = (HEIGHT - KNOB) / 2f;
        float travel = WIDTH - KNOB - 2 * inset;
        batch.setColor(1, 1, 1, alpha);
        knob.draw(batch, x + inset + travel * position, y + inset, KNOB, KNOB);
    }

    @Override public float getPrefWidth() { return WIDTH; }
    @Override public float getPrefHeight() { return HEIGHT; }
}
