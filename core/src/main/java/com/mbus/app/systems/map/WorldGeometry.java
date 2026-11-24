package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.model.Timetable;
import com.mbus.app.utils.Geolocation;
import com.mbus.app.utils.ZoomXY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** World positions of all stops and routes, projected once instead of on every frame. */
public class WorldGeometry {

    private final ZoomXY beginTile;
    private final Map<BusStop, Vector2> stops = new IdentityHashMap<>();
    private final Map<RoutePattern, Path> patterns = new IdentityHashMap<>();
    private final Map<BusLine, List<Path>> lines = new IdentityHashMap<>();

    public WorldGeometry(Timetable timetable, ZoomXY beginTile) {
        this.beginTile = beginTile;
        for (BusStop stop : timetable.getStops()) {
            stops.put(stop, project(stop.geo));
        }
        for (BusLine line : timetable.getLines()) {
            List<Path> distinct = new ArrayList<>();
            for (RoutePattern pattern : line.getPatterns()) {
                Path path = new Path(pattern.getPath());
                patterns.put(pattern, path);
                boolean known = false;
                for (Path other : distinct) known |= path.sameAs(other);
                if (!known) distinct.add(path);
            }
            lines.put(line, Collections.unmodifiableList(distinct));
        }
    }

    private Vector2 project(Geolocation geo) {
        return MapRasterTiles.getPixelPosition(geo.lat, geo.lng, beginTile.x, beginTile.y);
    }

    /** Shared instance, do not modify. */
    public Vector2 getStop(BusStop stop) {
        return stops.get(stop);
    }

    public Path getPattern(RoutePattern pattern) {
        return patterns.get(pattern);
    }

    /** The different roads a line uses, over all its directions and variants. */
    public List<Path> getLine(BusLine line) {
        return lines.get(line);
    }

    public class Path {
        public final float[] x;
        public final float[] y;
        public final Rectangle bounds = new Rectangle();

        Path(List<Geolocation> points) {
            x = new float[points.size()];
            y = new float[points.size()];
            for (int i = 0; i < x.length; i++) {
                Vector2 point = project(points.get(i));
                x[i] = point.x;
                y[i] = point.y;
                if (i == 0) {
                    bounds.set(point.x, point.y, 0, 0);
                } else {
                    bounds.merge(point);
                }
            }
        }

        boolean sameAs(Path other) {
            return Arrays.equals(x, other.x) && Arrays.equals(y, other.y);
        }

        public int size() {
            return x.length;
        }
    }
}
