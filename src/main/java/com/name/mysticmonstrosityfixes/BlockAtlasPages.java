package com.name.mysticmonstrosityfixes;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.StitcherException;
import com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas.TextureAtlasSpriteInvoker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Stitches the block atlas into fixed 16384×16384 pages with the vanilla {@link Stitcher}.
 * A sprite that does not fit is placed on the next page. Every page is one layer of a texture array
 * bound in place of the block atlas, and sprite U coordinates carry the page index.
 */
public final class BlockAtlasPages {
    public static final int PAGE_SIZE = 16384;
    /** Embeddium's compact vertex format has five free material bits, which address this many pages. */
    public static final int EMBEDDIUM_PAGE_LIMIT = 32;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> PAGING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> UPLOAD_LAYER = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Integer> CAPTURED_PAGE = ThreadLocal.withInitial(() -> 0);
    /** Embeddium encodes U, then V. Only U carries a page, so the V call must not replace it. */
    private static final ThreadLocal<Boolean> CHUNK_UV_IS_U = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private static final java.util.Set<String> PAGED_SHADERS = java.util.Set.of(
            "rendertype_solid",
            "rendertype_cutout",
            "rendertype_cutout_mipped",
            "rendertype_translucent",
            "rendertype_translucent_moving_block",
            "rendertype_translucent_no_crumbling",
            "rendertype_tripwire",
            "mmf_block_particle");

    private static volatile Map<TextureAtlasSprite, Integer> pages = Map.of();
    /** Same page index by sprite name, for a quad whose sprite object is not the stitched instance. */
    private static volatile Map<ResourceLocation, Integer> pagesByName = Map.of();
    /** Compact encoder writes the material byte, then the section id, with memPutByte. */
    private static final ThreadLocal<Boolean> COMPACT_MATERIAL_BYTE = ThreadLocal.withInitial(() -> Boolean.TRUE);
    /** Every stitched sprite, including ones the page-0 atlas map hides on later pages. */
    private static volatile Map<ResourceLocation, TextureAtlasSprite> allSprites = Map.of();
    private static final Map<TextureAtlasSprite.Ticker, Integer> tickerPages = new IdentityHashMap<>();
    private static int arrayTextureId;
    private static int atlasViewId;
    /** Spare unit for the entity/item page array. JSON samplers occupy 0 and the next few units. */
    private static final int SHARED_SAMPLER_UNIT = 12;
    private static int fallbackArrayId;
    private static final Map<Integer, Integer> sharedSamplerLocations = new HashMap<>();
    /** Programs whose fragment declares {@code MmfPages}. Litter binds its program without {@code ShaderInstance.apply}. */
    private static final Set<Integer> sharedPrograms = ConcurrentHashMap.newKeySet();
    private static boolean textureViews;
    private static boolean arrayBound;
    /** Texture id the last {@code GlStateManager._bindTexture} asked for, including a cache hit that skipped the GL call. */
    private static int glRequestedId = -1;
    private static int pageCount;
    private static int storedPageSize;
    private static boolean warnedPageLimit;
    private static boolean warnedNoViews;
    private static boolean cachedUpload;
    private static final ThreadLocal<String> COMPILING_SHADER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COMPILING_FRAGMENT = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<GlslPreprocessor> COMPILING_PREPROCESSOR = new ThreadLocal<>();
    private static ShaderInstance terrainParticleShader;
    private static ResourceManager terrainParticleManager;
    private static boolean irisAbsent;
    private static boolean loggedShaderPack;
    private static boolean loggedSodiumShader;
    private static Object irisApi;
    private static Method irisShaderPack;

    private BlockAtlasPages() {
    }

    public static boolean isBlockAtlas(ResourceLocation location) {
        return InventoryMenu.BLOCK_ATLAS.equals(location);
    }

    /**
     * Iris and Oculus shader packs sample the block atlas as one 2D texture. Paging stays off while one is active
     * so that sample still sees the atlas. A resource reload after the pack is toggled stitches again.
     */
    public static boolean shaderPackActive() {
        if (irisAbsent) {
            return false;
        }
        try {
            if (irisShaderPack == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisShaderPack = api.getMethod("isShaderPackInUse");
            }
            return Boolean.TRUE.equals(irisShaderPack.invoke(irisApi));
        } catch (ClassNotFoundException exception) {
            irisAbsent = true;
            return false;
        } catch (Throwable exception) {
            return false;
        }
    }

    public static void skipPagingForShaderPack() {
        pages = Map.of();
        pagesByName = Map.of();
        allSprites = Map.of();
        if (!loggedShaderPack) {
            loggedShaderPack = true;
            LOGGER.info("An Iris shader pack is active, so the block atlas stays one 2D texture");
        }
    }

    /**
     * Drops the texture-array id. When {@code textureId} is that array, deletes it and tells the caller to allocate a new 2D id.
     */
    public static boolean releaseArray(int textureId) {
        pageCount = 0;
        arrayBound = false;
        UPLOAD_LAYER.set(-1);
        pages = Map.of();
        pagesByName = Map.of();
        allSprites = Map.of();
        if (arrayTextureId != 0 && textureId == arrayTextureId) {
            GlStateManager._deleteTexture(textureId);
            arrayTextureId = 0;
            return true;
        }
        arrayTextureId = 0;
        return false;
    }

    public static boolean isPaging() {
        return PAGING.get();
    }

    public static boolean hasArray() {
        return arrayTextureId > 0;
    }

    /** The published atlas id is a 2D view of page 0, so its name map lists only that page. */
    public static boolean limitNamesToPageZero() {
        return textureViews && pageCount > 1;
    }

    public static int uploadLayer() {
        return UPLOAD_LAYER.get();
    }

    /** The texture array the block atlas pages were uploaded into. Zero before the stitch upload. */
    public static int arrayId() {
        return arrayTextureId;
    }

    /**
     * Keeps the sprite lookup ModernFix dynamic resources uses when it bakes a model.
     * Published when the stitch finishes, because dynamic resources bakes before the atlas upload
     * replaces {@code texturesByName}. The atlas field itself is later limited to the page whose
     * pixels that texture shows.
     */
    public static void rememberSprites(Map<ResourceLocation, TextureAtlasSprite> sprites) {
        allSprites = sprites == null ? Map.of() : Map.copyOf(sprites);
    }

    /**
     * @return the sprite dynamic resources should sample, or null when this atlas is not paged
     */
    public static TextureAtlasSprite sprite(ResourceLocation atlas, ResourceLocation name) {
        if (!isBlockAtlas(atlas) || allSprites.isEmpty() || name == null) {
            return null;
        }
        TextureAtlasSprite sprite = allSprites.get(name);
        if (sprite != null) {
            return sprite;
        }
        return allSprites.get(MissingTextureAtlasSprite.getLocation());
    }

    public static Set<ResourceLocation> spriteNames(ResourceLocation atlas) {
        if (!isBlockAtlas(atlas) || allSprites.isEmpty()) {
            return null;
        }
        return allSprites.keySet();
    }

    public static Map<ResourceLocation, TextureAtlasSprite> spritesOnPage(int page) {
        Map<ResourceLocation, TextureAtlasSprite> onPage = new HashMap<>();
        for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : allSprites.entrySet()) {
            if (pageOf(entry.getValue()) == page) {
                onPage.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(onPage);
    }

    public static int pageOf(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        Integer page = pages.get(sprite);
        if (page != null) {
            return page;
        }
        // The chest sheet copies entity/chest/normal, trapped, and ender. The decorated-pot sheet copies
        // entity/decorated_pot/decorated_pot_side. Those names also sit on later block-atlas pages.
        // Only the block-atlas sprite carries the page.
        if (!isBlockAtlas(sprite.atlasLocation())) {
            return 0;
        }
        page = pagesByName.get(sprite.contents().name());
        return page == null ? 0 : page;
    }

    public static int pageOfTicker(TextureAtlasSprite.Ticker ticker) {
        Integer page = tickerPages.get(ticker);
        return page == null ? 0 : page;
    }

    public static void rememberTicker(TextureAtlasSprite.Ticker ticker, int page) {
        tickerPages.put(ticker, page);
    }

    public static void clearTickers() {
        tickerPages.clear();
    }

    /**
     * @param localUv the sprite's 0–1 coordinate inside its page, as stored on the sprite
     * @return that coordinate shifted onto the sprite's page, held just below the next page
     */
    public static float pagedUv(TextureAtlasSprite sprite, float localUv) {
        int page = pageOf(sprite);
        if (page <= 0) {
            return holdOnPage(0, localUv);
        }
        return holdOnPage(page, localUv + page);
    }

    /**
     * Puts a baked quad's U on the sprite's page. Quads baked before the page was known, or from the
     * sprite's raw 0–1 fields, still name the right layer when the chunk mesh is built.
     * A U that is already past 1 while the sprite has no page is left alone: that value is a page
     * another bake already wrote, and clamping it would sample page 0.
     */
    public static float uvOnPage(TextureAtlasSprite sprite, float storedU) {
        int page = pageOf(sprite);
        if (page <= 0) {
            if (storedU > 1.0F) {
                return storedU;
            }
            return holdOnPage(0, storedU);
        }
        int carried = (int) Math.floor(storedU);
        if (carried == page) {
            return holdOnPage(page, storedU);
        }
        float local = carried > 0 ? storedU - carried : storedU;
        return pagedUv(sprite, local);
    }

    /**
     * Keeps a coordinate on {@code page}. A value just below the page is Forge's bleed; {@code floor}
     * would otherwise select the previous page. A value that lands on the next page is held just under
     * it. An exact integer equal to this page is the start of the page and is left alone, including
     * page 0's 0. Page 0's far edge is exactly 1, and that one value is held just under 1 so {@code floor}
     * does not select page 1.
     */
    private static float holdOnPage(int page, float uv) {
        if (uv < page) {
            uv = page;
        }
        float nextPage = page + 1.0F;
        return uv >= nextPage ? Math.nextDown(nextPage) : uv;
    }

    /** The next compact memPutByte is the material byte for this vertex. */
    public static void beginCompactVertex() {
        COMPACT_MATERIAL_BYTE.set(Boolean.TRUE);
    }

    /**
     * @return true when this byte is the material byte, which is written before the section id
     */
    public static boolean compactMaterialByte() {
        boolean material = COMPACT_MATERIAL_BYTE.get();
        COMPACT_MATERIAL_BYTE.set(!material);
        return material;
    }

    public static void captureUv(float pagedU) {
        int page = (int) Math.floor(pagedU);
        if (page < 0) {
            page = 0;
        }
        if (page >= EMBEDDIUM_PAGE_LIMIT && !warnedPageLimit) {
            warnedPageLimit = true;
            LOGGER.warn("Block atlas page {} is past the {} pages Embeddium stores in a chunk vertex", page, EMBEDDIUM_PAGE_LIMIT);
        }
        CAPTURED_PAGE.set(page);
    }

    public static int capturedPage() {
        return Math.min(CAPTURED_PAGE.get(), EMBEDDIUM_PAGE_LIMIT - 1);
    }

    /**
     * Page index in the high bits of Embeddium's compact material byte. The low three bits stay the mip
     * and alpha-cutoff flags. The vanilla-like format does not use this: its float U already carries the
     * page, and Let There Be Atmosphere keeps wind sway and glow in those high bits.
     */
    public static int materialBitsWithPage(int bits) {
        int page = capturedPage();
        if (page <= 0) {
            return bits;
        }
        return (bits & 7) | (page << 3);
    }

    /**
     * Records the page from U, then strips it. Compact vertices store a 16-bit UV, which wraps past the
     * second page, so the page has to travel in the material byte instead.
     */
    public static float chunkLocalUv(float uv) {
        noteChunkCoordinate(uv);
        return uv - (float) Math.floor(uv);
    }

    /**
     * Records the page from U and leaves it in the float. The vanilla-like format stores that float, and
     * the chunk shader selects the array layer with {@code floor}.
     */
    public static float chunkFloatUv(float uv) {
        noteChunkCoordinate(uv);
        return uv;
    }

    /**
     * {@code encodeTexture} clamps with {@code Math.min}. That clamp turns a paged U into a page-0
     * coordinate. U is the call that just cleared {@link #CHUNK_UV_IS_U}; V stays clamped.
     * The return inject calls {@link #keepEncodedPage} as well, because a missed clamp redirect would
     * still hand the shader a page-0 U.
     */
    public static float keepPagedFloat(float a, float b) {
        if (!CHUNK_UV_IS_U.get()) {
            float uv = Math.max(a, b);
            if (uv >= 1.0F) {
                return uv;
            }
        }
        return Math.min(a, b);
    }

    /**
     * @param incoming the U or V passed into {@code encodeTexture}, before its clamp
     * @param encoded the value the encoder was about to store
     * @return {@code incoming} when it already names a later page
     */
    public static float keepEncodedPage(float incoming, float encoded) {
        if (incoming >= 1.0F) {
            return incoming;
        }
        return encoded;
    }

    private static void noteChunkCoordinate(float uv) {
        if (CHUNK_UV_IS_U.get()) {
            captureUv(uv);
            CHUNK_UV_IS_U.set(Boolean.FALSE);
        } else {
            CHUNK_UV_IS_U.set(Boolean.TRUE);
        }
    }

    public static void beginLayer(int layer) {
        UPLOAD_LAYER.set(layer);
        if (arrayTextureId > 0) {
            bindTexture(GL11.GL_TEXTURE_2D, arrayTextureId);
        }
    }

    public static void endLayer() {
        UPLOAD_LAYER.set(-1);
        arrayBound = false;
    }

    /**
     * Records the texture a bind asked for. {@code _bindTexture} skips the GL call when its cache already
     * holds that id, and the skip used to leave {@link #arrayBound} set, so the next filter change was
     * applied to the page array instead of the chest, fluid, or icon texture.
     */
    public static void noteGlBind(int texture) {
        glRequestedId = texture;
        arrayBound = texture != 0 && texture == arrayTextureId;
    }

    public static void bindTexture(int target, int texture) {
        // The id the block atlas publishes is a 2D view of page 0. A patched shader reads the array
        // instead, which is where later pages were uploaded. The 2D view stays bound for a shader
        // that still samples sampler2D.
        if (texture != 0 && texture == arrayTextureId) {
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, arrayTextureId);
            arrayBound = true;
            return;
        }
        // A patched shader reads the array. An unpatched sampler2D still reads the 2D binding, so the
        // page-0 view stays bound there. Binding only the array would leave that sampler on a stale 2D texture.
        if (texture != 0 && texture == atlasViewId && pageCount > 1 && arrayTextureId != 0) {
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, arrayTextureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            arrayBound = false;
            return;
        }
        arrayBound = false;
        GL11.glBindTexture(target, texture);
    }

    /**
     * Patched block shaders sample the array. Entity and item shaders keep the page-0 view on Sampler0
     * and read later pages from {@link #bindSharedAtlas}.
     */
    public static int samplerId(ShaderInstance shader, int texture) {
        if (arrayTextureId == 0 || !textureViews || texture != atlasViewId || shader == null) {
            return texture;
        }
        String name = shader.getName();
        if (name != null && BlockAtlasShaderPatch.usesArray(name)) {
            return arrayTextureId;
        }
        return texture;
    }

    /**
     * Binds the page array for an entity or item shader that kept Sampler0 as sampler2D.
     * Called at the end of {@code ShaderInstance.apply}, while that program is current.
     * U below 1 still reads Sampler0, which is the page-0 view or a mob skin.
     */
    public static void bindSharedAtlas(ShaderInstance shader) {
        if (shader == null || !BlockAtlasShaderPatch.usesShared(shader.getName())) {
            return;
        }
        bindSharedProgram(shader.getId());
    }

    /**
     * Binds {@code MmfPages} for a program that kept its 2D samplers. {@code ShaderInstance.apply} reaches
     * this through {@link #bindSharedAtlas}. Let There Be Atmosphere calls {@code glUseProgram} itself, so
     * the same bind also runs from there while that program is current.
     */
    public static void bindSharedProgram(int program) {
        if (program == 0 || !sharedPrograms.contains(program)) {
            return;
        }
        Integer cached = sharedSamplerLocations.get(program);
        int location = cached != null ? cached : GL20.glGetUniformLocation(program, "MmfPages");
        if (cached == null) {
            sharedSamplerLocations.put(program, location);
        }
        if (location < 0) {
            return;
        }
        int previous = GlStateManager._getActiveTexture();
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + SHARED_SAMPLER_UNIT);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, sharedArrayTexture());
            GL20.glUniform1i(location, SHARED_SAMPLER_UNIT);
        } finally {
            GlStateManager._activeTexture(previous);
        }
    }

    public static void noteSharedProgram(ShaderInstance shader) {
        if (shader == null || !BlockAtlasShaderPatch.usesShared(shader.getName())) {
            return;
        }
        int program = shader.getId();
        if (program != 0) {
            sharedPrograms.add(program);
        }
    }

    public static void forgetSharedProgram(ShaderInstance shader) {
        if (shader == null) {
            return;
        }
        int program = shader.getId();
        sharedPrograms.remove(program);
        sharedSamplerLocations.remove(program);
    }

    private static int sharedArrayTexture() {
        if (arrayTextureId != 0) {
            return arrayTextureId;
        }
        if (fallbackArrayId == 0) {
            fallbackArrayId = GL11.glGenTextures();
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, fallbackArrayId);
            GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL11.GL_RGBA8, 1, 1, 1);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        }
        return fallbackArrayId;
    }

    public static void bindChunkTexture(int texture) {
        if (arrayTextureId != 0 && (texture == atlasViewId || texture == arrayTextureId)) {
            bindTexture(GL11.GL_TEXTURE_2D, arrayTextureId);
        } else {
            bindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    public static void filterArrayIfAtlas(int boundId, boolean blur, boolean mipmap) {
        if (arrayTextureId == 0 || boundId != atlasViewId || arrayTextureId == atlasViewId) {
            return;
        }
        int min = mipmap ? (blur ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR) : (blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        int mag = blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        // Named by texture id, so the chest or fluid texture that is bound on the 2D target is left alone.
        if (GL.getCapabilities().OpenGL45) {
            GL45.glTextureParameteri(arrayTextureId, GL11.GL_TEXTURE_MIN_FILTER, min);
            GL45.glTextureParameteri(arrayTextureId, GL11.GL_TEXTURE_MAG_FILTER, mag);
            return;
        }
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, arrayTextureId);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, min);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, mag);
    }

    public static int parameterTarget(int target) {
        if (target != GL11.GL_TEXTURE_2D) {
            return target;
        }
        // A page upload writes the array. Otherwise only a bind of the array id itself retargets parameters.
        // A cached bind of a chest, fluid, or icon texture must not retarget them onto the array.
        if (uploadLayer() >= 0 || (arrayTextureId != 0 && glRequestedId == arrayTextureId)) {
            return GL30.GL_TEXTURE_2D_ARRAY;
        }
        return target;
    }

    public static void prepareReload(int currentAtlasId) {
        // allSprites stays. Dynamic resources bakes on other threads while this upload runs,
        // and the stitch already published the sprites those bakes have to sample.
        if (arrayTextureId != 0 && arrayTextureId != currentAtlasId) {
            GlStateManager._deleteTexture(arrayTextureId);
        }
        arrayTextureId = 0;
        atlasViewId = 0;
        textureViews = false;
        arrayBound = false;
        cachedUpload = false;
        pageCount = 0;
        sharedSamplerLocations.clear();
    }

    public static void allocateStorage(int pagesToAllocate, int size, int mipLevel) {
        pageCount = Math.max(1, pagesToAllocate);
        storedPageSize = size;
        arrayTextureId = GL11.glGenTextures();
        bindTexture(GL11.GL_TEXTURE_2D, arrayTextureId);
        int target = GL30.GL_TEXTURE_2D_ARRAY;
        int depth = pageCount;
        int levels = mipLevel + 1;
        GL42.glTexStorage3D(target, levels, GL11.GL_RGBA8, size, size, depth);
        int min = mipLevel > 0 ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, min);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL, mipLevel);
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
    }

    public static boolean uploadCached(int pagesToAllocate, int size, int mipLevel) {
        if (!cachedUpload) {
            return false;
        }
        try {
            allocateStorage(pagesToAllocate, size, mipLevel);
            int rowsPerChunk = 64;
            ByteBuffer buffer = ByteBuffer.allocateDirect(rowsPerChunk * size * 4);
            for (int page = 0; page < pageCount; page++) {
                Path file = BlockAtlasPageCache.pageFile(page);
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                    for (int y = 0; y < size; y += rowsPerChunk) {
                        int rows = Math.min(rowsPerChunk, size - y);
                        buffer.clear();
                        buffer.limit(rows * size * 4);
                        long offset = (long) y * size * 4L;
                        while (buffer.hasRemaining()) {
                            int read = channel.read(buffer, offset + buffer.position());
                            if (read < 0) {
                                throw new java.io.EOFException(file.toString());
                            }
                        }
                        buffer.flip();
                        GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, y, page, size, rows, 1,
                                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                    }
                }
            }
            if (mipLevel > 0) {
                GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
            }
            LOGGER.info("Uploaded {} cached block atlas page(s)", pageCount);
            return true;
        } catch (java.io.IOException | RuntimeException exception) {
            LOGGER.warn("Cached block atlas pages could not be uploaded, uploading sprites instead", exception);
            cachedUpload = false;
            if (arrayTextureId != 0) {
                GlStateManager._deleteTexture(arrayTextureId);
                arrayTextureId = 0;
            }
            return false;
        }
    }

    /**
     * @return the texture id the block atlas should expose. With texture views this is a 2D view of page 0.
     */
    public static int finishViews(int size, int mipLevel) {
        boolean views = GL.getCapabilities().OpenGL43;
        int levels = mipLevel + 1;
        if (views) {
            try {
                atlasViewId = createView(0, levels);
                textureViews = true;
                LOGGER.info("Block atlas page 0 is a 2D texture view of the page array");
                return atlasViewId;
            } catch (RuntimeException exception) {
                LOGGER.warn("Texture views for the block atlas failed", exception);
            }
        }
        if (!warnedNoViews) {
            warnedNoViews = true;
            LOGGER.warn("This GPU cannot make a 2D view of block atlas page 0, so a shader that samples the block atlas as one 2D image will not see it");
        }
        textureViews = false;
        atlasViewId = arrayTextureId;
        return arrayTextureId;
    }

    private static int createView(int layer, int levels) {
        int view = GL11.glGenTextures();
        GL43.glTextureView(view, GL11.GL_TEXTURE_2D, arrayTextureId, GL11.GL_RGBA8, 0, levels, layer, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, view);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, levels > 1 ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels - 1);
        return view;
    }

    public static boolean dumpAtlas(ResourceLocation id, Path directory) {
        if (!isBlockAtlas(id) || arrayTextureId == 0 || textureViews) {
            return false;
        }
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int framebuffer = GL30.glGenFramebuffers();
        try {
            Files.createDirectories(directory);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            for (int page = 0; page < pageCount; page++) {
                GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, arrayTextureId, 0, page);
                try (NativeImage image = new NativeImage(storedPageSize, storedPageSize, false)) {
                    long pixels = ((com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas.NativeImagePixelsAccessor) (Object) image)
                            .mysticmonstrosityfixes$pixels();
                    GL11.glReadPixels(0, 0, storedPageSize, storedPageSize, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    image.flipY();
                    image.writeToFile(directory.resolve(id.toDebugFileName() + "_page" + page + "_0.png"));
                }
            }
            writeSpritePages(directory, id);
            return true;
        } catch (java.io.IOException | RuntimeException exception) {
            LOGGER.warn("Could not dump block atlas pages", exception);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            GL30.glDeleteFramebuffers(framebuffer);
        }
    }

    public static void writeSpritePages(Path directory, ResourceLocation id) {
        if (!isBlockAtlas(id) || pages.isEmpty()) {
            return;
        }
        Path file = directory.resolve(id.toDebugFileName() + "_pages.txt");
        try {
            Files.createDirectories(directory);
            List<String> lines = new ArrayList<>();
            for (Map.Entry<TextureAtlasSprite, Integer> entry : pages.entrySet()) {
                TextureAtlasSprite sprite = entry.getKey();
                lines.add(sprite.contents().name() + "\tpage=" + entry.getValue()
                        + "\tx=" + sprite.getX() + "\ty=" + sprite.getY()
                        + "\tw=" + sprite.contents().width() + "\th=" + sprite.contents().height());
            }
            lines.sort(String::compareTo);
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            LOGGER.warn("Could not write block atlas page list", exception);
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean OVERFLOW_FILTER = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * ModernFix logs the overflow from its own stitcher mixin, which Mixin will not let another
     * mixin target. The filter sits on ModernFix's own logger, which is what that call checks.
     */
    private static void quietExpectedOverflow() {
        if (!OVERFLOW_FILTER.compareAndSet(false, true)) {
            return;
        }
        try {
            org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger)
                    org.apache.logging.log4j.LogManager.getLogger("ModernFix");
            logger.addFilter(new AtlasOverflowLogFilter());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not quiet the expected block atlas overflow line", exception);
        }
    }

    public static SpriteLoader.Preparations stitch(ResourceLocation location, int maxGpuSize, List<SpriteContents> sprites, int requestedMip, Executor executor) {
        int pageSize = pageSize(maxGpuSize);
        int mipLevel = chooseMipLevel(location, sprites, requestedMip);
        PAGING.set(true);
        quietExpectedOverflow();
        cachedUpload = false;
        try {
            BlockAtlasPageCache.Lookup lookup = BlockAtlasPageCache.lookup(sprites, pageSize, mipLevel);
            if (lookup.hit() != null) {
                cachedUpload = true;
                return preparationsFrom(location, pageSize, mipLevel, sprites, lookup.hit(), executor);
            }
            List<Stitcher<SpriteContents>> stitched = new ArrayList<>();
            List<List<SpriteContents>> pending = new ArrayList<>();
            pending.add(new ArrayList<>(sprites));
            while (!pending.isEmpty()) {
                List<SpriteContents> batch = pending.remove(pending.size() - 1);
                Stitcher<SpriteContents> stitcher = new Stitcher<>(pageSize, pageSize, mipLevel);
                for (SpriteContents sprite : batch) {
                    stitcher.registerSprite(sprite);
                }
                try {
                    stitcher.stitch();
                    stitched.add(stitcher);
                } catch (StitcherException exception) {
                    Set<SpriteContents> placed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                    stitcher.gatherSprites((entry, x, y) -> placed.add(entry));
                    if (!placed.isEmpty()) {
                        stitched.add(stitcher);
                        List<SpriteContents> overflow = new ArrayList<>();
                        for (SpriteContents sprite : batch) {
                            if (!placed.contains(sprite)) {
                                overflow.add(sprite);
                            }
                        }
                        if (!overflow.isEmpty()) {
                            pending.add(overflow);
                        }
                        continue;
                    }
                    if (batch.size() <= 1) {
                        throw stitchFailure(exception, pageSize);
                    }
                    // ModernFix's fast stitcher throws without recording partial placements
                    // when the whole batch is larger than one page. Split and stitch again.
                    int mid = batch.size() / 2;
                    pending.add(new ArrayList<>(batch.subList(mid, batch.size())));
                    pending.add(new ArrayList<>(batch.subList(0, mid)));
                }
            }
            if (stitched.isEmpty()) {
                Stitcher<SpriteContents> empty = new Stitcher<>(pageSize, pageSize, mipLevel);
                empty.stitch();
                stitched.add(empty);
            }

            Map<TextureAtlasSprite, Integer> builtPages = new IdentityHashMap<>();
            Map<ResourceLocation, TextureAtlasSprite> regions = new HashMap<>();
            List<BlockAtlasPageCache.Placed> placed = new ArrayList<>();
            int pageIndex = 0;
            for (Stitcher<SpriteContents> stitcher : stitched) {
                int page = pageIndex++;
                stitcher.gatherSprites((contents, x, y) -> {
                    placed.add(new BlockAtlasPageCache.Placed(contents, page, x, y));
                    TextureAtlasSprite sprite = spriteAt(location, contents, pageSize, x, y);
                    builtPages.put(sprite, page);
                    regions.put(contents.name(), sprite);
                });
            }
            assignPages(builtPages);
            warnedPageLimit = false;
            BlockAtlasPageCache.save(placed, pageSize, mipLevel, stitched.size(), lookup.hash());
            LOGGER.info("Stitched {} block sprites into {} page(s) of {}x{}", regions.size(), stitched.size(), pageSize, pageSize);
            return ready(location, pageSize, mipLevel, regions, executor);
        } finally {
            PAGING.set(false);
        }
    }

    public static ShaderInstance terrainParticleShader() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (terrainParticleShader == null || terrainParticleManager != manager) {
            if (terrainParticleShader != null) {
                terrainParticleShader.close();
            }
            try {
                terrainParticleShader = new ShaderInstance(manager,
                        new ResourceLocation("mysticmonstrosityfixes", "mmf_block_particle"),
                        DefaultVertexFormat.PARTICLE);
            } catch (IOException exception) {
                throw new ReportedException(CrashReport.forThrowable(exception, "Loading block atlas particle shader"));
            }
            terrainParticleManager = manager;
        }
        return terrainParticleShader;
    }

    public static void beginCompile(com.mojang.blaze3d.shaders.Program.Type type, String name, GlslPreprocessor preprocessor) {
        COMPILING_SHADER.set(name);
        COMPILING_FRAGMENT.set(type == com.mojang.blaze3d.shaders.Program.Type.FRAGMENT);
        COMPILING_PREPROCESSOR.set(preprocessor);
    }

    public static void endCompile() {
        COMPILING_SHADER.remove();
        COMPILING_FRAGMENT.remove();
        COMPILING_PREPROCESSOR.remove();
    }

    /**
     * Patches block-atlas fragment shaders after imports are expanded, so a shader or an import added
     * later still samples the page carried in U. An active shader pack keeps the 2D sample.
     * A shader this cannot rewrite falls back to the paged copy shipped with this mod.
     */
    public static java.util.List<String> rewriteProcessed(java.util.List<String> processed) {
        boolean fragment = Boolean.TRUE.equals(COMPILING_FRAGMENT.get());
        String name = COMPILING_SHADER.get();
        if (!fragment || processed == null) {
            return processed;
        }
        if (shaderPackActive()) {
            BlockAtlasShaderPatch.forget(name);
            BlockAtlasShaderPatch.forgetShared(name);
            return processed;
        }
        if ("litter_solid".equals(BlockAtlasShaderPatch.simpleName(name))) {
            BlockAtlasShaderPatch.forget(name);
            BlockAtlasShaderPatch.Fragment litter = BlockAtlasShaderPatch.patchLitterAtlas(String.join("\n", processed));
            if (litter.status() == BlockAtlasShaderPatch.Status.PATCHED) {
                BlockAtlasShaderPatch.rememberShared(name);
                LOGGER.info("Patched Let There Be Atmosphere litter shader {} to sample block atlas pages from U", name);
                return java.util.List.of(litter.source());
            }
            BlockAtlasShaderPatch.forgetShared(name);
            if (litter.status() == BlockAtlasShaderPatch.Status.FAILED) {
                LOGGER.warn("Could not patch Let There Be Atmosphere litter shader {} for paged sampling", name);
            }
            return processed;
        }
        if (BlockAtlasShaderPatch.isSharedAtlasShader(name)) {
            BlockAtlasShaderPatch.forget(name);
            BlockAtlasShaderPatch.Fragment shared = BlockAtlasShaderPatch.patchSharedFragment(String.join("\n", processed));
            if (shared.status() == BlockAtlasShaderPatch.Status.PATCHED) {
                BlockAtlasShaderPatch.rememberShared(name);
                LOGGER.info("Patched shared block atlas shader {} to sample later pages from U", name);
                return java.util.List.of(shared.source());
            }
            BlockAtlasShaderPatch.forgetShared(name);
            if (shared.status() == BlockAtlasShaderPatch.Status.FAILED) {
                LOGGER.warn("Could not patch block atlas entity shader {} for paged sampling", name);
            }
            return processed;
        }
        BlockAtlasShaderPatch.forgetShared(name);
        if (!BlockAtlasShaderPatch.isBlockAtlasShader(name)) {
            BlockAtlasShaderPatch.forget(name);
            return processed;
        }
        BlockAtlasShaderPatch.Fragment patched = BlockAtlasShaderPatch.patchFragment(String.join("\n", processed));
        if (patched.status() == BlockAtlasShaderPatch.Status.FAILED) {
            String shipped = shippedShader(name);
            GlslPreprocessor preprocessor = COMPILING_PREPROCESSOR.get();
            if (shipped == null || preprocessor == null) {
                LOGGER.warn("Could not patch block atlas shader {} for paged sampling", name);
                BlockAtlasShaderPatch.forget(name);
                return processed;
            }
            LOGGER.info("Block atlas shader {} could not be patched in place, using the shipped paged copy", name);
            processed = preprocessor.process(shipped);
            patched = BlockAtlasShaderPatch.patchFragment(String.join("\n", processed));
        }
        if (patched.status() == BlockAtlasShaderPatch.Status.PATCHED) {
            BlockAtlasShaderPatch.remember(name);
            LOGGER.info("Patched block atlas shader {} to sample pages from U", name);
            return java.util.List.of(patched.source());
        }
        BlockAtlasShaderPatch.forget(name);
        return processed;
    }

    private static String shippedShader(String name) {
        String simple = BlockAtlasShaderPatch.simpleName(name);
        if (!PAGED_SHADERS.contains(simple) || "mmf_block_particle".equals(simple)) {
            return null;
        }
        String file = simple + ".fsh";
        try (InputStream input = BlockAtlasPages.class.getResourceAsStream("/assets/mysticmonstrosityfixes/shaders/core/" + file)) {
            if (input == null) {
                return null;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not read paged shader {}", file, exception);
            return null;
        }
    }

    /**
     * Same rewrite as {@link #rewriteSodiumShader}, for the shader text after includes are inlined.
     * A miss is not logged: includes and unrelated shaders share this call.
     */
    public static String rewriteParsedSodiumShader(String source) {
        if (source == null) {
            return null;
        }
        return rewriteSodiumShader(new ResourceLocation("sodium", "include/parsed"), source);
    }

    public static String rewriteSodiumShader(ResourceLocation location, String source) {
        if (shaderPackActive()) {
            return source;
        }
        String path = location.getPath();
        boolean blockLayer = path.contains("block_layer");
        if (source.contains("sampler2DArray u_BlockTex") || source.contains("v_AtlasPage")) {
            return source;
        }
        if (source.contains("uniform sampler2D u_BlockTex;") && source.contains("texture(u_BlockTex, v_TexCoord, v_MaterialMipBias)")) {
            String rewritten = source.replace("uniform sampler2D u_BlockTex;", "uniform sampler2DArray u_BlockTex;\nflat in uint v_AtlasPage;");
            rewritten = rewritten.replace(
                    "texture(u_BlockTex, v_TexCoord, v_MaterialMipBias)",
                    "texture(u_BlockTex, vec3(v_TexCoord, float(v_AtlasPage)), v_MaterialMipBias)");
            noteSodiumRewrite(source, rewritten, location);
            return rewritten;
        }
        // 2.7.0 keeps its night-vibrancy uniform in this file. feature/modcompat moves that uniform
        // into light.glsl. Both still copy the diffuse UV with this assignment.
        // The assignment itself stays, so a mod that anchors on it still matches. The page is floor of the
        // float U. Compact vertices have already stripped that floor, so they still read the material byte.
        if (source.contains("out vec2 v_TexCoord;") && source.contains("v_TexCoord = _vert_tex_diffuse_coord;")) {
            String rewritten = source.replace("out vec2 v_TexCoord;", "out vec2 v_TexCoord;\nflat out uint v_AtlasPage;");
            rewritten = rewritten.replace(
                    "v_TexCoord = _vert_tex_diffuse_coord;",
                    "v_TexCoord = _vert_tex_diffuse_coord;\n"
                            + "    #ifdef USE_VERTEX_COMPRESSION\n"
                            + "    v_AtlasPage = (_material_params >> 3u) & 31u;\n"
                            + "    #else\n"
                            + "    v_AtlasPage = uint(clamp(floor(_vert_tex_diffuse_coord.x), 0.0, 31.0));\n"
                            + "    v_TexCoord.x = _vert_tex_diffuse_coord.x - floor(_vert_tex_diffuse_coord.x);\n"
                            + "    #endif");
            noteSodiumRewrite(source, rewritten, location);
            return rewritten;
        }
        if (blockLayer) {
            noteSodiumRewrite(source, source, location);
        }
        return source;
    }

    private static void noteSodiumRewrite(String source, String rewritten, ResourceLocation location) {
        if (rewritten.equals(source) && !loggedSodiumShader) {
            loggedSodiumShader = true;
            LOGGER.warn("Embeddium chunk shader {} has no block-atlas page hook, so chunk meshes stay on page 0", location);
        }
    }

    public static int pageCount() {
        return pageCount;
    }

    private static void assignPages(Map<TextureAtlasSprite, Integer> builtPages) {
        Map<ResourceLocation, Integer> names = new HashMap<>();
        for (Map.Entry<TextureAtlasSprite, Integer> entry : builtPages.entrySet()) {
            names.put(entry.getKey().contents().name(), entry.getValue());
        }
        pages = builtPages;
        pagesByName = Map.copyOf(names);
    }

    private static TextureAtlasSprite spriteAt(ResourceLocation location, SpriteContents contents, int pageSize, int x, int y) {
        TextureAtlasSprite sprite = ForgeHooksClient.loadTextureAtlasSprite(
                location, contents, pageSize, pageSize, x, y, contents.byMipLevel.length - 1);
        if (sprite == null) {
            sprite = TextureAtlasSpriteInvoker.mysticmonstrosityfixes$create(location, contents, pageSize, pageSize, x, y);
        }
        return sprite;
    }

    private static SpriteLoader.Preparations preparationsFrom(ResourceLocation location, int pageSize, int mipLevel,
                                                               List<SpriteContents> sprites, BlockAtlasPageCache.Hit hit,
                                                               Executor executor) {
        Map<TextureAtlasSprite, Integer> builtPages = new IdentityHashMap<>();
        Map<ResourceLocation, TextureAtlasSprite> regions = new HashMap<>();
        for (SpriteContents contents : sprites) {
            BlockAtlasPageCache.Placement placement = hit.placements().get(contents.name());
            TextureAtlasSprite sprite = spriteAt(location, contents, pageSize, placement.x(), placement.y());
            builtPages.put(sprite, placement.page());
            regions.put(contents.name(), sprite);
        }
        assignPages(builtPages);
        warnedPageLimit = false;
        LOGGER.info("Placed {} block sprites from the disk cache onto {} page(s) of {}x{}", regions.size(), hit.pages(), pageSize, pageSize);
        return ready(location, pageSize, mipLevel, regions, executor);
    }

    private static SpriteLoader.Preparations ready(ResourceLocation location, int pageSize, int mipLevel,
                                                    Map<ResourceLocation, TextureAtlasSprite> regions, Executor executor) {
        rememberSprites(regions);
        TextureAtlasSprite missing = regions.get(MissingTextureAtlasSprite.getLocation());
        CompletableFuture<Void> future;
        if (mipLevel > 0) {
            int mip = mipLevel;
            future = CompletableFuture.runAsync(() -> {
                for (TextureAtlasSprite sprite : regions.values()) {
                    sprite.contents().increaseMipLevel(mip);
                }
            }, executor);
        } else {
            future = CompletableFuture.completedFuture(null);
        }
        return new SpriteLoader.Preparations(pageSize, pageSize, mipLevel, missing, regions, future);
    }

    private static int pageSize(int maxGpuSize) {
        int capped = Math.min(PAGE_SIZE, Math.max(1, maxGpuSize));
        int power = Integer.highestOneBit(capped);
        if (power < PAGE_SIZE) {
            LOGGER.warn("This GPU's max texture size is {} so block atlas pages are {}x{}", maxGpuSize, power, power);
        }
        return power;
    }

    private static int chooseMipLevel(ResourceLocation location, List<SpriteContents> sprites, int requestedMip) {
        int smallest = Integer.MAX_VALUE;
        int mipTexel = 1 << requestedMip;
        for (SpriteContents sprite : sprites) {
            smallest = Math.min(smallest, Math.min(sprite.width(), sprite.height()));
            int lowest = Math.min(Integer.lowestOneBit(sprite.width()), Integer.lowestOneBit(sprite.height()));
            if (lowest < mipTexel) {
                LOGGER.warn("Texture {} with size {}x{} limits mip level from {} to {}",
                        sprite.name(), sprite.width(), sprite.height(), Mth.log2(mipTexel), Mth.log2(lowest));
                mipTexel = lowest;
            }
        }
        if (sprites.isEmpty()) {
            return requestedMip;
        }
        int fromSmallest = Mth.log2(Math.min(smallest, mipTexel));
        if (fromSmallest < requestedMip && net.minecraftforge.common.ForgeConfig.CLIENT.allowMipmapLowering()) {
            LOGGER.warn("{}: dropping miplevel from {} to {}, because of minimum power of two: {}",
                    location, requestedMip, fromSmallest, Math.min(smallest, mipTexel));
            return fromSmallest;
        }
        return requestedMip;
    }

    private static ReportedException stitchFailure(StitcherException exception, int pageSize) {
        CrashReport report = CrashReport.forThrowable(exception, "Stitching");
        CrashReportCategory category = report.addCategory("Stitcher");
        category.setDetail("Sprites", exception.getAllSprites().stream().map(entry ->
                String.format(Locale.ROOT, "%s[%dx%d]", entry.name(), entry.width(), entry.height())
        ).collect(Collectors.joining(",")));
        category.setDetail("Page Size", pageSize);
        return new ReportedException(report);
    }
}
