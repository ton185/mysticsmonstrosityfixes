package com.name.mysticmonstrosityfixes.atlasviewer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;

public final class AtlasMipLevels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Session session;

    private AtlasMipLevels() {
    }

    public static int spriteMipLimit(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        SpriteContents contents = sprite.contents();
        return spriteMipLimit(contents.width(), contents.height());
    }

    public static int spriteMipLimit(int width, int height) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        return Math.max(0, log2(Math.min(width, height)));
    }

    public static int atlasMipLimit(Collection<TextureAtlasSprite> sprites, int existingMip) {
        int max = Math.max(0, existingMip);
        if (sprites != null) {
            for (TextureAtlasSprite sprite : sprites) {
                max = Math.max(max, spriteMipLimit(sprite));
            }
        }
        return max;
    }

    public static void prepare(
            TextureAtlas atlas,
            int level,
            int width,
            int height,
            int existingMip,
            boolean generate,
            Object screen,
            Map<ResourceLocation, TextureAtlasSprite> sprites
    ) {
        if (atlas == null || level < 0) {
            return;
        }
        if (session != null && !session.matches(atlas)) {
            drop(true);
        }
        if (level > existingMip && session != null && session.generatedMax >= level) {
            setMaxLevel(atlas, session.generatedMax);
            return;
        }
        if (level <= existingMip || !generate || width <= 0 || height <= 0
                || sprites == null || sprites.isEmpty()) {
            setMaxLevel(atlas, existingMip);
            return;
        }
        int targetMax = atlasMipLimit(sprites.values(), existingMip);
        if (targetMax <= existingMip) {
            setMaxLevel(atlas, existingMip);
            return;
        }
        Session candidate = new Session(screen, atlas, atlas.getId(), existingMip, targetMax);
        if (generate(candidate, width, height, sprites)) {
            session = candidate;
            setMaxLevel(atlas, targetMax);
        } else {
            setMaxLevel(atlas, existingMip);
        }
    }

    private static boolean generate(
            Session candidate,
            int width,
            int height,
            Map<ResourceLocation, TextureAtlasSprite> sprites
    ) {
        if (!RenderSystem.isOnRenderThread() || sprites == null || sprites.isEmpty()) {
            return false;
        }
        TextureAtlas atlas = candidate.atlas;
        try {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(candidate.textureId);
            for (int level = candidate.existingMip + 1; level <= candidate.generatedMax; level++) {
                int levelWidth = width >> level;
                int levelHeight = height >> level;
                if (levelWidth <= 0 || levelHeight <= 0) {
                    freeLevels(candidate);
                    return false;
                }
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        level,
                        GL11.GL_RGBA,
                        levelWidth,
                        levelHeight,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        (ByteBuffer) null
                );
            }
            for (TextureAtlasSprite sprite : sprites.values()) {
                uploadSprite(sprite, candidate);
            }
            return true;
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Could not generate extra mip levels for atlas {}", atlas.location(), exception);
            freeLevels(candidate);
            return false;
        }
    }

    private static void uploadSprite(TextureAtlasSprite sprite, Session session) {
        SpriteContents contents = sprite.contents();
        int spriteMax = Math.min(session.generatedMax, spriteMipLimit(contents.width(), contents.height()));
        if (spriteMax <= session.existingMip) {
            return;
        }
        NativeImage[] generated;
        try {
            generated = MipmapGenerator.generateMipLevels(contents.byMipLevel, spriteMax);
        } catch (RuntimeException | Error exception) {
            LOGGER.debug("Could not generate mip levels for atlas sprite {}", contents.name(), exception);
            return;
        }
        int originalLength = contents.byMipLevel.length;
        try {
            for (int level = session.existingMip + 1; level <= spriteMax; level++) {
                int levelWidth = contents.width() >> level;
                int levelHeight = contents.height() >> level;
                if (levelWidth <= 0 || levelHeight <= 0) {
                    continue;
                }
                generated[level].upload(
                        level,
                        sprite.getX() >> level,
                        sprite.getY() >> level,
                        0,
                        0,
                        levelWidth,
                        levelHeight,
                        false,
                        false
                );
            }
        } finally {
            for (int level = originalLength; level < generated.length; level++) {
                generated[level].close();
            }
        }
    }

    public static void onAtlasSelected(Object screen, TextureAtlas atlas) {
        if (session != null && (session.screen != screen || session.atlas != atlas)) {
            drop(true);
        }
    }

    public static void onLevelZero(Object screen) {
        if (session != null && session.screen == screen) {
            drop(true);
        }
    }

    public static void onScreenClosed(Object screen) {
        if (session != null && session.screen == screen) {
            drop(true);
        }
    }

    public static void onViewerClosed() {
        drop(true);
    }

    public static void onAtlasUpload(TextureAtlas atlas) {
        if (session != null && session.atlas == atlas) {
            drop(true);
        }
    }

    public static void resetMaxLevel(TextureAtlas atlas, int existingMip) {
        setMaxLevel(atlas, existingMip);
    }

    private static void drop(boolean free) {
        Session current = session;
        session = null;
        if (free && current != null) {
            freeLevels(current);
        }
    }

    private static void freeLevels(Session current) {
        TextureAtlas atlas = current.atlas;
        if (atlas.getId() != current.textureId || !RenderSystem.isOnRenderThread()) {
            return;
        }
        try {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(current.textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, current.existingMip);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            for (int level = current.existingMip + 1; level <= current.generatedMax; level++) {
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        level,
                        GL11.GL_RGBA,
                        0,
                        0,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        (ByteBuffer) null
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not release generated mip levels for atlas {}", atlas.location(), exception);
        }
    }

    private static void setMaxLevel(TextureAtlas atlas, int level) {
        if (atlas == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(atlas.getId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, Math.max(0, level));
    }

    private static int log2(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    private static final class Session {
        private final Object screen;
        private final TextureAtlas atlas;
        private final int textureId;
        private final int existingMip;
        private final int generatedMax;

        private Session(Object screen, TextureAtlas atlas, int textureId, int existingMip, int generatedMax) {
            this.screen = screen;
            this.atlas = atlas;
            this.textureId = textureId;
            this.existingMip = existingMip;
            this.generatedMax = generatedMax;
        }

        private boolean matches(TextureAtlas atlas) {
            return this.atlas == atlas && this.textureId == atlas.getId();
        }
    }
}
