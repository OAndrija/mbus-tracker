package com.mbus.app.ui.theme;

import com.badlogic.gdx.graphics.Color;

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
    public static final Color MAP_BACKGROUND = new Color();

    // The same in both versions
    public static final Color LOGO = Color.valueOf("9b1444");
    public static final Color SHADOW = Color.valueOf("0f172a");
    public static final Color ON_COLOR = Color.valueOf("ffffff");

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
        set(INK, "0f172a", "f1f5f9");
        set(TEXT, "334155", "cbd5e1");
        set(MUTED, "64748b", "94a3b8");
        set(FAINT, "94a3b8", "718096");
        set(BORDER, "e2e8f0", "2c384a");
        set(SUBTLE, "f1f5f9", "1f2a3a");
        set(SURFACE, "ffffff", "141d2b");
        set(QUIET, "cbd5e1", "475569");
        set(BRAND, "9b1444", "f2719a");
        set(BRAND_STRONG, "7d0f36", "f79bb8");
        set(BRAND_SOFT, "fbedf2", "381c2b");
        set(SELECTION, "f3c9d8", "5c2740");
        set(STAR, "e0a100", "f5b91f");
        set(NOW, "dc2626", "f87171");
        set(SOON, "c2570c", "fb923c");
        set(TOOLTIP, "0f172a", "33415a");
        set(MAP_BACKGROUND, "f2efe9", "141a23");
    }

    private static void set(Color color, String lightValue, String darkValue) {
        Color.valueOf(dark ? darkValue : lightValue, color);
    }
}
