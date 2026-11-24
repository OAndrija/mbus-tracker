package com.mbus.app.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.mbus.app.model.BusLine;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The colors of the interface, in a light and a dark version. Do not modify these instances. After
 * switching, a new {@link Theme} and new widgets are needed, as shapes are drawn in these colors.
 */
public final class Palette {

    public static final Color INK = new Color();
    public static final Color TEXT = new Color();
    public static final Color MUTED = new Color();
    public static final Color FAINT = new Color();
    public static final Color BORDER = new Color();
    public static final Color SUBTLE = new Color();
    public static final Color SURFACE = new Color();
    /** Things to notice but not to read, such as empty stars and scrollbars. */
    public static final Color QUIET = new Color();

    public static final Color BRAND = new Color();
    public static final Color BRAND_STRONG = new Color();
    public static final Color BRAND_SOFT = new Color();
    public static final Color SELECTION = new Color();

    public static final Color STAR = new Color();
    public static final Color NOW = new Color();
    public static final Color SOON = new Color();

    public static final Color TOOLTIP = new Color();
    public static final Color SHADOW = new Color();
    public static final Color MAP_BACKGROUND = new Color();

    // The same in both versions
    public static final Color LOGO = Color.valueOf("9b1444");
    public static final Color ON_COLOR = Color.valueOf("ffffff");
    public static final Color ON_LIGHT = Color.valueOf("16181d");

    /** How much of their color lines lose, and how bright they stay, in the dark. */
    private static final float DARK_GRAYNESS = 0.3f;
    private static final float DARK_BRIGHTNESS = 0.86f;
    private static final float DARK_TEXT_LUMINANCE = 0.3f;

    private static final Map<BusLine, Color> lines = new IdentityHashMap<>();
    private static boolean dark;

    static {
        setDark(false);
    }

    private Palette() {
    }

    public static boolean isDark() {
        return dark;
    }

    public static void setDark(boolean useDark) {
        dark = useDark;
        lines.clear();
        set(INK, "0f172a", "ececee");
        set(TEXT, "334155", "c8c8cb");
        set(MUTED, "64748b", "9b9b9f");
        set(FAINT, "94a3b8", "76767a");
        set(BORDER, "e2e8f0", "38383b");
        set(SUBTLE, "f1f5f9", "2a2a2c");
        set(SURFACE, "ffffff", "1f1f21");
        set(QUIET, "cbd5e1", "555559");
        set(BRAND, "9b1444", "dc86a2");
        set(BRAND_STRONG, "7d0f36", "e7a3b9");
        set(BRAND_SOFT, "fbedf2", "33272b");
        set(SELECTION, "f3c9d8", "54404a");
        set(STAR, "e0a100", "d6ab3f");
        set(NOW, "dc2626", "e08080");
        set(SOON, "c2570c", "d9a066");
        set(SHADOW, "0f172a", "000000");
        set(TOOLTIP, "0f172a", "3c3c40");
        set(MAP_BACKGROUND, "f2efe9", "1a1a1b");
    }

    /** The color of a line, a little calmer in the dark. */
    public static Color line(BusLine line) {
        return lines.computeIfAbsent(line, l -> dark ? calm(l.color) : l.color);
    }

    private static Color calm(Color color) {
        float gray = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
        Color result = new Color(color).lerp(gray, gray, gray, 1f, DARK_GRAYNESS);
        result.mul(DARK_BRIGHTNESS).a = 1f;
        return result;
    }

    /** Readable on the color of a line. */
    public static Color onLine(BusLine line) {
        return BusLine.luminance(line(line)) > DARK_TEXT_LUMINANCE ? ON_LIGHT : ON_COLOR;
    }

    private static void set(Color color, String lightValue, String darkValue) {
        Color.valueOf(dark ? darkValue : lightValue, color);
    }
}
