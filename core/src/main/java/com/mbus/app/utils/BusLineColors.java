package com.mbus.app.utils;

import com.badlogic.gdx.graphics.Color;
import java.util.HashMap;
import java.util.Map;

/** One color per line, all dark enough for white text. Do not modify the returned instances. */
public class BusLineColors {

    private static final Color DEFAULT = Color.valueOf("5f6b7a");
    private static final Map<Integer, Color> lineColors = new HashMap<>();

    static {
        lineColors.put(1, Color.valueOf("1a7fd6"));     // Blue
        lineColors.put(2, Color.valueOf("2f9445"));     // Green
        lineColors.put(3, Color.valueOf("d94f92"));     // Pink
        lineColors.put(4, Color.valueOf("7c3fc4"));     // Purple
        lineColors.put(6, Color.valueOf("b27d00"));     // Gold
        lineColors.put(7, Color.valueOf("0891a3"));     // Cyan
        lineColors.put(8, Color.valueOf("dd3b36"));     // Red
        lineColors.put(9, Color.valueOf("dd6b0a"));     // Orange
        lineColors.put(10, Color.valueOf("648f1c"));    // Lime
        lineColors.put(12, Color.valueOf("1652a8"));    // Dark blue
        lineColors.put(13, Color.valueOf("b81a75"));    // Magenta
        lineColors.put(15, Color.valueOf("c9532c"));    // Coral
        lineColors.put(16, Color.valueOf("4a7aa5"));    // Steel blue
        lineColors.put(17, Color.valueOf("974c9f"));    // Lavender
        lineColors.put(18, Color.valueOf("1b9268"));    // Sea green
        lineColors.put(19, Color.valueOf("a8763a"));    // Bronze
        lineColors.put(20, Color.valueOf("5a50d0"));    // Indigo
        lineColors.put(21, Color.valueOf("b92545"));    // Crimson
        lineColors.put(151, Color.valueOf("7a771c"));   // Olive
        lineColors.put(4122, DEFAULT);                  // Slate
    }

    public static Color getColor(int lineId) {
        Color color = lineColors.get(lineId);
        return color != null ? color : DEFAULT;
    }
}
