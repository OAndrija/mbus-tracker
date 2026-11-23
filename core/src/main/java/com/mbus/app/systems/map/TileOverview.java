package com.mbus.app.systems.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.mbus.app.utils.Constants;

import java.nio.ByteBuffer;

/**
 * Builds one low-resolution picture of the whole map out of the individual tiles.
 * Each tile writes only to its own square, so tiles may be added from several threads at once.
 */
public final class TileOverview {

    public static final int TILE_PIXELS = 128;
    /** Power of two, so mipmaps also work on GLES2. */
    public static final int TEXTURE_SIZE = 4096;
    /** Part of the texture that holds map; the rest is padding. */
    public static final int MAP_PIXELS = TILE_PIXELS * Constants.NUM_TILES;

    private static final int RGB_BYTES = 3;

    private TileOverview() {
    }

    public static Pixmap create() {
        Pixmap overview = new Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGB888);
        overview.setBlending(Pixmap.Blending.None);
        overview.setColor(0.95f, 0.94f, 0.92f, 1f);
        overview.fill();
        return overview;
    }

    public static void addTile(Pixmap overview, byte[] tileData, int column, int row) {
        Pixmap tile = decode(tileData);
        try {
            downsample(tile, overview, column * TILE_PIXELS, row * TILE_PIXELS);
        } finally {
            tile.dispose();
        }
    }

    /** Decodes an image file to RGB888. The caller owns the returned pixmap. */
    public static Pixmap decode(byte[] data) {
        Pixmap decoded = new Pixmap(data, 0, data.length);
        if (decoded.getFormat() == Pixmap.Format.RGB888) {
            return decoded;
        }

        Pixmap rgb = new Pixmap(decoded.getWidth(), decoded.getHeight(), Pixmap.Format.RGB888);
        rgb.setBlending(Pixmap.Blending.None);
        rgb.drawPixmap(decoded, 0, 0);
        decoded.dispose();
        return rgb;
    }

    /** Averages every source block into one pixel, which keeps thin roads and text from sparkling. */
    private static void downsample(Pixmap tile, Pixmap overview, int destX, int destY) {
        int factorX = tile.getWidth() / TILE_PIXELS;
        int factorY = tile.getHeight() / TILE_PIXELS;
        if (factorX == 0 || factorY == 0) return;

        int samples = factorX * factorY;
        int sourceStride = tile.getWidth() * RGB_BYTES;
        int destStride = overview.getWidth() * RGB_BYTES;
        ByteBuffer source = tile.getPixels();
        ByteBuffer dest = overview.getPixels();

        for (int y = 0; y < TILE_PIXELS; y++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < factorY; sy++) {
                    int offset = (y * factorY + sy) * sourceStride + x * factorX * RGB_BYTES;
                    for (int sx = 0; sx < factorX; sx++) {
                        r += source.get(offset++) & 0xff;
                        g += source.get(offset++) & 0xff;
                        b += source.get(offset++) & 0xff;
                    }
                }
                int out = (destY + y) * destStride + (destX + x) * RGB_BYTES;
                dest.put(out, (byte) (r / samples));
                dest.put(out + 1, (byte) (g / samples));
                dest.put(out + 2, (byte) (b / samples));
            }
        }
    }
}
