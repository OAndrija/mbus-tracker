package com.mbus.app.model;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A bus line such as G6, with all its directions and variants. Ordered by name: G1 … G6, P7 … P19. */
public class BusLine implements Comparable<BusLine> {

    private static final Color LIGHT_TEXT = Color.valueOf("ffffff");
    private static final Color DARK_TEXT = Color.valueOf("0f172a");
    private static final float DARK_TEXT_LUMINANCE = 0.3f;
    private static final float LIGHT_LUMINANCE = 0.42f;
    private static final float DARK_LUMINANCE = 0.07f;

    public final String id;
    public final String name;
    public final String description;
    /** Do not modify. */
    public final Color color;
    /** Readable on {@link #color}. Do not modify. */
    public final Color textColor;

    private final List<RoutePattern> patterns = new ArrayList<>();

    public BusLine(String id, String name, String description, Color color) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.color = color;
        this.textColor = luminance() > DARK_TEXT_LUMINANCE ? DARK_TEXT : LIGHT_TEXT;
    }

    /** True if the color needs an outline on a light map. */
    public boolean isLight() {
        return luminance() > LIGHT_LUMINANCE;
    }

    /** True if the color needs an outline on a dark map. */
    public boolean isDark() {
        return luminance() < DARK_LUMINANCE;
    }

    private float luminance() {
        return 0.2126f * linear(color.r) + 0.7152f * linear(color.g) + 0.0722f * linear(color.b);
    }

    private static float linear(float channel) {
        return channel <= 0.04045f ? channel / 12.92f : (float) Math.pow((channel + 0.055f) / 1.055f, 2.4);
    }

    public List<RoutePattern> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    void addPattern(RoutePattern pattern) {
        patterns.add(pattern);
    }

    @Override
    public int compareTo(BusLine other) {
        int byLetters = letters().compareTo(other.letters());
        if (byLetters != 0) return byLetters;
        int byNumber = Integer.compare(number(), other.number());
        return byNumber != 0 ? byNumber : name.compareTo(other.name);
    }

    private String letters() {
        return name.replaceAll("\\d", "");
    }

    private int number() {
        String digits = name.replaceAll("\\D", "");
        return digits.isEmpty() || digits.length() > 9 ? 0 : Integer.parseInt(digits);
    }

    @Override
    public String toString() {
        return name;
    }
}
