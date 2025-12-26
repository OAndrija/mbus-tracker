package com.mbus.app.utils;

import com.badlogic.gdx.graphics.Color;
import java.util.HashMap;
import java.util.Map;

public class BusLineColors {

    private static final Map<Integer, Color> lineColors = new HashMap<Integer, Color>();

    static {
        // Define colors for each bus line ID
        // You can adjust these or add more as needed
        lineColors.put(1, new Color(0.2f, 0.6f, 1f, 0.7f));      // Light Blue
        lineColors.put(2, new Color(0.4f, 0.8f, 0.4f, 0.7f));     // Green
        lineColors.put(3, new Color(1f, 0.5f, 0.8f, 0.7f));       // Pink
        lineColors.put(4, new Color(0.6f, 0.3f, 0.9f, 0.7f));     // Purple
        lineColors.put(6, new Color(1f, 0.7f, 0.2f, 0.7f));       // Gold
        lineColors.put(7, new Color(0.3f, 0.9f, 0.9f, 0.7f));     // Cyan
        lineColors.put(8, new Color(1f, 0.4f, 0.4f, 0.7f));       // Red
        lineColors.put(9, new Color(1f, 0.6f, 0.2f, 0.7f));       // Orange
        lineColors.put(10, new Color(0.5f, 0.7f, 0.3f, 0.7f));    // Lime
        lineColors.put(12, new Color(0.2f, 0.5f, 0.8f, 0.7f));    // Blue
        lineColors.put(13, new Color(0.8f, 0.2f, 0.6f, 0.7f));    // Magenta
        lineColors.put(15, new Color(0.9f, 0.5f, 0.3f, 0.7f));    // Coral
        lineColors.put(16, new Color(0.4f, 0.6f, 0.8f, 0.7f));    // Steel Blue
        lineColors.put(17, new Color(0.7f, 0.4f, 0.7f, 0.7f));    // Lavender
        lineColors.put(18, new Color(0.3f, 0.8f, 0.5f, 0.7f));    // Sea Green
        lineColors.put(19, new Color(1f, 0.7f, 0.4f, 0.7f));      // Peach
        lineColors.put(20, new Color(0.5f, 0.4f, 0.9f, 0.7f));    // Indigo
        lineColors.put(21, new Color(0.9f, 0.3f, 0.4f, 0.7f));    // Crimson
        lineColors.put(151, new Color(0.6f, 0.6f, 0.2f, 0.7f));   // Olive
    }

    /**
     * Get the color for a specific bus line
     */
    public static Color getColor(int lineId) {
        Color color = lineColors.get(lineId);
        if (color == null) {
            // Default color if line ID not found
            return new Color(0.5f, 0.5f, 0.5f, 0.7f); // Gray
        }
        return new Color(color); // Return a copy to avoid modification
    }

    /**
     * Get the color for a specific bus line with custom alpha
     */
    public static Color getColor(int lineId, float alpha) {
        Color color = getColor(lineId);
        color.a = alpha;
        return color;
    }

    /**
     * Get a brighter version of the line color (for UI buttons)
     */
    public static Color getButtonColor(int lineId) {
        Color color = getColor(lineId);
        // Make it more opaque and slightly brighter for UI
        return new Color(
            Math.min(color.r * 1.2f, 1f),
            Math.min(color.g * 1.2f, 1f),
            Math.min(color.b * 1.2f, 1f),
            1f
        );
    }
}
