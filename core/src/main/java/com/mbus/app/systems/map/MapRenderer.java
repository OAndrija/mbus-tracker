package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Vector2;

import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;
import com.mbus.app.utils.Constants;

import java.util.List;

public class MapRenderer {

    private final OrthographicCamera camera;
    private final ShapeRenderer shapeRenderer;

    private Texture[] mapTiles;
    private ZoomXY beginTile;
    private List<BusStop> stops;

    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;

    public MapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
    }

    // ----------------------------
    // PUBLIC API
    // ----------------------------

    public void loadTiles(Texture[] tiles, ZoomXY beginTile) {
        this.mapTiles = tiles;
        this.beginTile = beginTile;
        buildTileMap();
    }

    public void setStops(List<BusStop> stops) {
        this.stops = stops;
    }

    public void render() {
        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();
        renderMarkers();
    }

    public void dispose() {
        shapeRenderer.dispose();

        if (tiledMap != null)
            tiledMap.dispose();

        if (mapTiles != null) {
            for (Texture t : mapTiles)
                if (t != null) t.dispose();
        }
    }

    // ----------------------------
    // PRIVATE INTERNAL LOGIC
    // ----------------------------

    private void buildTileMap() {
        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(
            Constants.NUM_TILES,
            Constants.NUM_TILES,
            MapRasterTiles.TILE_SIZE,
            MapRasterTiles.TILE_SIZE
        );

        int index = 0;
        for (int j = Constants.NUM_TILES - 1; j >= 0; j--) {
            for (int i = 0; i < Constants.NUM_TILES; i++) {
                TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                cell.setTile(new StaticTiledMapTile(new TextureRegion(mapTiles[index], MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE)));
                layer.setCell(i, j, cell);
                index++;
            }
        }

        layers.add(layer);
        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);
    }

    private void renderMarkers() {
        if (stops == null || stops.isEmpty()) return;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1, 0, 0, 1);

        for (BusStop stop : stops) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x < 0 || pos.y < 0 ||
                pos.x > Constants.MAP_WIDTH || pos.y > Constants.MAP_HEIGHT)
                continue;

            shapeRenderer.circle(pos.x, pos.y, 6);
        }

        shapeRenderer.end();
    }
}
