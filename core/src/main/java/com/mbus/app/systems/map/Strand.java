package com.mbus.app.systems.map;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** A route as it is drawn: next to the other lines on the same street instead of on top of them. */
public class Strand {

    /** Center of the street. */
    final float[] x, y;
    /** To the left of the driving direction, longer in corners. */
    final float[] normalX, normalY;
    /** How many lanes to the left of the center, and how many lanes there are. */
    final float[] lane, laneCount;
    /** Distance along the original route, and how far the center is from there. */
    final double[] along;
    final float[] shiftX, shiftY;
    final Rectangle bounds = new Rectangle();

    Strand(float[] x, float[] y, float[] normalX, float[] normalY, float[] lane, float[] laneCount,
           double[] along, float[] shiftX, float[] shiftY) {
        this.x = x;
        this.y = y;
        this.normalX = normalX;
        this.normalY = normalY;
        this.lane = lane;
        this.laneCount = laneCount;
        this.along = along;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
        for (int i = 0; i < x.length; i++) {
            if (i == 0) bounds.set(x[i], y[i], 0, 0);
            else bounds.merge(x[i], y[i]);
        }
    }

    int size() {
        return x.length;
    }

    /** Where a point is drawn. */
    float drawnX(int i, LineStyle style) {
        return x[i] + normalX[i] * lane[i] * style.spacing(laneCount[i]) * style.pixel;
    }

    float drawnY(int i, LineStyle style) {
        return y[i] + normalY[i] * lane[i] * style.spacing(laneCount[i]) * style.pixel;
    }

    /** How far from the original route the line is drawn, at a distance along that route. */
    Vector2 offsetAt(double distance, LineStyle style, Vector2 out) {
        int low = 0;
        int high = along.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (along[middle] <= distance) low = middle;
            else high = middle;
        }
        double length = along[high] - along[low];
        float t = length <= 0 ? 0 : MathUtils.clamp((float) ((distance - along[low]) / length), 0f, 1f);
        float lowX = shiftX[low] + drawnX(low, style) - x[low];
        float lowY = shiftY[low] + drawnY(low, style) - y[low];
        float highX = shiftX[high] + drawnX(high, style) - x[high];
        float highY = shiftY[high] + drawnY(high, style) - y[high];
        return out.set(lowX + (highX - lowX) * t, lowY + (highY - lowY) * t);
    }
}
