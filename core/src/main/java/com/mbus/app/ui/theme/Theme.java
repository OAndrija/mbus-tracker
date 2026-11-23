package com.mbus.app.ui.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.mbus.app.utils.MipMaps;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fonts, shapes and icons of the interface. Everything is rendered at startup for the pixel density
 * of the screen, so it stays sharp on HiDPI displays. Sizes are in logical pixels.
 */
public class Theme implements Disposable {

    private static final String CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
        + "čšžćđČŠŽĆĐ" + "–—·•…©";
    private static final int MARKER_PIXELS = 128;
    private static final float MARKER_SHADOW = 10f;

    public final BitmapFont caption;
    public final BitmapFont captionStrong;
    public final BitmapFont body;
    public final BitmapFont bodyStrong;
    public final BitmapFont title;
    public final BitmapFont brand;
    public final BitmapFont display;
    /** Large and mipmapped, for text that scales with the map. */
    public final BitmapFont map;

    public final Texture stopMarker;
    public final Texture stopMarkerHovered;
    public final Texture stopMarkerSelected;
    public final Texture clusterMarker;

    // Buses point to the right. The base is white with a shadow, the fill takes the line color.
    public final Texture busDiscBase;
    public final Texture busDiscFill;
    public final Texture busBodyBase;
    public final Texture busBodyFill;
    public final Texture busBodyDetail;
    /** Diameter of the disc relative to its texture, and length of the bus relative to its texture. */
    public static final float BUS_DISC_SCALE = 92f / 192f;
    public static final float BUS_BODY_SCALE = 216f / 256f;

    /** Size of the visible disc relative to a marker texture, the rest is shadow. */
    public static final float MARKER_DISC = (MARKER_PIXELS - 2 * MARKER_SHADOW) / MARKER_PIXELS;

    private final float density;
    private final List<Disposable> owned = new ArrayList<>();
    private final Map<String, NinePatch> patches = new HashMap<>();
    private TextureRegion pixel;
    private final Map<Icon, TextureRegion> icons = new EnumMap<>(Icon.class);

    public Theme() {
        density = Math.max(1f, Gdx.graphics.getBackBufferWidth() / (float) Math.max(1, Gdx.graphics.getWidth()));

        FreeTypeFontGenerator medium = generator("Inter-Medium");
        FreeTypeFontGenerator semiBold = generator("Inter-SemiBold");
        FreeTypeFontGenerator bold = generator("Inter-Bold");

        caption = font(medium, 12);
        captionStrong = font(semiBold, 12);
        body = font(medium, 14);
        bodyStrong = font(semiBold, 14);
        title = font(semiBold, 18);
        brand = font(bold, 20);
        display = font(bold, 34);
        map = mapFont(bold, 26);

        medium.dispose();
        semiBold.dispose();
        bold.dispose();

        stopMarker = marker(Palette.SURFACE, Palette.TEXT, 5f, Icon.BUS, Palette.TEXT);
        stopMarkerHovered = marker(Palette.SURFACE, Palette.BRAND, 8f, Icon.BUS, Palette.BRAND);
        stopMarkerSelected = marker(Palette.BRAND, Palette.SURFACE, 7f, Icon.BUS, Palette.SURFACE);
        clusterMarker = marker(Palette.SURFACE, Palette.TEXT, 6f, null, null);

        Shapes.Distance disc = Shapes.union(
            Shapes.circle(96, 96, 46), Shapes.triangle(4, 126, 70, 126, 122, 158, 96));
        busDiscBase = base(192, 192, Shapes.grow(8, disc));
        busDiscFill = fillTexture(192, 192, disc);

        Shapes.Distance body = Shapes.roundRect(28, 24, 200, 80, 22);
        busBodyBase = base(256, 128, Shapes.grow(8, body));
        busBodyFill = fillTexture(256, 128, body);
        busBodyDetail = busDetail();
    }

    // Fonts

    private FreeTypeFontGenerator generator(String name) {
        return new FreeTypeFontGenerator(Gdx.files.internal("fonts/" + name + ".otf"));
    }

    private BitmapFont font(FreeTypeFontGenerator generator, int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = Math.round(size * density);
        parameter.characters = CHARACTERS;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Slight;
        parameter.gamma = 1.3f;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;

        BitmapFont font = generator.generateFont(parameter);
        font.getData().setScale(1f / density);
        font.setUseIntegerPositions(density == 1f);
        owned.add(font);
        return font;
    }

    private BitmapFont mapFont(FreeTypeFontGenerator generator, int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = Math.round(size * density);
        parameter.characters = "0123456789";
        // Room around the glyphs, so neighbors do not bleed in when scaled down
        parameter.padLeft = parameter.padRight = parameter.padTop = parameter.padBottom = 2;

        BitmapFont font = generator.generateFont(parameter);
        for (TextureRegion region : font.getRegions()) {
            MipMaps.generate(region.getTexture());
        }
        font.setUseIntegerPositions(false);
        owned.add(font);
        return font;
    }

    public Label label(String text, BitmapFont font, Color color) {
        return new Label(text, new Label.LabelStyle(font, new Color(color)));
    }

    // Shapes

    public Drawable roundRect(float radius, Color fill) {
        return roundRect(radius, fill, null, 0f);
    }

    /** A stretchable rounded rectangle, optionally with a border on the inside of its edge. */
    public Drawable roundRect(float radius, Color fill, Color border, float borderWidth) {
        String key = "rect/" + radius + "/" + fill + "/" + border + "/" + borderWidth;
        NinePatch patch = patches.get(key);
        if (patch == null) {
            float size = 2f * radius + 4f;
            int pixels = MathUtils.ceil(size * density);
            float units = pixels / density;
            Pixmap pixmap = Shapes.transparent(pixels, pixels);
            if (border != null) {
                Shapes.paint(pixmap, density, border, Shapes.roundRect(0, 0, units, units, radius));
                float inner = units - 2 * borderWidth;
                Shapes.paint(pixmap, density, fill,
                    Shapes.roundRect(borderWidth, borderWidth, inner, inner, Math.max(0, radius - borderWidth)));
            } else {
                Shapes.paint(pixmap, density, fill, Shapes.roundRect(0, 0, units, units, radius));
            }
            patch = ninePatch(pixmap, MathUtils.ceil((radius + 1f) * density));
            patches.put(key, patch);
        }
        NinePatchDrawable drawable = new NinePatchDrawable(patch);
        drawable.setMinWidth(0);
        drawable.setMinHeight(0);
        drawable.setPadding(0, 0, 0, 0);
        return drawable;
    }

    /** A plain rectangle of one color. */
    public Drawable fill(Color color, float minWidth, float minHeight) {
        if (pixel == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            pixel = new TextureRegion(texture(pixmap, false));
        }
        Drawable drawable = new TextureRegionDrawable(pixel).tint(color);
        drawable.setMinWidth(minWidth);
        drawable.setMinHeight(minHeight);
        return drawable;
    }

    /** A white card that floats above the map. The shadow is drawn outside of the bounds. */
    public Drawable card(float radius) {
        final float blur = 14f;
        final float offset = 4f;
        final float margin = blur + offset;

        NinePatch patch = patches.get("card/" + radius);
        if (patch == null) {
            float size = 2f * (margin + radius) + 4f;
            int pixels = MathUtils.ceil(size * density);
            float units = pixels / density - 2f * margin;
            Pixmap pixmap = Shapes.transparent(pixels, pixels);
            Shapes.paintShadow(pixmap, density, blur, 0.16f,
                Shapes.roundRect(margin, margin + offset, units, units, radius));
            Shapes.paintShadow(pixmap, density, 2f, 0.10f,
                Shapes.roundRect(margin, margin + 1f, units, units, radius));
            Shapes.paint(pixmap, density, Palette.SURFACE,
                Shapes.roundRect(margin, margin, units, units, radius));
            patch = ninePatch(pixmap, MathUtils.ceil((margin + radius + 1f) * density));
            patches.put("card/" + radius, patch);
        }

        final NinePatch cardPatch = patch;
        return new BaseDrawable() {
            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                cardPatch.draw(batch, x - margin, y - margin, width + 2 * margin, height + 2 * margin);
            }
        };
    }

    private NinePatch ninePatch(Pixmap pixmap, int split) {
        Texture texture = texture(pixmap, false);
        NinePatch patch = new NinePatch(texture, split, split, split, split);
        patch.scale(1f / density, 1f / density);
        return patch;
    }

    // Icons

    public Drawable icon(Icon icon, float size, Color color) {
        TextureRegion region = icons.get(icon);
        if (region == null) {
            // Mipmapped, as one texture serves several sizes.
            int pixels = Math.round(Icon.GRID * 2f * density);
            Pixmap pixmap = Shapes.draw(pixels, pixels, pixels / Icon.GRID, Color.WHITE, icon.shape);
            region = new TextureRegion(texture(pixmap, true));
            icons.put(icon, region);
        }
        Drawable tinted = new TextureRegionDrawable(region).tint(color);
        tinted.setMinWidth(size);
        tinted.setMinHeight(size);
        return tinted;
    }

    /** The app logo: a bus on a rounded brand-colored square. */
    public Drawable logo(float size) {
        int pixels = MathUtils.ceil(size * density);
        TextureRegionDrawable drawable = new TextureRegionDrawable(texture(logoPixmap(pixels), false));
        drawable.setMinWidth(size);
        drawable.setMinHeight(size);
        return drawable;
    }

    public static Pixmap logoPixmap(int pixels) {
        Pixmap pixmap = Shapes.transparent(pixels, pixels);
        float scale = pixels / 40f;
        Shapes.paint(pixmap, scale, Palette.BRAND, Shapes.roundRect(0, 0, 40, 40, 10));
        Shapes.paint(pixmap, scale, Palette.SURFACE, (x, y) -> Icon.BUS.shape.at(x - 8f, y - 8f));
        return pixmap;
    }

    private Texture base(int width, int height, Shapes.Distance shape) {
        Pixmap pixmap = Shapes.transparent(width, height);
        Shapes.paintShadow(pixmap, 1f, 10f, 0.5f, shape);
        Shapes.paint(pixmap, 1f, Palette.SURFACE, shape);
        return texture(pixmap, true);
    }

    private Texture fillTexture(int width, int height, Shapes.Distance shape) {
        return texture(Shapes.draw(width, height, 1f, Color.WHITE, shape), true);
    }

    /** Windshield, rear window and the edges of the roof. */
    private Texture busDetail() {
        Color glass = new Color(0.06f, 0.09f, 0.16f, 0.85f);
        Color shade = new Color(0f, 0f, 0f, 0.16f);
        Color light = new Color(1f, 1f, 1f, 0.9f);
        Pixmap pixmap = Shapes.transparent(256, 128);
        Shapes.paint(pixmap, 1f, shade, Shapes.roundRect(62, 30, 124, 6, 3));
        Shapes.paint(pixmap, 1f, shade, Shapes.roundRect(62, 92, 124, 6, 3));
        Shapes.paint(pixmap, 1f, glass, Shapes.roundRect(194, 36, 18, 56, 8));
        Shapes.paint(pixmap, 1f, glass, Shapes.roundRect(38, 42, 10, 44, 5));
        Shapes.paint(pixmap, 1f, light, Shapes.roundRect(218, 34, 6, 14, 3));
        Shapes.paint(pixmap, 1f, light, Shapes.roundRect(218, 80, 6, 14, 3));
        return texture(pixmap, true);
    }

    private Texture marker(Color fill, Color ring, float ringWidth, Icon icon, Color iconColor) {
        float center = MARKER_PIXELS / 2f;
        float radius = center - MARKER_SHADOW;
        Pixmap pixmap = Shapes.transparent(MARKER_PIXELS, MARKER_PIXELS);
        Shapes.paintShadow(pixmap, 1f, MARKER_SHADOW - 2f, 0.45f, Shapes.circle(center, center + 2f, radius));
        Shapes.paint(pixmap, 1f, ring, Shapes.circle(center, center, radius));
        if (ringWidth > 0) {
            Shapes.paint(pixmap, 1f, fill, Shapes.circle(center, center, radius - ringWidth));
        }
        if (icon != null) {
            float scale = radius * 1.12f / Icon.GRID;
            float origin = center - Icon.GRID * scale / 2f;
            Shapes.paint(pixmap, 1f, iconColor,
                (x, y) -> icon.shape.at((x - origin) / scale, (y - origin) / scale) * scale);
        }
        return texture(pixmap, true);
    }

    private Texture texture(Pixmap pixmap, boolean mipMaps) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        if (mipMaps) MipMaps.generate(texture);
        pixmap.dispose();
        owned.add(texture);
        return texture;
    }

    // Widget styles

    public ScrollPane.ScrollPaneStyle scrollPane() {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.vScrollKnob = roundRect(3, Color.valueOf("cbd5e1"));
        style.vScrollKnob.setMinWidth(6);
        style.vScrollKnob.setMinHeight(36);
        return style;
    }

    @Override
    public void dispose() {
        for (Disposable disposable : owned) disposable.dispose();
        owned.clear();
    }
}
