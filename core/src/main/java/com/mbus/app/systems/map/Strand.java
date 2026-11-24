package com.mbus.app.systems.map;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.Arrays;

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

    /** The part between two distances along the original route. */
    Strand slice(double from, double to) {
        int first = 0;
        while (first + 1 < size() && along[first + 1] <= from) first++;
        int last = size() - 1;
        while (last > first && along[last - 1] >= to) last--;
        if (last == first) last = Math.min(size() - 1, first + 1);
        if (last == first) first = Math.max(0, last - 1);

        int n = last - first + 1;
        float[][] source = {x, y, normalX, normalY, lane, laneCount, shiftX, shiftY};
        float[][] part = new float[source.length][];
        for (int k = 0; k < source.length; k++) {
            part[k] = Arrays.copyOfRange(source[k], first, last + 1);
            part[k][0] = interpolate(source[k], first, from);
            part[k][n - 1] = interpolate(source[k], last - 1, to);
        }
        double[] partAlong = Arrays.copyOfRange(along, first, last + 1);
        partAlong[0] = Math.max(partAlong[0], from);
        partAlong[n - 1] = Math.min(partAlong[n - 1], to);
        return new Strand(part[0], part[1], part[2], part[3], part[4], part[5], partAlong, part[6], part[7]);
    }

    private float interpolate(float[] values, int index, double distance) {
        double length = along[index + 1] - along[index];
        float t = length <= 0 ? 0 : MathUtils.clamp((float) ((distance - along[index]) / length), 0f, 1f);
        return values[index] + (values[index + 1] - values[index]) * t;
    }
}
