package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LineLayoutTest {

    private final LineStyle style = new LineStyle();

    public LineLayoutTest() {
        style.set(1f, 0f);
    }

    private static LineLayout.Road road(int rank, float... points) {
        float[] x = new float[points.length / 2];
        float[] y = new float[points.length / 2];
        for (int i = 0; i < x.length; i++) {
            x[i] = points[2 * i];
            y[i] = points[2 * i + 1];
        }
        return new LineLayout.Road(rank, x, y);
    }

    /** The y a horizontal strand is drawn at, at an x. */
    private float drawnY(Strand strand, float x) {
        int nearest = 0;
        for (int i = 0; i < strand.size(); i++) {
            if (Math.abs(strand.x[i] - x) < Math.abs(strand.x[nearest] - x)) nearest = i;
        }
        return strand.drawnY(nearest, style);
    }

    @Test
    public void aLineOnItsOwnStaysOnItsRoad() {
        Strand strand = LineLayout.build(Collections.singletonList(road(0, 0, 0, 400, 0, 400, 300))).get(0);
        for (int i = 0; i < strand.size(); i++) {
            assertEquals(0f, strand.lane[i], 0f);
            assertEquals(1f, strand.laneCount[i], 0f);
        }
    }

    @Test
    public void linesOnTheSameStreetLieSideBySide() {
        List<Strand> strands = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 600, 0), road(1, 0, 0, 600, 0), road(2, 0, 0, 600, 0)));
        float first = drawnY(strands.get(0), 300);
        float second = drawnY(strands.get(1), 300);
        float third = drawnY(strands.get(2), 300);
        assertEquals(0f, second, 1e-3f);
        assertEquals(style.gap, first - second, 1e-3f);
        assertEquals(style.gap, second - third, 1e-3f);
    }

    @Test
    public void theLaneDoesNotDependOnTheDrivingDirection() {
        List<Strand> strands = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 600, 0), road(1, 0, 0, 600, 0), road(1, 600, 0, 0, 0)));
        assertEquals(drawnY(strands.get(1), 300), drawnY(strands.get(2), 300), 1e-3f);
    }

    @Test
    public void routesThatDifferALittleAreTheSameStreet() {
        List<Strand> strands = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 600, 0), road(1, 600, 5, 300, 6, 0, 5)));
        // Smoothing cuts the corner of the second route at 300 a little.
        assertEquals(style.gap, drawnY(strands.get(0), 300) - drawnY(strands.get(1), 300), 0.1f);
    }

    @Test
    public void crossingAndDistantRoutesAreNot() {
        List<Strand> strands = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 600, 0), road(1, 300, -300, 300, 300), road(2, 0, 40, 600, 40)));
        for (Strand strand : strands) {
            for (int i = 0; i < strand.size(); i++) assertEquals(1f, strand.laneCount[i], 0f);
        }
    }

    @Test
    public void lanesChangeGraduallyWhereALineLeaves() {
        Strand stays = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 1000, 0), road(1, 0, 0, 500, 0, 500, 500))).get(0);
        for (int i = 1; i < stays.size(); i++) {
            assertTrue(Math.abs(stays.lane[i] - stays.lane[i - 1]) < 0.3f);
        }
        assertEquals(0.5f, Math.abs(stays.lane[stays.size() / 4]), 1e-3f);
        assertEquals(0f, stays.lane[stays.size() - 1], 1e-3f);
    }

    @Test
    public void manyLinesStayWithinTheBand() {
        List<LineLayout.Road> roads = new ArrayList<>();
        for (int rank = 0; rank < 11; rank++) roads.add(road(rank, 0, 0, 600, 0));
        List<Strand> strands = LineLayout.build(roads);
        assertTrue(drawnY(strands.get(0), 300) - drawnY(strands.get(10), 300) <= style.maxBand);
    }

    @Test
    public void busesFollowTheLane() {
        Strand strand = LineLayout.build(Arrays.asList(
            road(0, 0, 0, 600, 0), road(1, 0, 4, 600, 4))).get(1);
        Vector2 offset = strand.offsetAt(300, style, new Vector2());
        assertEquals(drawnY(strand, 300), 4 + offset.y, 0.05f);
    }
}
