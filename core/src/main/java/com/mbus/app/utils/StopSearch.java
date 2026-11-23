package com.mbus.app.utils;

import com.mbus.app.model.BusStop;

import java.text.Normalizer;
import java.util.Locale;

/** Matches bus stops against what the user types, ignoring case and accents ("zolgarjeva" finds "Žolgarjeva"). */
public final class StopSearch {

    private StopSearch() {
    }

    /** True if every word of the query occurs in the stop's name or is the start of its number. */
    public static boolean matches(BusStop stop, String query) {
        String name = normalize(stop.name);
        String id = String.valueOf(stop.idAvpost);

        for (String word : normalize(query).trim().split("\\s+")) {
            if (!name.contains(word) && !id.startsWith(word)) {
                return false;
            }
        }
        return true;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    }
}
