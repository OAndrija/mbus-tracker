package com.mbus.app.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class AssetDescriptors {
    public static final AssetDescriptor<Skin> SKIN =
        new AssetDescriptor<Skin>(AssetPaths.SKIN, Skin.class);

    public static final AssetDescriptor<Texture> BUS_ICON =
        new AssetDescriptor<Texture>(AssetPaths.BUS_ICON, Texture.class);

    public static final AssetDescriptor<Texture> TITLE_ICON =
        new AssetDescriptor<Texture>(AssetPaths.TITLE_ICON, Texture.class);

    private AssetDescriptors() {
    }
}
