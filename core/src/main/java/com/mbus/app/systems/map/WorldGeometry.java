package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.Geolocation;
import com.mbus.app.utils.ZoomXY;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** World positions of all stops and lines, projected once instead of on every frame. */
public class WorldGeometry {

    private final ZoomXY beginTile;
    private final Map<BusStop, Vector2> stops = new IdentityHashMap<>();
    private final Map<BusLine, LinePath> lines = new IdentityHashMap<>();

    public WorldGeometry(List<BusStop> busStops, List<BusLine> busLines, ZoomXY beginTile) {
        this.beginTile = beginTile;
        for (BusStop stop : busStops) {
            stops.put(stop, project(stop.geo));
        }
        for (BusLine line : busLines) {
            lines.put(line, new LinePath(line));
        }
    }

    public Vector2 project(Geolocation geo) {
        return MapRasterTiles.getPixelPosition(geo.lat, geo.lng, beginTile.x, beginTile.y);
    }

    /** Shared instance, do not modify. */
    public Vector2 getStop(BusStop stop) {
        return stops.get(stop);
    }

    public LinePath getLine(BusLine line) {
        return lines.get(line);
    }

    public class LinePath {
        public final float[] x;
        public final float[] y;
        public final Rectangle bounds = new Rectangle();

        LinePath(BusLine line) {
            List<Geolocation> path = line.getPath();
            x = new float[path.size()];
            y = new float[path.size()];
            for (int i = 0; i < x.length; i++) {
                Vector2 point = project(path.get(i));
                x[i] = point.x;
                y[i] = point.y;
                if (i == 0) {
                    bounds.set(point.x, point.y, 0, 0);
                } else {
                    bounds.merge(point);
                }
            }
        }

        public int size() {
            return x.length;
        }
    }
}
