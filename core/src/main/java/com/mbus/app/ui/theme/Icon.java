package com.mbus.app.ui.theme;

import static com.mbus.app.ui.theme.Shapes.circle;
import static com.mbus.app.ui.theme.Shapes.outline;
import static com.mbus.app.ui.theme.Shapes.roundRect;
import static com.mbus.app.ui.theme.Shapes.stroke;
import static com.mbus.app.ui.theme.Shapes.subtract;
import static com.mbus.app.ui.theme.Shapes.union;

/** The icons, drawn on a 24 x 24 grid. */
public enum Icon {

    SEARCH(union(outline(2, circle(10.5f, 10.5f, 6.5f)), stroke(2, 15.5f, 15.5f, 20.5f, 20.5f))),
    CLOSE(union(stroke(2, 6.5f, 6.5f, 17.5f, 17.5f), stroke(2, 17.5f, 6.5f, 6.5f, 17.5f))),
    CHEVRON_RIGHT(stroke(2, 9.5f, 6, 15.5f, 12, 9.5f, 18)),
    CHEVRON_UP(stroke(2, 6, 14.5f, 12, 8.5f, 18, 14.5f)),
    CHEVRON_DOWN(stroke(2, 6, 9.5f, 12, 15.5f, 18, 9.5f)),
    PLUS(union(stroke(2, 12, 5.5f, 12, 18.5f), stroke(2, 5.5f, 12, 18.5f, 12))),
    MINUS(stroke(2, 5.5f, 12, 18.5f, 12)),
    CLOCK(union(outline(2, circle(12, 12, 8.5f)), stroke(2, 12, 7.5f, 12, 12, 15, 14))),
    /** Corner brackets: show the whole network. */
    FIT(union(
        stroke(2, 4.5f, 9, 4.5f, 4.5f, 9, 4.5f), stroke(2, 15, 4.5f, 19.5f, 4.5f, 19.5f, 9),
        stroke(2, 19.5f, 15, 19.5f, 19.5f, 15, 19.5f), stroke(2, 9, 19.5f, 4.5f, 19.5f, 4.5f, 15))),
    BUS(subtract(
        union(
            roundRect(4, 2.5f, 16, 17, 3.2f),
            roundRect(5.5f, 17, 3.2f, 5, 1.2f),
            roundRect(15.3f, 17, 3.2f, 5, 1.2f)),
        roundRect(6.2f, 6.2f, 11.6f, 6.2f, 1.2f),
        roundRect(9, 3.9f, 6, 1.1f, 0.55f),
        circle(8, 16, 1.25f),
        circle(16, 16, 1.25f)));

    public static final float GRID = 24f;

    final Shapes.Distance shape;

    Icon(Shapes.Distance shape) {
        this.shape = shape;
    }
}
