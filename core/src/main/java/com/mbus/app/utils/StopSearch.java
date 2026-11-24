package com.mbus.app.utils;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;

import java.text.Normalizer;
import java.util.Locale;

/** Matches bus stops against what the user types, ignoring case and accents ("zolgarjeva" finds "Žolgarjeva"). */
public final class StopSearch {

    private StopSearch() {
    }

    /** True if every word of the query occurs in the stop's name, starts its number or is a line that stops there. */
    public static boolean matches(BusStop stop, String query) {
        String name = normalize(stop.name);
        String id = stop.id;

        for (String word : normalize(query).trim().split("\\s+")) {
            if (!name.contains(word) && !id.startsWith(word) && !isServedBy(stop, word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isServedBy(BusStop stop, String lineName) {
        for (BusLine line : stop.getLines()) {
            if (line.name.equalsIgnoreCase(lineName)) return true;
        }
        return false;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    }
}
