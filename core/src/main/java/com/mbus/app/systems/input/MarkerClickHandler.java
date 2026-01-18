package com.mbus.app.systems.input;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.utils.Constants;

import java.util.List;

public class MarkerClickHandler {

    private static final float BASE_MARKER_SIZE = 32f;
    private static final float CLICK_TOLERANCE = 1.2f;

    private OrthographicCamera camera;
    private List<BusStop> stops;
    private ZoomXY beginTile;

    public MarkerClickHandler(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setStops(List<BusStop> stops) {
        this.stops = stops;
    }

    public void setBeginTile(ZoomXY beginTile) {
        this.beginTile = beginTile;
    }

    public BusStop checkMarkerClick(int screenX, int screenY) {
        if (stops == null || stops.isEmpty() || beginTile == null) {
            return null;
        }

        Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
        float worldX = worldCoords.x;
        float worldY = worldCoords.y;

        float clickRadius = (BASE_MARKER_SIZE / 2f) * CLICK_TOLERANCE;

        BusStop closestStop = null;
        float closestDistance = Float.MAX_VALUE;

        for (BusStop stop : stops) {
            Vector2 markerPos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (markerPos.x < 0 || markerPos.y < 0 ||
                markerPos.x > Constants.MAP_WIDTH || markerPos.y > Constants.MAP_HEIGHT) {
                continue;
            }

            float dx = worldX - markerPos.x;
            float dy = worldY - markerPos.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= clickRadius && distance < closestDistance) {
                closestStop = stop;
                closestDistance = distance;
            }
        }

        return closestStop;
    }
}
