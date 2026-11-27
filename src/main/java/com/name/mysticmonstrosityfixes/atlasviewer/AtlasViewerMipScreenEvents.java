package com.name.mysticmonstrosityfixes.atlasviewer;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import xfacthd.atlasviewer.client.mixin.AccessorTextureAtlas;
import xfacthd.atlasviewer.client.screen.AtlasScreen;
import xfacthd.atlasviewer.client.screen.SpriteInfoScreen;
import xfacthd.atlasviewer.client.screen.widget.DiscreteSliderButton;

import java.lang.reflect.Field;
import java.util.Map;

public final class AtlasViewerMipScreenEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field ATLAS_SCREEN_CURRENT_ATLAS = findField(AtlasScreen.class, "currentAtlas");
    private static final Field ATLAS_SCREEN_CURRENT_MIP = findField(AtlasScreen.class, "currentMipLevel");
    private static final Field ATLAS_SCREEN_MIP_SLIDER = findField(AtlasScreen.class, "mipLevelSlider");
    private static final Field SPRITE_SCREEN_ATLAS = findField(SpriteInfoScreen.class, "atlas");
    private static final Field SPRITE_SCREEN_SPRITE = findField(SpriteInfoScreen.class, "sprite");
    private static final Field SPRITE_SCREEN_CURRENT_MIP = findField(SpriteInfoScreen.class, "currentMipLevel");

    private static TextureAtlas cachedAtlas;
    private static Map<ResourceLocation, TextureAtlasSprite> cachedSprites;
    private static int cachedMip;
    private static int cachedMax;

    private AtlasViewerMipScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Object screen = event.getScreen();
        TextureAtlas atlas;
        if (screen instanceof AtlasScreen) {
            atlas = readField(ATLAS_SCREEN_CURRENT_ATLAS, screen);
        } else if (screen instanceof SpriteInfoScreen) {
            atlas = readField(SPRITE_SCREEN_ATLAS, screen);
        } else {
            return;
        }
        if (atlas == null) {
            return;
        }
        DiscreteSliderButton slider = findSlider(event);
        if (slider == null) {
            return;
        }
        AccessorTextureAtlas access = (AccessorTextureAtlas) atlas;
        int max = screen instanceof AtlasScreen
                ? atlasMipLimit(atlas, access)
                : AtlasMipLevels.spriteMipLimit(readField(SPRITE_SCREEN_SPRITE, screen));
        int step = Math.min(slider.getStep(), max);
        slider.setMaxStep(max);
        slider.setStep(step, true);
        ((AbstractWidget) slider).active = max > 0;
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        Object screen = event.getScreen();
        ScreenState state = screenState(screen);
        if (state == null || state.atlas == null) {
            return;
        }
        AccessorTextureAtlas access = (AccessorTextureAtlas) state.atlas;
        int mipLevel = state.mipLevel;
        if (screen instanceof AtlasScreen) {
            updateAtlasScreenSlider(screen, state.atlas, access);
            Integer updated = readField(ATLAS_SCREEN_CURRENT_MIP, screen);
            if (updated != null) {
                mipLevel = updated;
            }
        }
        if (mipLevel <= 0) {
            AtlasMipLevels.onLevelZero(screen);
            AtlasMipLevels.resetMaxLevel(state.atlas, access.getMipLevel());
            return;
        }
        AtlasMipLevels.prepare(
                state.atlas,
                mipLevel,
                access.getWidth(),
                access.getHeight(),
                access.getMipLevel(),
                true,
                screen,
                access.getTexturesByName()
        );
    }

    private static void updateAtlasScreenSlider(Object screen, TextureAtlas atlas, AccessorTextureAtlas access) {
        DiscreteSliderButton slider = readField(ATLAS_SCREEN_MIP_SLIDER, screen);
        if (slider == null) {
            return;
        }
        int max = atlasMipLimit(atlas, access);
        if (slider.getMaxStep() == max) {
            if (max > 0) {
                ((AbstractWidget) slider).active = true;
            }
            return;
        }
        int step = Math.min(slider.getStep(), max);
        slider.setMaxStep(max);
        slider.setStep(step, true);
        ((AbstractWidget) slider).active = max > 0;
    }

    private static int atlasMipLimit(TextureAtlas atlas, AccessorTextureAtlas access) {
        Map<ResourceLocation, TextureAtlasSprite> sprites = access.getTexturesByName();
        int mip = access.getMipLevel();
        if (atlas != cachedAtlas || sprites != cachedSprites || mip != cachedMip) {
            cachedAtlas = atlas;
            cachedSprites = sprites;
            cachedMip = mip;
            cachedMax = AtlasMipLevels.atlasMipLimit(sprites.values(), mip);
        }
        return cachedMax;
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ScreenState state = screenState(event.getScreen());
        if (state != null && state.atlas != null) {
            AccessorTextureAtlas access = (AccessorTextureAtlas) state.atlas;
            AtlasMipLevels.resetMaxLevel(state.atlas, access.getMipLevel());
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        Object screen = event.getScreen();
        if (screen instanceof AtlasScreen) {
            AtlasMipLevels.onViewerClosed();
        } else if (screen instanceof SpriteInfoScreen) {
            AtlasMipLevels.onScreenClosed(screen);
        }
    }

    private static ScreenState screenState(Object screen) {
        if (screen instanceof AtlasScreen) {
            TextureAtlas atlas = readField(ATLAS_SCREEN_CURRENT_ATLAS, screen);
            Integer mipLevel = readField(ATLAS_SCREEN_CURRENT_MIP, screen);
            return atlas != null && mipLevel != null ? new ScreenState(atlas, mipLevel) : null;
        }
        if (screen instanceof SpriteInfoScreen) {
            TextureAtlas atlas = readField(SPRITE_SCREEN_ATLAS, screen);
            Integer mipLevel = readField(SPRITE_SCREEN_CURRENT_MIP, screen);
            return atlas != null && mipLevel != null ? new ScreenState(atlas, mipLevel) : null;
        }
        return null;
    }

    private static DiscreteSliderButton findSlider(ScreenEvent.Init.Post event) {
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof DiscreteSliderButton slider) {
                return slider;
            }
        }
        return null;
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Could not find AtlasViewer field {} in {}", name, owner.getName(), exception);
            return null;
        }
    }

    private static <T> T readField(Field field, Object owner) {
        if (field == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            T value = (T) field.get(owner);
            return value;
        } catch (IllegalAccessException exception) {
            LOGGER.warn("Could not read AtlasViewer field {} from {}", field.getName(), owner.getClass().getName(), exception);
            return null;
        }
    }

    private record ScreenState(TextureAtlas atlas, int mipLevel) {
    }
}
