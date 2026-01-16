package com.mbus.app.lwjgl3;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class AssetPacker {

    private static final boolean DRAW_DEBUG_OUTLINE = false;

    private static final String RAW_ASSETS_PATH = "lwjgl3/assets-raw/bus";
    private static final String ASSETS_PATH = "assets";

    public static void main(String[] args) {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.debug = DRAW_DEBUG_OUTLINE;
        settings.maxWidth = 4096;
        settings.maxHeight = 4096;

        TexturePacker.process(settings,
            RAW_ASSETS_PATH,   // the directory containing individual images to be packed
            ASSETS_PATH + "/sprites",   // the directory where the pack file will be written
            "bus"   // the name of the pack file / atlas name
        );
    }
}
