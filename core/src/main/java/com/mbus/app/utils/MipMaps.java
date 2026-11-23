package com.mbus.app.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * Mipmaps made by the GPU. With ANGLE, libGDX's own option makes them on the CPU, which is slow and
 * leaves textures that are not square incomplete, so that they are drawn black.
 */
public final class MipMaps {

    private MipMaps() {
    }

    /** Creates a mipmapped texture. The pixmap still belongs to the caller. */
    public static Texture create(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        generate(texture);
        return texture;
    }

    public static void generate(Texture texture) {
        texture.bind();
        Gdx.gl20.glGenerateMipmap(texture.glTarget);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
    }
}
