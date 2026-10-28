package com.name.mysticmonstrosityfixes;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas.NativeImagePixelsAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stores each stitched block-atlas page as raw RGBA so the next boot can upload the page in a few
 * slices instead of packing every sprite again. The key is the mods, resource packs, page size,
 * mip level, and the sprite images. A sprite, pack, or mod that was not in that key rebuilds the pages.
 */
public final class BlockAtlasPageCache {
    public record Placement(int page, int x, int y) {
    }

    public record Placed(SpriteContents contents, int page, int x, int y) {
    }

    public record Hit(Map<ResourceLocation, Placement> placements, int pages) {
    }

    public record Lookup(byte[] hash, Hit hit) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x4D4D4632;
    private static final int VERSION = 1;
    private static final long MAX_CACHE_BYTES = 8L << 30;

    private BlockAtlasPageCache() {
    }

    public static Lookup lookup(List<SpriteContents> sprites, int pageSize, int mip) {
        byte[] hash = hash(sprites, pageSize, mip);
        Path layout = directory().resolve("blocks.layout");
        boolean stored = Files.isRegularFile(layout);
        if (hash == null || !stored) {
            return miss(hash, stored);
        }
        Lookup loaded = null;
        boolean rebuild = false;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(layout))) {
            loaded = readLayout(in, hash, sprites, pageSize, mip);
            rebuild = loaded == null;
        } catch (IOException exception) {
            LOGGER.warn("Could not read the block atlas page cache", exception);
            rebuild = true;
        }
        if (rebuild) {
            return miss(hash, true);
        }
        return loaded;
    }

    private static Lookup readLayout(DataInputStream in, byte[] hash, List<SpriteContents> sprites, int pageSize, int mip) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION) {
            return null;
        }
        int storedPage = in.readInt();
        int storedMip = in.readInt();
        int pages = in.readInt();
        int count = in.readInt();
        byte[] storedHash = in.readNBytes(32);
        if (storedPage != pageSize || storedMip != mip || pages < 1 || !java.util.Arrays.equals(hash, storedHash)) {
            return null;
        }
        long pageBytes = (long) pageSize * pageSize * 4L;
        for (int page = 0; page < pages; page++) {
            Path file = pageFile(page);
            if (!Files.isRegularFile(file) || Files.size(file) != pageBytes) {
                return null;
            }
        }
        Map<ResourceLocation, Placement> placements = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation name = new ResourceLocation(in.readUTF());
            Placement placement = new Placement(in.readInt(), in.readInt(), in.readInt());
            if (placement.page() < 0 || placement.page() >= pages || placements.put(name, placement) != null) {
                return null;
            }
        }
        if (placements.size() != sprites.size()) {
            return null;
        }
        for (SpriteContents sprite : sprites) {
            if (!placements.containsKey(sprite.name())) {
                return null;
            }
        }
        LOGGER.info("Loaded {} cached block atlas page(s) from {}", pages, directory());
        return new Lookup(hash, new Hit(placements, pages));
    }

    private static Lookup miss(byte[] hash, boolean stored) {
        if (stored) {
            LOGGER.info("Block atlas pages no longer match the sprites, mods, or resource packs, so they will be rebuilt");
            discard();
        }
        return new Lookup(hash == null ? new byte[0] : hash, null);
    }

    /**
     * Deletes saved pages. The next stitch writes pages for the sprites loaded now.
     */
    public static void discard() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (name.startsWith("blocks-page-") || name.startsWith("blocks.layout")) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not discard stale block atlas pages", exception);
        }
    }

    public static void save(List<Placed> placed, int pageSize, int mip, int pages, byte[] hash) {
        if (hash == null || hash.length != 32 || placed.isEmpty()) {
            return;
        }
        long pageBytes = (long) pageSize * pageSize * 4L;
        long total = pageBytes * pages;
        if (total <= 0 || total > MAX_CACHE_BYTES) {
            LOGGER.warn("Skipping the block atlas page cache because {} page(s) would use {} bytes", pages, total);
            return;
        }
        long started = System.nanoTime();
        try {
            Files.createDirectories(directory());
            for (int page = 0; page < pages; page++) {
                writePage(placed, page, pageSize, pageFile(page));
            }
            Path layout = directory().resolve("blocks.layout");
            Path temp = directory().resolve("blocks.layout.tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(pageSize);
                out.writeInt(mip);
                out.writeInt(pages);
                out.writeInt(placed.size());
                out.write(hash);
                for (Placed sprite : placed) {
                    out.writeUTF(sprite.contents().name().toString());
                    out.writeInt(sprite.page());
                    out.writeInt(sprite.x());
                    out.writeInt(sprite.y());
                }
            }
            Files.move(temp, layout, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            deleteExtraPages(pages);
            LOGGER.info("Saved {} block atlas page(s) to {} in {} ms", pages, directory(), (System.nanoTime() - started) / 1_000_000L);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not save the block atlas page cache", exception);
        }
    }

    /**
     * The cache copies the top of the original image at the sprite's frame size. Vanilla frames sit
     * in that rectangle. A Fusion sheet is a different size, and each tile uploads its own rectangle,
     * so those sprites are left out of the cache and uploaded on their own.
     */
    public static boolean storesSprite(SpriteContents contents) {
        if (contents.getClass() != SpriteContents.class) {
            return false;
        }
        NativeImage image = contents.getOriginalImage();
        return image.getWidth() == contents.width() && image.getHeight() >= contents.height();
    }

    public static Path pageFile(int page) {
        return directory().resolve("blocks-page-" + page + ".rgba");
    }

    private static void writePage(List<Placed> placed, int page, int pageSize, Path file) throws IOException {
        long pageBytes = (long) pageSize * pageSize * 4L;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer zeros = ByteBuffer.allocateDirect(1024 * 1024);
            long position = 0;
            while (position < pageBytes) {
                zeros.clear();
                int chunk = (int) Math.min(zeros.capacity(), pageBytes - position);
                zeros.limit(chunk);
                while (zeros.hasRemaining()) {
                    position += channel.write(zeros, position);
                }
            }
            ByteBuffer row = ByteBuffer.allocateDirect(pageSize * 4);
            for (Placed sprite : placed) {
                if (sprite.page() != page) {
                    continue;
                }
                if (!storesSprite(sprite.contents())) {
                    continue;
                }
                NativeImage image = sprite.contents().getOriginalImage();
                if (image.format() != NativeImage.Format.RGBA) {
                    throw new IOException("sprite " + sprite.contents().name() + " is " + image.format());
                }
                long pixels = ((NativeImagePixelsAccessor) (Object) image).mysticmonstrosityfixes$pixels();
                int stride = image.getWidth() * 4;
                int rowBytes = sprite.contents().width() * 4;
                for (int y = 0; y < sprite.contents().height(); y++) {
                    row.clear();
                    MemoryUtil.memCopy(pixels + (long) y * stride, MemoryUtil.memAddress(row), rowBytes);
                    row.limit(rowBytes);
                    long at = ((long) (sprite.y() + y) * pageSize + sprite.x()) * 4L;
                    while (row.hasRemaining()) {
                        at += channel.write(row, at);
                    }
                }
            }
            channel.force(false);
        }
    }

    private static byte[] hash(List<SpriteContents> sprites, int pageSize, int mip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateInt(digest, pageSize);
            updateInt(digest, mip);
            List<String> rows = new ArrayList<>();
            LoadingModList mods = LoadingModList.get();
            if (mods != null) {
                for (ModFileInfo info : mods.getModFiles()) {
                    Path path = info.getFile().getFilePath();
                    if (Files.isRegularFile(path)) {
                        rows.add(path.getFileName() + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis());
                    }
                }
            }
            Path packs = FMLPaths.GAMEDIR.get().resolve("resourcepacks");
            if (Files.isDirectory(packs)) {
                fingerprintPacks(rows, packs);
            }
            Collections.sort(rows);
            for (String row : rows) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            updateInt(digest, sprites.size());
            List<SpriteContents> ordered = new ArrayList<>(sprites);
            ordered.sort(java.util.Comparator.comparing(sprite -> sprite.name().toString()));
            for (SpriteContents sprite : ordered) {
                digest.update((byte) 0);
                digest.update(sprite.name().toString().getBytes(StandardCharsets.UTF_8));
                updateInt(digest, sprite.width());
                updateInt(digest, sprite.height());
                int[] frames = sprite.getUniqueFrames().toArray();
                updateInt(digest, frames.length);
                for (int frame : frames) {
                    updateInt(digest, frame);
                }
                NativeImage image = sprite.getOriginalImage();
                updateInt(digest, image.getWidth());
                updateInt(digest, image.getHeight());
                if (image.format() != NativeImage.Format.RGBA) {
                    return null;
                }
                long pixels = ((NativeImagePixelsAccessor) (Object) image).mysticmonstrosityfixes$pixels();
                int bytes = image.getWidth() * image.getHeight() * 4;
                ByteBuffer view = MemoryUtil.memByteBuffer(pixels, bytes);
                digest.update(view);
            }
            return digest.digest();
        } catch (Exception exception) {
            LOGGER.warn("Could not fingerprint the block atlas", exception);
            return null;
        }
    }

    private static void fingerprintPacks(List<String> rows, Path packs) throws IOException {
        List<Path> children;
        try (Stream<Path> stream = Files.list(packs)) {
            children = stream.toList();
        }
        for (Path path : children) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        rows.add(fileRow("pack", path.getFileName() + "/" + path.relativize(file), file));
                    }
                }
            } else if (Files.isRegularFile(path)) {
                rows.add(fileRow("pack", path.getFileName().toString(), path));
            }
        }
    }

    private static String fileRow(String kind, String label, Path path) throws IOException {
        return kind + ":" + label + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
    }

    private static void deleteExtraPages(int pages) throws IOException {
        try (Stream<Path> stream = Files.list(directory())) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("blocks-page-") || !name.endsWith(".rgba")) {
                    continue;
                }
                String number = name.substring("blocks-page-".length(), name.length() - ".rgba".length());
                try {
                    if (Integer.parseInt(number) >= pages) {
                        Files.deleteIfExists(path);
                    }
                } catch (NumberFormatException ignored) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static Path directory() {
        return FMLPaths.GAMEDIR.get().resolve("mysticmonstrosityfixes_cache").resolve("atlas");
    }
}
