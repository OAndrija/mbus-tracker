package com.mbus.app.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** The days a group of trips runs on. */
public class Service {

    private final boolean[] weekdays; // Monday first
    private final LocalDate start;
    private final LocalDate end;
    private final Set<LocalDate> added = new HashSet<>();
    private final Set<LocalDate> removed = new HashSet<>();

    public Service(boolean[] weekdays, LocalDate start, LocalDate end) {
        this.weekdays = weekdays.clone();
        this.start = start;
        this.end = end;
    }

    /** A service without regular days, made up of exceptions only. */
    public Service() {
        this(new boolean[7], LocalDate.MAX, LocalDate.MIN);
    }

    public void addException(LocalDate date, boolean runs) {
        (runs ? added : removed).add(date);
    }

    public boolean runsOn(LocalDate date) {
        if (removed.contains(date)) return false;
        if (added.contains(date)) return true;
        return !date.isBefore(start) && !date.isAfter(end)
            && weekdays[date.getDayOfWeek().getValue() - 1];
    }

    public LocalDate getEnd() {
        return end;
    }
}
