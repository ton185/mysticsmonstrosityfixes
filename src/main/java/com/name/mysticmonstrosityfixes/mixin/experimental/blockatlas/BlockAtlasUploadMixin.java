package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uploads each block-atlas page as a layer of one texture array on the atlas texture id.
 */
@Mixin(TextureAtlas.class)
public abstract class BlockAtlasUploadMixin extends AbstractTexture {
    @Shadow
    private List<SpriteContents> sprites;

    @Shadow
    private List<TextureAtlasSprite.Ticker> animatedTextures;

    @Shadow
    private Map<ResourceLocation, TextureAtlasSprite> texturesByName;

    @Shadow
    private ResourceLocation location;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private int mipLevel;

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$uploadPages(SpriteLoader.Preparations preparations, CallbackInfo callback) {
        if (!BlockAtlasPages.isBlockAtlas(this.location)) {
            return;
        }
        if (BlockAtlasPages.shaderPackActive()) {
            BlockAtlasPages.prepareReload(this.id);
            ((TextureAtlas) (Object) this).clearTextureData();
            return;
        }
        BlockAtlasPages.clearTickers();
        BlockAtlasPages.prepareReload(this.id);
        ((TextureAtlas) (Object) this).clearTextureData();
        int pages = 1;
        for (TextureAtlasSprite sprite : preparations.regions().values()) {
            pages = Math.max(pages, BlockAtlasPages.pageOf(sprite) + 1);
        }
        boolean uploaded = BlockAtlasPages.uploadCached(pages, preparations.width(), preparations.mipLevel());
        if (!uploaded) {
            BlockAtlasPages.allocateStorage(pages, preparations.width(), preparations.mipLevel());
        }
        this.width = preparations.width();
        this.height = preparations.height();
        this.mipLevel = preparations.mipLevel();
        this.texturesByName = Map.copyOf(preparations.regions());
        BlockAtlasPages.rememberSprites(this.texturesByName);

        List<SpriteContents> contents = new ArrayList<>();
        List<TextureAtlasSprite.Ticker> tickers = new ArrayList<>();
        for (TextureAtlasSprite sprite : preparations.regions().values()) {
            contents.add(sprite.contents());
            int page = BlockAtlasPages.pageOf(sprite);
            if (!uploaded) {
                BlockAtlasPages.beginLayer(page);
                try {
                    sprite.uploadFirstFrame();
                } catch (Throwable throwable) {
                    CrashReport report = CrashReport.forThrowable(throwable, "Stitching texture atlas");
                    CrashReportCategory category = report.addCategory("Texture being stitched together");
                    category.setDetail("Atlas path", this.location);
                    category.setDetail("Sprite", sprite);
                    category.setDetail("Page", page);
                    throw new ReportedException(report);
                } finally {
                    BlockAtlasPages.endLayer();
                }
            }
            TextureAtlasSprite.Ticker ticker = sprite.createTicker();
            if (ticker != null) {
                BlockAtlasPages.rememberTicker(ticker, page);
                tickers.add(ticker);
            }
        }
        this.id = BlockAtlasPages.finishViews(preparations.width(), preparations.mipLevel());
        this.setFilter(false, preparations.mipLevel() > 0);
        this.sprites = List.copyOf(contents);
        this.animatedTextures = List.copyOf(tickers);
        ForgeHooksClient.onTextureStitchedPost((TextureAtlas) (Object) this);
        // The published id is a 2D view of page 0. getSprite still resolves every name.
        if (BlockAtlasPages.limitNamesToPageZero()) {
            this.texturesByName = BlockAtlasPages.spritesOnPage(0);
        }
        callback.cancel();
    }

    @Inject(method = "getSprite", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$spriteOnAnyPage(ResourceLocation name, CallbackInfoReturnable<TextureAtlasSprite> callback) {
        TextureAtlasSprite sprite = BlockAtlasPages.sprite(this.location, name);
        if (sprite != null) {
            callback.setReturnValue(sprite);
        }
    }

    @Inject(method = "getTextureLocations", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$allSpriteNames(CallbackInfoReturnable<Set<ResourceLocation>> callback) {
        Set<ResourceLocation> names = BlockAtlasPages.spriteNames(this.location);
        if (names != null) {
            callback.setReturnValue(names);
        }
    }

    @Inject(method = "dumpContents", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$dumpPages(ResourceLocation id, Path path, CallbackInfo callback) {
        if (BlockAtlasPages.dumpAtlas(id, path)) {
            callback.cancel();
        }
    }

    @Inject(method = "dumpContents", at = @At("RETURN"))
    private void mysticmonstrosityfixes$writePageList(ResourceLocation id, Path path, CallbackInfo callback) {
        BlockAtlasPages.writeSpritePages(path, id);
    }

    @Redirect(
            method = "cycleAnimationFrames",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Ticker;tickAndUpload()V"
            )
    )
    private void mysticmonstrosityfixes$tickOnPage(TextureAtlasSprite.Ticker ticker) {
        if (!BlockAtlasPages.isBlockAtlas(this.location)) {
            ticker.tickAndUpload();
            return;
        }
        BlockAtlasPages.beginLayer(BlockAtlasPages.pageOfTicker(ticker));
        try {
            ticker.tickAndUpload();
        } finally {
            BlockAtlasPages.endLayer();
        }
    }
}
