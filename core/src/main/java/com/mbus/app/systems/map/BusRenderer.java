package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Trip;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the buses. Zoomed out, a bus is a disc with a pointer in the driving direction; zoomed in, it
 * is a bus seen from above. Both carry the line number, which always stays upright.
 */
public class BusRenderer {

    // Logical screen pixels
    private static final float DISC_SIZE_FAR = 22f;
    private static final float DISC_SIZE_NEAR = 28f;
    private static final float BUS_LENGTH = 64f;
    private static final float NUMBER_CAP_HEIGHT = 0.42f;   // of the disc
    private static final float NUMBER_MAX_WIDTH = 0.8f;    // of the disc

    /** Discs turn into buses below the first camera zoom, and back above the second. */
    private static final float BUS_ZOOM = 0.095f;
    private static final float DISC_ZOOM = 0.105f;
    private static final float MORPH_SECONDS = 0.18f;

    private static final float HIT_RADIUS = 15f;
    private static final float TURN_SPEED = 10f;
    private static final float MAX_DEGREES_PER_SECOND = 240f;

    private final SpriteBatch spriteBatch;
    private Theme theme;
    private LineLayer lineLayer;
    private LineStyle lineStyle;
    private final Vector2 offset = new Vector2();
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tint = new Color();
    private final Vector2 front = new Vector2();
    private final Vector2 rear = new Vector2();

    private final Map<RoutePattern, PatternMotion> motions = new IdentityHashMap<>();
    private final Map<Trip, Bus> buses = new IdentityHashMap<>();
    private WorldGeometry geometry;
    private long frame = 0;
    private boolean showBuses = false;
    private float morph = 0f;
    private float time = 0f;
    private Trip highlighted;
    private Trip hovered;

    private static class Bus {
        final Vector2 position = new Vector2();
        float angle;
        float alpha;
        long lastFrame;
    }

    public BusRenderer(SpriteBatch spriteBatch, Theme theme) {
        this.spriteBatch = spriteBatch;
        this.theme = theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public void setGeometry(WorldGeometry geometry, LineLayer lineLayer, LineStyle lineStyle) {
        this.geometry = geometry;
        this.lineLayer = lineLayer;
        this.lineStyle = lineStyle;
        motions.clear();
        buses.clear();
    }

    /** A bus that is shown even if its line is hidden, with a halo. */
    public void setHighlighted(Trip trip) {
        this.highlighted = trip;
    }

    public void setHovered(Trip trip) {
        this.hovered = trip;
    }

    /** Where the bus of a trip is, or null if it is not on the road. Shared instance, do not modify. */
    public Vector2 getPosition(Trip trip) {
        Bus bus = buses.get(trip);
        return bus == null ? null : bus.position;
    }

    /** The trip of the bus at a world position, or null. */
    public Trip findBus(float x, float y, float worldPerPixel) {
        float closest = HIT_RADIUS * worldPerPixel;
        closest *= closest;
        Trip found = null;
        for (Map.Entry<Trip, Bus> entry : buses.entrySet()) {
            Bus bus = entry.getValue();
            if (bus.alpha < 0.3f) continue;
            // A bus seen from above is longer than wide.
            float dx = x - bus.position.x;
            float dy = y - bus.position.y;
            float cos = MathUtils.cosDeg(bus.angle);
            float sin = MathUtils.sinDeg(bus.angle);
            float along = dx * cos + dy * sin;
            float across = dy * cos - dx * sin;
            float stretch = showBuses ? BUS_LENGTH / (2f * HIT_RADIUS) : 1f;
            float distance = along * along / (stretch * stretch) + across * across;
            if (distance < closest) {
                closest = distance;
                found = entry.getKey();
            }
        }
        return found;
    }

    /** Draws the buses of some lines out of a day's trips. Expects the batch to be set up for world coordinates, but not begun. */
    public void render(List<Trip> trips, Collection<BusLine> lines, double time, Rectangle view,
                       float cameraZoom, float worldPerPixel, float closeness, float delta) {
        if (geometry == null) return;

        frame++;
        this.time += delta;
        // Takes time rather than following the zoom, so that buses never rest half-way
        if (cameraZoom < BUS_ZOOM) showBuses = true;
        else if (cameraZoom > DISC_ZOOM) showBuses = false;
        float step = delta / MORPH_SECONDS;
        morph = MathUtils.clamp(morph + (showBuses ? step : -step), 0f, 1f);
        float busShare = Interpolation.smooth.apply(morph);
        float disc = MathUtils.lerp(DISC_SIZE_FAR, DISC_SIZE_NEAR, closeness) * worldPerPixel;
        float length = BUS_LENGTH * worldPerPixel;

        spriteBatch.begin();
        for (Trip trip : trips) {
            if (trip.getStart() > time) break; // ordered by start
            if (!lines.contains(trip.pattern.line)) continue;
            if (trip == highlighted || trip == hovered) continue; // on top of the others
            renderIfRunning(trip, time, view, disc, length, busShare, delta);
        }
        boolean hoveredIsShown = hovered != null && hovered != highlighted
            && trips.contains(hovered) && lines.contains(hovered.pattern.line);
        if (hoveredIsShown) renderIfRunning(hovered, time, view, disc, length, busShare, delta);
        if (highlighted != null) renderIfRunning(highlighted, time, view, disc, length, busShare, delta);
        spriteBatch.setColor(Color.WHITE);
        theme.map.getData().setScale(1f);
        spriteBatch.end();

        // Finished trips and lines that are no longer shown
        buses.values().removeIf(bus -> bus.lastFrame != frame);
    }

    private void renderIfRunning(Trip trip, double time, Rectangle view,
                                 float disc, float length, float busShare, float delta) {
        PatternMotion motion = motionOf(trip.pattern);
        if (motion.isRunning(trip, time)) {
            renderBus(trip, motion, time, view, disc, length, busShare, delta);
        }
    }

    PatternMotion motionOf(RoutePattern pattern) {
        PatternMotion motion = motions.get(pattern);
        if (motion == null) {
            WorldGeometry.Path path = geometry.getPattern(pattern);
            motion = new PatternMotion(pattern, new RouteTrack(path.x, path.y), geometry);
            motions.put(pattern, motion);
        }
        return motion;
    }

    private void renderBus(Trip trip, PatternMotion motion, double time, Rectangle view,
                           float disc, float length, float busShare, float delta) {
        // Front and rear both follow the road, which makes the bus turn like a real one.
        double along = motion.distanceAt(trip, time);
        motion.getTrack().pointAt(along + length / 2f, front);
        motion.getTrack().pointAt(along - length / 2f, rear);
        Strand strand = lineLayer.getStrand(trip.pattern);
        if (strand != null) {
            front.add(strand.offsetAt(along + length / 2f, lineStyle, offset));
            rear.add(strand.offsetAt(along - length / 2f, lineStyle, offset));
        }
        float x = (front.x + rear.x) / 2f;
        float y = (front.y + rear.y) / 2f;
        float heading = MathUtils.atan2(front.y - rear.y, front.x - rear.x) * MathUtils.radiansToDegrees;

        Bus bus = buses.get(trip);
        if (bus == null) {
            bus = new Bus();
            bus.angle = heading;
            buses.put(trip, bus);
        } else if (front.dst2(rear) > 1f) { // at a turning point both ends meet
            float step = turn(bus.angle, heading) * (1f - (float) Math.exp(-TURN_SPEED * delta));
            float limit = MAX_DEGREES_PER_SECOND * delta;
            bus.angle += MathUtils.clamp(step, -limit, limit);
        }
        bus.lastFrame = frame;
        bus.position.set(x, y);
        bus.alpha = motion.alphaAt(trip, time);

        if (x < view.x - length || x > view.x + view.width + length
            || y < view.y - length || y > view.y + view.height + length) return;

        BusLine line = trip.pattern.line;
        Color color = Palette.line(line);
        float alpha = bus.alpha;
        if (trip == highlighted || trip == hovered) {
            float pulse = trip == highlighted ? 0.5f + 0.5f * MathUtils.sin(this.time * 3f) : 0f;
            float size = (busShare > 0.5f ? length * 1.15f : disc * 1.9f) * (1f + 0.08f * pulse) / Theme.HALO_SCALE;
            draw(theme.halo, color, alpha * (0.32f + 0.1f * pulse), x, y, size, size, 0f);
        }
        if (busShare < 1f) {
            float size = disc / Theme.BUS_DISC_SCALE;
            draw(theme.busDiscBase, Color.WHITE, alpha * (1f - busShare), x, y, size, size, bus.angle);
            draw(theme.busDiscFill, color, alpha * (1f - busShare), x, y, size, size, bus.angle);
        }
        if (busShare > 0f) {
            float width = length / Theme.BUS_BODY_SCALE;
            float height = width * theme.busBodyBase.getHeight() / theme.busBodyBase.getWidth();
            draw(theme.busBodyBase, Color.WHITE, alpha * busShare, x, y, width, height, bus.angle);
            draw(theme.busBodyFill, color, alpha * busShare, x, y, width, height, bus.angle);
            draw(theme.busBodyDetail, Color.WHITE, alpha * busShare, x, y, width, height, bus.angle);
        }
        drawNumber(line.name, Palette.onLine(line), x, y, disc, alpha);
    }

    /** The shortest way from one angle to another, in degrees. */
    static float turn(float from, float to) {
        float difference = (to - from) % 360f;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return difference;
    }

    private void draw(Texture texture, Color color, float alpha, float x, float y,
                      float width, float height, float angle) {
        spriteBatch.setColor(color.r, color.g, color.b, alpha);
        spriteBatch.draw(texture, x - width / 2f, y - height / 2f, width / 2f, height / 2f,
            width, height, 1f, 1f, angle,
            0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    private void drawNumber(String number, Color color, float x, float y, float disc, float alpha) {
        BitmapFont font = theme.map;
        font.getData().setScale(1f);
        layout.setText(font, number);
        font.getData().setScale(Math.min(
            disc * NUMBER_MAX_WIDTH / layout.width, disc * NUMBER_CAP_HEIGHT / font.getCapHeight()));
        tint.set(color).a = alpha;
        layout.setText(font, number, tint, 0, Align.left, false);
        font.draw(spriteBatch, layout, x - layout.width / 2f, y + font.getCapHeight() / 2f);
    }
}
