package com.mbus.app.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class AssetDescriptors {
    public static final AssetDescriptor<Skin> SKIN =
        new AssetDescriptor<>(AssetPaths.SKIN, Skin.class);

    private AssetDescriptors() {
    }
}
