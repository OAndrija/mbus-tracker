package com.mbus.app.utils;

import com.mbus.app.systems.map.MapRasterTiles;

public class Constants {
    public static final int NUM_TILES = 29;
    public static final int ZOOM = 15;
    public static final int MAP_WIDTH = MapRasterTiles.TILE_SIZE * NUM_TILES;
    public static final int MAP_HEIGHT = MapRasterTiles.TILE_SIZE * NUM_TILES;
    public static final float HUD_WIDTH = 5f;
}
