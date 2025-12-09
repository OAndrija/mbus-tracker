package com.mbus.app;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.mbus.app.model.BusStop;
import com.mbus.app.utils.*;


import java.io.IOException;
import java.util.List;

public class RasterMap extends ApplicationAdapter implements GestureDetector.GestureListener {

    private ShapeRenderer shapeRenderer;
    private Vector3 touchPosition;

    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;
    private OrthographicCamera camera;

    private Texture[] mapTiles;
    private ZoomXY beginTile;   // top left tile
    private List<BusStop> stops;
    // center geolocation
    private final Geolocation CENTER_GEOLOCATION = new Geolocation(46.557314, 15.637771);

    // test marker
    private final Geolocation MARKER_GEOLOCATION = new Geolocation(46.559070, 15.638100);

    @Override
    public void create() {
        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Constants.MAP_WIDTH, Constants.MAP_HEIGHT);
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.viewportWidth = Constants.MAP_WIDTH / 2f;
        camera.viewportHeight = Constants.MAP_HEIGHT / 2f;
        camera.zoom = 0.5f;
        camera.update();

        touchPosition = new Vector3();

        try {
            ZoomXY centerTile = MapRasterTiles.getTileNumber(CENTER_GEOLOCATION.lat, CENTER_GEOLOCATION.lng, Constants.ZOOM);
            mapTiles = MapRasterTiles.getRasterTileZone(centerTile, Constants.NUM_TILES);
            beginTile = new ZoomXY(Constants.ZOOM, centerTile.x - ((Constants.NUM_TILES - 1) / 2), centerTile.y - ((Constants.NUM_TILES - 1) / 2));
        } catch (IOException e) {
            e.printStackTrace();
        }

        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(Constants.NUM_TILES, Constants.NUM_TILES, MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE);
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

        stops = GeoJSONLoader.loadBusStopsFromFile("data/int_mob_marprom_postaje.json");

        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0, 0, 0, 1);

        handleInput();

        camera.update();

        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();

        drawMarkers();
    }

    private void drawMarkers() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(Color.RED);

        for (BusStop stop : stops) {

//            for(int i = 0; i < 3; i++) {  System.out.println("STOP LAT/LON: " + stop.lat + ", " + stop.lon); }
            // Convert lat/lon to pixel position on the raster map
            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            // Optional: skip if marker is not visible on current map bounds
            if (pos.x < 0 || pos.y < 0 || pos.x > Constants.MAP_WIDTH || pos.y > Constants.MAP_HEIGHT) {
                continue;
            }

//            for(int j = 0; j < 3; j++) { System.out.println("STOP PIXEL: " + pos); }

            // Draw marker
            shapeRenderer.circle(pos.x, pos.y, 6);
        }

        shapeRenderer.end();
    }


    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        touchPosition.set(x, y, 0);
        camera.unproject(touchPosition);
        return false;
    }
    @Override
    public boolean tap(float x, float y, int count, int button) {
        return false;
    }
    @Override
    public boolean longPress(float x, float y) {
        return false;
    }
    @Override
    public boolean fling(float velocityX, float velocityY, int button) {
        return false;
    }
    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        camera.translate(-deltaX, deltaY);
        return false;
    }
    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }
    @Override
    public boolean zoom(float initialDistance, float distance) {
        if (initialDistance >= distance)
            camera.zoom += 0.02;
        else
            camera.zoom -= 0.02;
        return false;
    }
    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        return false;
    }
    @Override
    public void pinchStop() {

    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.zoom += 0.02;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            camera.zoom -= 0.02;
        }
        float moveSpeed = 20 * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.translate(-moveSpeed, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.translate(moveSpeed, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.translate(0, -moveSpeed, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.translate(0, moveSpeed, 0);
        }

        camera.zoom = MathUtils.clamp(camera.zoom, 0.5f, 2f);

        float effectiveViewportWidth = camera.viewportWidth * camera.zoom;
        float effectiveViewportHeight = camera.viewportHeight * camera.zoom;

        camera.position.x = MathUtils.clamp(camera.position.x, effectiveViewportWidth / 2f, Constants.MAP_WIDTH - effectiveViewportWidth / 2f);
        camera.position.y = MathUtils.clamp(camera.position.y, effectiveViewportHeight / 2f, Constants.MAP_HEIGHT - effectiveViewportHeight / 2f);
    }
}
