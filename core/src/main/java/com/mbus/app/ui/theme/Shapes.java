package com.mbus.app.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Draws anti-aliased shapes from signed distances: negative inside, positive outside.
 * One pixel of distance is one pixel of edge softness, so edges are crisp at any size.
 */
public final class Shapes {

    /** Signed distance to a shape; coordinates are in the caller's unit, y pointing down. */
    public interface Distance {
        float at(float x, float y);
    }

    private Shapes() {
    }

    /** Fills a transparent pixmap with the shape, {@code scale} pixels per unit. */
    public static Pixmap draw(int width, int height, float scale, Color color, Distance shape) {
        Pixmap pixmap = transparent(width, height);
        paint(pixmap, scale, color, shape);
        return pixmap;
    }

    public static Pixmap transparent(int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        // White rather than black, so filtering does not darken the edges.
        pixmap.setColor(1f, 1f, 1f, 0f);
        pixmap.fill();
        return pixmap;
    }

    /** Blends the shape over what is already there. */
    public static void paint(Pixmap pixmap, float scale, Color color, Distance shape) {
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                float distance = shape.at((x + 0.5f) / scale, (y + 0.5f) / scale) * scale;
                float coverage = clamp(0.5f - distance);
                if (coverage > 0f) blend(pixmap, x, y, color, color.a * coverage);
            }
        }
    }

    /** Blends a soft shadow of the shape over what is already there. */
    public static void paintShadow(Pixmap pixmap, float scale, float blur, float strength, Distance shape) {
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                float distance = shape.at((x + 0.5f) / scale, (y + 0.5f) / scale);
                float t = clamp(0.5f - distance / (2f * blur));
                float alpha = strength * t * t * (3f - 2f * t);
                if (alpha > 0f) blend(pixmap, x, y, Palette.INK, alpha);
            }
        }
    }

    private static void blend(Pixmap pixmap, int x, int y, Color color, float alpha) {
        int below = pixmap.getPixel(x, y);
        float belowAlpha = (below & 0xff) / 255f;
        float outAlpha = alpha + belowAlpha * (1f - alpha);
        float keep = belowAlpha * (1f - alpha);
        float r = (color.r * alpha + ((below >>> 24) & 0xff) / 255f * keep) / outAlpha;
        float g = (color.g * alpha + ((below >>> 16) & 0xff) / 255f * keep) / outAlpha;
        float b = (color.b * alpha + ((below >>> 8) & 0xff) / 255f * keep) / outAlpha;
        pixmap.drawPixel(x, y, Color.rgba8888(clamp(r), clamp(g), clamp(b), outAlpha));
    }

    public static Distance circle(float centerX, float centerY, float radius) {
        return (x, y) -> length(x - centerX, y - centerY) - radius;
    }

    public static Distance roundRect(float left, float top, float width, float height, float radius) {
        float cx = left + width / 2f;
        float cy = top + height / 2f;
        return (x, y) -> {
            float dx = Math.abs(x - cx) - width / 2f + radius;
            float dy = Math.abs(y - cy) - height / 2f + radius;
            return Math.min(Math.max(dx, dy), 0f) + length(Math.max(dx, 0f), Math.max(dy, 0f)) - radius;
        };
    }

    /** A line through the given x, y pairs with round ends and corners. */
    public static Distance stroke(float width, float... points) {
        return (x, y) -> {
            float best = Float.MAX_VALUE;
            for (int i = 0; i + 3 < points.length; i += 2) {
                best = Math.min(best, segment(x, y, points[i], points[i + 1], points[i + 2], points[i + 3]));
            }
            return best - width / 2f;
        };
    }

    /** A triangle with slightly rounded corners, given as x, y pairs. */
    public static Distance triangle(float rounding, float x1, float y1, float x2, float y2, float x3, float y3) {
        return (x, y) -> {
            float side1 = side(x, y, x1, y1, x2, y2);
            float side2 = side(x, y, x2, y2, x3, y3);
            float side3 = side(x, y, x3, y3, x1, y1);
            boolean inside = (side1 >= 0 && side2 >= 0 && side3 >= 0) || (side1 <= 0 && side2 <= 0 && side3 <= 0);
            float edge = Math.min(segment(x, y, x1, y1, x2, y2),
                Math.min(segment(x, y, x2, y2, x3, y3), segment(x, y, x3, y3, x1, y1)));
            return (inside ? -edge : edge) - rounding;
        };
    }

    private static float side(float x, float y, float x1, float y1, float x2, float y2) {
        return (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1);
    }

    /** Grows a shape on all sides, rounding its corners. */
    public static Distance grow(float amount, Distance shape) {
        return (x, y) -> shape.at(x, y) - amount;
    }

    /** Only the outline of a shape. */
    public static Distance outline(float width, Distance shape) {
        return (x, y) -> Math.abs(shape.at(x, y)) - width / 2f;
    }

    public static Distance union(Distance... shapes) {
        return (x, y) -> {
            float best = Float.MAX_VALUE;
            for (Distance shape : shapes) best = Math.min(best, shape.at(x, y));
            return best;
        };
    }

    public static Distance subtract(Distance shape, Distance... holes) {
        return (x, y) -> {
            float distance = shape.at(x, y);
            for (Distance hole : holes) distance = Math.max(distance, -hole.at(x, y));
            return distance;
        };
    }

    private static float segment(float x, float y, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0 : clamp(((x - x1) * dx + (y - y1) * dy) / lengthSquared);
        return length(x - x1 - t * dx, y - y1 - t * dy);
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
