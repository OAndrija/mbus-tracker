package com.mbus.app.utils;

import java.util.Calendar;
import java.util.TimeZone;

public class BusPositionCalculator {

    private static final TimeZone CITY_TIME_ZONE = TimeZone.getTimeZone("Europe/Ljubljana");

    private static Calendar cityCalendar() {
        return Calendar.getInstance(CITY_TIME_ZONE);
    }

    public static int getCurrentTimeMinutes() {
        Calendar cal = cityCalendar();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    public static double getCurrentTimeMinutesPrecise() {
        Calendar cal = cityCalendar();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            + cal.get(Calendar.SECOND) / 60.0 + cal.get(Calendar.MILLISECOND) / 60000.0;
    }

    public static int getCurrentDayType() {
        Calendar cal = cityCalendar();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        if (dayOfWeek == Calendar.SUNDAY) {
            return 2;
        } else if (dayOfWeek == Calendar.SATURDAY) {
            return 1;
        } else {
            return 0;
        }
    }

    public static String formatTime(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }
}
