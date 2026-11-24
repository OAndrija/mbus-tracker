package com.mbus.app.ui.theme;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

/**
 * Puts every letter exactly on the pixels of the screen. With display scaling such as 150%, whole
 * logical pixels are not whole screen pixels, and letters in between would be blurred and cut off.
 */
class CrispFont extends BitmapFont {

    private static final int FLOATS_PER_VERTEX = 5;
    private static final int FLOATS_PER_GLYPH = 4 * FLOATS_PER_VERTEX;

    private final float density;

    CrispFont(BitmapFontData data, Array<TextureRegion> pages, float density) {
        super(data, pages, false);
        this.density = density;
    }

    private float snap(float value) {
        return Math.round(value * density) / density;
    }

    @Override
    public BitmapFontCache newFontCache() {
        return new BitmapFontCache(this, false) {
            @Override
            public void addText(GlyphLayout layout, float x, float y) {
                int pages = getRegions().size;
                int[] start = new int[pages];
                for (int page = 0; page < pages; page++) start[page] = getVertexCount(page);
                super.addText(layout, x, y);
                for (int page = 0; page < pages; page++) {
                    snapGlyphs(getVertices(page), start[page], getVertexCount(page));
                }
            }

            @Override
            public void setPosition(float x, float y) {
                super.setPosition(snap(x), snap(y));
            }
        };
    }

    private void snapGlyphs(float[] vertices, int from, int to) {
        for (int glyph = from; glyph + FLOATS_PER_GLYPH <= to; glyph += FLOATS_PER_GLYPH) {
            float dx = snap(vertices[glyph]) - vertices[glyph];
            float dy = snap(vertices[glyph + 1]) - vertices[glyph + 1];
            for (int v = glyph; v < glyph + FLOATS_PER_GLYPH; v += FLOATS_PER_VERTEX) {
                vertices[v] += dx;
                vertices[v + 1] += dy;
            }
        }
    }
}
