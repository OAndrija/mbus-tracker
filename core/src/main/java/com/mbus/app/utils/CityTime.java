package com.mbus.app.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** Time in Maribor, whatever the time zone of the machine, and how it is written. */
public final class CityTime {

    private static final ZoneId ZONE = ZoneId.of("Europe/Ljubljana");

    private CityTime() {
    }

    /** Minutes since the start of 1970, local time. */
    public static double totalMinutes() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return now.toLocalDate().toEpochDay() * 1440.0 + now.toLocalTime().toNanoOfDay() / 60e9;
    }

    /** Formats minutes since midnight, which may be past the end of the day. */
    public static String format(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60);
    }
}
