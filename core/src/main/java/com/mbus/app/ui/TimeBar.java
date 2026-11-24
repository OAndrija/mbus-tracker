package com.mbus.app.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.mbus.app.model.Timetable;
import com.mbus.app.ui.theme.Icon;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.AppClock;
import com.mbus.app.utils.CityTime;

import java.time.LocalDate;

/** Chooses the day and time the app shows. The bars tell how many buses run when. */
public class TimeBar extends FloatingPanel {

    static final float HEIGHT = 108f;
    private static final int[] SPEEDS = {1, 10, 60};
    private static final int SLOTS = 96;

    private final Timetable timetable;
    private final AppClock clock;
    private final Runnable onClose;

    private final Label day;
    private final Label time;
    private final IconButton previous;
    private final IconButton next;
    private final LinkButton speed;
    private final LinkButton now;

    private LocalDate countedDate;
    private int[] counts = new int[SLOTS];
    private int maxCount = 1;

    public TimeBar(Theme theme, Timetable timetable, AppClock clock, Runnable onClose) {
        super(theme);
        this.timetable = timetable;
        this.clock = clock;
        this.onClose = onClose;

        day = theme.label("", theme.bodyStrong, Palette.INK);
        time = theme.label("", theme.title, Palette.INK);
        previous = new IconButton(theme, Icon.CHEVRON_LEFT, 16, () -> moveDay(-1));
        next = new IconButton(theme, Icon.CHEVRON_RIGHT, 16, () -> moveDay(1));
        speed = new LinkButton(theme, "", this::cycleSpeed);
        now = new LinkButton(theme, "Zdaj", clock::goLive);
        Tips.attach(theme, previous, "Dan prej");
        Tips.attach(theme, next, "Dan kasneje");
        Tips.attach(theme, speed, "Hitrost časa");
        Tips.attach(theme, now, "Nazaj na trenutni čas");
    }

    public void show() {
        rebuild(false);
        open();
    }

    @Override
    protected void build() {
        Table top = new Table();
        top.add(previous).size(30);
        top.add(day).minWidth(96).padLeft(4).padRight(4);
        day.setAlignment(com.badlogic.gdx.utils.Align.center);
        top.add(next).size(30);
        top.add(time).expandX();
        top.add(now).minWidth(40).height(30);
        top.add(speed).minWidth(40).height(30).padLeft(6);
        top.add(new IconButton(theme, Icon.CLOSE, 14, onClose)).size(30).padLeft(6);

        card.add(top).growX().pad(8, 10, 0, 8).row();
        card.add(new Slider()).growX().height(58).pad(0, 18, 8, 18);
        update();
    }

    public void update() {
        LocalDate date = clock.getDate();
        LocalDate today = clock.getRealDate();
        day.setText(CityTime.describe(date, today));
        time.setText(CityTime.format(clock.getMinute()));
        time.getStyle().fontColor = clock.isLive() ? Palette.INK : Palette.BRAND;
        speed.setText(clock.getSpeed() + "×");
        now.setVisible(!clock.isLive());
        previous.setDisabled(!date.isAfter(today));
        next.setDisabled(!date.isBefore(timetable.getValidUntil()));

        if (!date.equals(countedDate)) {
            countedDate = date;
            counts = timetable.countBuses(date, SLOTS);
            maxCount = 1;
            for (int count : counts) maxCount = Math.max(maxCount, count);
        }
    }

    private void moveDay(int days) {
        LocalDate date = clock.getDate().plusDays(days);
        if (date.isBefore(clock.getRealDate()) || date.isAfter(timetable.getValidUntil())) return;
        clock.travelTo(date, clock.getMinutes());
    }

    private void cycleSpeed() {
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == clock.getSpeed()) {
                clock.setSpeed(SPEEDS[(i + 1) % SPEEDS.length]);
                return;
            }
        }
        clock.setSpeed(1);
    }

    private class Slider extends Widget {

        private static final float TRACK_Y = 22f;
        private static final float KNOB = 8f;
        private static final float MAX_BAR = 28f;

        private final Drawable track = theme.roundRect(2, Palette.BORDER);
        private final Drawable filled = theme.roundRect(2, Palette.BRAND);
        private final Drawable knob = theme.roundRect(KNOB, Palette.BRAND);
        private final Drawable knobCenter = theme.roundRect(KNOB - 3, Palette.SURFACE);
        private final Drawable bar = theme.fill(Palette.BORDER, 1, 1);
        private final Drawable pastBar = theme.fill(new Color(Palette.BRAND).lerp(Palette.SURFACE, 0.7f), 1, 1);
        private final GlyphLayout layout = new GlyphLayout();

        Slider() {
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    travel(x);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    travel(x);
                }
            });
        }

        private void travel(float x) {
            float share = MathUtils.clamp(x / getWidth(), 0f, 1f);
            int step = 1; // minutes
            clock.travelTo(clock.getDate(), Math.round(share * AppClock.MINUTES_PER_DAY / step) * step);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = getColor().a * parentAlpha;
            batch.setColor(1, 1, 1, alpha);
            float share = (float) (clock.getMinutes() / AppClock.MINUTES_PER_DAY);
            float x = getX();
            float y = getY() + TRACK_Y;
            float width = getWidth();

            float slot = width / SLOTS;
            for (int i = 0; i < SLOTS; i++) {
                float height = MAX_BAR * counts[i] / maxCount;
                if (height <= 0) continue;
                (i + 0.5f <= share * SLOTS ? pastBar : bar).draw(batch, x + i * slot, y + 5, Math.max(1f, slot - 1f), height);
            }

            track.draw(batch, x, y - 2, width, 4);
            if (width * share > 4) filled.draw(batch, x, y - 2, width * share, 4);
            float knobX = x + width * share;
            knob.draw(batch, knobX - KNOB, y - KNOB, 2 * KNOB, 2 * KNOB);
            knobCenter.draw(batch, knobX - KNOB + 3, y - KNOB + 3, 2 * KNOB - 6, 2 * KNOB - 6);

            theme.caption.setColor(Palette.FAINT.r, Palette.FAINT.g, Palette.FAINT.b, alpha);
            for (int hour = 0; hour <= 24; hour += 6) {
                layout.setText(theme.caption, hour + ":00");
                float textX = x + width * hour / 24f - layout.width / 2f;
                textX = MathUtils.clamp(textX, x - 8, x + width - layout.width + 8);
                theme.caption.draw(batch, layout, Math.round(textX), getY() + 10);
            }
        }
    }
}
