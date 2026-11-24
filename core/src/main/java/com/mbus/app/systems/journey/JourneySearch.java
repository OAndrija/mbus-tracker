package com.mbus.app.systems.journey;

import com.badlogic.gdx.Gdx;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs the planner in the background. Only the latest search is answered. */
public class JourneySearch {

    private final JourneyPlanner planner;
    private final ExecutorService executor;
    private volatile int latest = 0;

    public JourneySearch(JourneyPlanner planner) {
        this.planner = planner;
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "journey-search");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Answers on the main thread. */
    public void search(Place from, Place to, boolean arriveBy, LocalDate date, int time, Consumer<List<Journey>> answer) {
        final int request = ++latest;
        executor.execute(() -> {
            if (request != latest) return;
            List<Journey> found = arriveBy
                ? planner.arriveBy(from, to, date, time) : planner.leaveAt(from, to, date, time);
            Gdx.app.postRunnable(() -> {
                if (request == latest) answer.accept(found);
            });
        });
    }

    public void cancel() {
        latest++;
    }

    public void dispose() {
        cancel();
        executor.shutdownNow();
    }
}
