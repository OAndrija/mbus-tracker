package com.mbus.app.ui.theme;

import static com.mbus.app.ui.theme.Shapes.circle;
import static com.mbus.app.ui.theme.Shapes.outline;
import static com.mbus.app.ui.theme.Shapes.polygon;
import static com.mbus.app.ui.theme.Shapes.roundRect;
import static com.mbus.app.ui.theme.Shapes.star;
import static com.mbus.app.ui.theme.Shapes.stroke;
import static com.mbus.app.ui.theme.Shapes.subtract;
import static com.mbus.app.ui.theme.Shapes.union;

/** The icons, drawn on a 24 x 24 grid. */
public enum Icon {

    SEARCH(union(outline(2, circle(10.5f, 10.5f, 6.5f)), stroke(2, 15.5f, 15.5f, 20.5f, 20.5f))),
    CLOSE(union(stroke(2, 6.5f, 6.5f, 17.5f, 17.5f), stroke(2, 17.5f, 6.5f, 6.5f, 17.5f))),
    CHEVRON_LEFT(stroke(2, 14.5f, 6, 8.5f, 12, 14.5f, 18)),
    CHEVRON_RIGHT(stroke(2, 9.5f, 6, 15.5f, 12, 9.5f, 18)),
    CHEVRON_UP(stroke(2, 6, 14.5f, 12, 8.5f, 18, 14.5f)),
    CHEVRON_DOWN(stroke(2, 6, 9.5f, 12, 15.5f, 18, 9.5f)),
    PLUS(union(stroke(2, 12, 5.5f, 12, 18.5f), stroke(2, 5.5f, 12, 18.5f, 12))),
    MINUS(stroke(2, 5.5f, 12, 18.5f, 12)),
    CLOCK(union(outline(2, circle(12, 12, 8.5f)), stroke(2, 12, 7.5f, 12, 12, 15, 14))),
    STAR(outline(2, polygon(1, star(12, 12.6f, 8.6f, 4.1f)))),
    STAR_FILLED(polygon(2, star(12, 12.6f, 8.6f, 4.1f))),
    /** A circle with a dot and four marks: keep the bus in the middle. */
    FOLLOW(union(
        outline(2, circle(12, 12, 6)), circle(12, 12, 2.2f),
        stroke(2, 12, 2.5f, 12, 5), stroke(2, 12, 19, 12, 21.5f),
        stroke(2, 2.5f, 12, 5, 12), stroke(2, 19, 12, 21.5f, 12))),
    /** From one place to another. */
    ROUTE(union(
        outline(2, circle(6, 18, 2.6f)), outline(2, circle(18, 6, 2.6f)),
        stroke(2, 8.6f, 18, 15, 18, 17, 17, 17.6f, 15, 17, 13, 15, 12, 9, 12, 7, 11, 6.4f, 9, 7, 7, 9, 6, 15.4f, 6))),
    SWAP(union(
        stroke(2, 8, 19, 8, 5), stroke(2, 4.5f, 8.5f, 8, 5, 11.5f, 8.5f),
        stroke(2, 16, 5, 16, 19), stroke(2, 12.5f, 15.5f, 16, 19, 19.5f, 15.5f))),
    ORIGIN(outline(3, circle(12, 12, 5.5f))),
    PIN(subtract(
        union(circle(12, 9.5f, 6.6f), polygon(0.4f, 6.4f, 12.6f, 17.6f, 12.6f, 12, 21.4f)), circle(12, 9.5f, 2.5f))),
    WALK(union(
        circle(13.2f, 4.2f, 2.1f), stroke(2.4f, 12.6f, 9, 11.4f, 14.2f),
        stroke(2, 11.4f, 14.2f, 8.4f, 20.8f), stroke(2, 11.6f, 14.2f, 14.6f, 16.6f, 15.8f, 20.8f),
        stroke(1.8f, 12.4f, 8.8f, 9.4f, 10.4f, 8.4f, 13.6f), stroke(1.8f, 12.8f, 9, 14.8f, 11.6f, 17.6f, 12.4f))),
    MOON(subtract(circle(11.5f, 12.5f, 8.5f), circle(16.5f, 8.5f, 7.2f))),
    SUN(union(circle(12, 12, 4.2f),
        stroke(2, 12, 2.6f, 12, 4.4f), stroke(2, 12, 19.6f, 12, 21.4f),
        stroke(2, 2.6f, 12, 4.4f, 12), stroke(2, 19.6f, 12, 21.4f, 12),
        stroke(2, 5.4f, 5.4f, 6.6f, 6.6f), stroke(2, 17.4f, 17.4f, 18.6f, 18.6f),
        stroke(2, 5.4f, 18.6f, 6.6f, 17.4f), stroke(2, 17.4f, 6.6f, 18.6f, 5.4f))),
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
