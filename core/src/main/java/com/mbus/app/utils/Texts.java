package com.mbus.app.utils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;

import java.time.LocalDate;
import java.util.Locale;

/** The texts of the interface in the chosen language. */
public final class Texts {

    private static I18NBundle bundle;
    private static String language = "";

    private Texts() {
    }

    /** Uses the language with a code, or the one of the computer if it is empty. Slovene and English are available. */
    public static void load(FileHandle base, String code) {
        String wanted = code.isEmpty() ? Locale.getDefault().getLanguage() : code;
        language = wanted.equals("sl") ? "sl" : "en";
        bundle = I18NBundle.createBundle(base, new Locale(language), "UTF-8");
    }

    public static String getLanguage() {
        return language;
    }

    public static String getOtherLanguage() {
        return language.equals("sl") ? "en" : "sl";
    }

    public static String get(String key) {
        return bundle.get(key);
    }

    /** Fills in the arguments, which are used as they are. */
    public static String format(String key, Object... arguments) {
        Object[] strings = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) strings[i] = String.valueOf(arguments[i]);
        return bundle.format(key, strings);
    }

    /** The word for a number of things. Slovene has a singular, a dual and two plurals. */
    public static String plural(String key, int count) {
        if (!language.equals("sl")) return get(key + (count == 1 ? "One" : "Many"));
        switch (count % 100) {
            case 1: return get(key + "One");
            case 2: return get(key + "Two");
            case 3:
            case 4: return get(key + "Few");
            default: return get(key + "Many");
        }
    }

    public static String buses(int count) {
        return plural("bus", count);
    }

    /** Such as "31 Dec 2026". */
    public static String date(LocalDate date) {
        return formatDate("fullDate", date);
    }

    /** Such as "Today", "Tomorrow" or "Sat, 28 Nov". */
    public static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) return get("today");
        if (date.equals(today.plusDays(1))) return get("tomorrow");
        return formatDate("shortDate", date);
    }

    private static String formatDate(String key, LocalDate date) {
        return format(key,
            get("weekdays").split(",")[date.getDayOfWeek().getValue() - 1],
            date.getDayOfMonth(), date.getMonthValue(),
            get("months").split(",")[date.getMonthValue() - 1], date.getYear());
    }
}
