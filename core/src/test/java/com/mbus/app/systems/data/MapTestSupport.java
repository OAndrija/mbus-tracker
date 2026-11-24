package com.mbus.app.systems.data;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.Gdx;
import com.mbus.app.systems.map.MapRasterTiles;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.ZoomXY;

public final class MapTestSupport {

    private MapTestSupport() {
    }

    /** The first tile of the app's map. */
    public static ZoomXY beginTile() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {});
        ZoomXY center = MapRasterTiles.getTileNumber(46.557314, 15.637771, Constants.ZOOM);
        int half = (Constants.NUM_TILES - 1) / 2;
        return new ZoomXY(Constants.ZOOM, center.x - half, center.y - half);
    }
}
