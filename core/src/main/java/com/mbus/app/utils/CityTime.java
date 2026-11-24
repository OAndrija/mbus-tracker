package com.mbus.app.utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date and time in Maribor, whatever the time zone of the machine. */
public final class CityTime {

    private static final ZoneId ZONE = ZoneId.of("Europe/Ljubljana");

    private CityTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Whole minutes since midnight. */
    public static int minutes() {
        return (int) preciseMinutes();
    }

    public static double preciseMinutes() {
        return LocalTime.now(ZONE).toNanoOfDay() / 60e9;
    }

    /** Formats minutes since midnight, which may be past the end of the day. */
    public static String format(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60);
    }

    public static String format(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("d. M. yyyy", Locale.ROOT));
    }
}
