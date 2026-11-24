package com.mbus.app.systems.journey;

import com.badlogic.gdx.Gdx;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs the planner in the background. Only the latest search is answered. */
public class JourneySearch {

    /** Only used in the background. */
    private JourneyPlanner planner;
    private final ExecutorService executor;
    private volatile int latest = 0;

    /** Makes the planner in the background, as that takes a moment. */
    public JourneySearch(Supplier<JourneyPlanner> createPlanner) {
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "journey-search");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(() -> planner = createPlanner.get());
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
