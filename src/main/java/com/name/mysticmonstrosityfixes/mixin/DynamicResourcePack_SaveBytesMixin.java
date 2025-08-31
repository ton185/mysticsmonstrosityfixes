package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.util.Entry;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// --- imports you likely already have ---
import java.util.zip.CRC32;
import java.util.*;
import com.google.gson.*; // add gson to your mod deps if not already present

@Mixin(value = DynamicResourcePack.class, remap = false)
public abstract class DynamicResourcePack_SaveBytesMixin {
    @Unique private static final Set<Path> CREATED_DIRS = ConcurrentHashMap.newKeySet();

    // ===== manifest bits =====
    @Unique private static final Path MANIFEST_DIR  = Paths.get(".cache","moonlight-debug");
    @Unique private static final Path MANIFEST_FILE = MANIFEST_DIR.resolve("manifest.json");
    @Unique private static final ConcurrentHashMap<String, Entry> MANIFEST = new ConcurrentHashMap<>();
    @Unique private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    @Unique private static volatile boolean MANIFEST_LOADED = false;

    @Unique private static void mysticmonstrosityfixes$loadManifestIfNeeded() {
        if (MANIFEST_LOADED) return;
        synchronized (MANIFEST) {
            if (MANIFEST_LOADED) return;
            try {
                Files.createDirectories(MANIFEST_DIR);
                if (Files.exists(MANIFEST_FILE)) {
                    try (var r = Files.newBufferedReader(MANIFEST_FILE)) {
                        var type = new com.google.gson.reflect.TypeToken<Map<String,Entry>>(){}.getType();
                        Map<String,Entry> m = GSON.fromJson(r, type);
                        if (m != null) MANIFEST.putAll(m);
                    }
                }
            } catch (IOException ignored) {}
            MANIFEST_LOADED = true;
        }
    }

    @Unique private static final java.util.concurrent.ScheduledExecutorService MM_MANIFEST_EXEC =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mmfixes-manifest-writer");
                t.setDaemon(true);
                return t;
            });

    @Unique private static final int MM_FLUSH_DELAY_MS = 250;
    @Unique private static final java.util.concurrent.atomic.AtomicBoolean MM_DIRTY = new java.util.concurrent.atomic.AtomicBoolean(false);
    @Unique private static final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ScheduledFuture<?>> MM_PENDING =
            new java.util.concurrent.atomic.AtomicReference<>(null);
    @Unique private static volatile byte[] MM_LAST_BYTES = new byte[0];

    @Unique
    private static byte[] mm$serializeManifest() throws IOException {
        String json;
        synchronized (MANIFEST) {
            json = GSON.toJson(MANIFEST);
        }
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Unique
    private static void mysticmonstrosityfixes$persistManifest() {
        MM_DIRTY.set(true);
        java.util.concurrent.ScheduledFuture<?> old = MM_PENDING.getAndSet(
                MM_MANIFEST_EXEC.schedule(() -> {
                    // coalesce multiple calls within the window
                    if (MM_DIRTY.get()) mysticmonstrosityfixes$flushManifestOnce();
                }, MM_FLUSH_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        );
        if (old != null) old.cancel(false); // cancel previous pending flush
    }

    @Unique
    private static void mysticmonstrosityfixes$flushManifestOnce() {
        try {
            byte[] bytes = mm$serializeManifest();
            if (java.util.Arrays.equals(bytes, MM_LAST_BYTES)) {
                MM_DIRTY.set(false);
                return;
            }
            java.nio.file.Files.createDirectories(MANIFEST_FILE.getParent());
            java.nio.file.Path tmp = MANIFEST_FILE.resolveSibling(MANIFEST_FILE.getFileName() + ".tmp.~mm");
            try (java.io.OutputStream os = new java.io.BufferedOutputStream(
                    java.nio.file.Files.newOutputStream(tmp,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE),
                    64 * 1024)) {
                os.write(bytes);
                os.flush();
            }
            try {
                java.nio.file.Files.move(tmp, MANIFEST_FILE,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmp, MANIFEST_FILE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            MM_LAST_BYTES = bytes;
            MM_DIRTY.set(false);
        } catch (Throwable ignored) {
        }
    }

    @Unique private static long mysticmonstrosityfixes$crc32(byte[] bytes) {
        CRC32 c = new CRC32();
        c.update(bytes, 0, bytes.length);
        return c.getValue();
    }

    @Unique private static boolean mysticmonstrosityfixes$shouldSkipWrite(Path p, byte[] bytes) throws IOException {
        mysticmonstrosityfixes$loadManifestIfNeeded();
        String key = p.toAbsolutePath().toString().replace('\\','/');
        Entry e = MANIFEST.get(key);
        if (e == null) return false;                 // no metadata yet → write once
        if (!Files.exists(p)) return false;          // file missing → write
        if (e.size != bytes.length) return false;    // size changed → write

        // cheap in-memory hash of the incoming bytes
        long crc = mysticmonstrosityfixes$crc32(bytes);
        if (e.crc32 != crc) return false;            // content changed → write

        // optional: fast sanity check, file hasn't been modified externally
        try {
            long lm = Files.getLastModifiedTime(p).toMillis();
            if (lm != e.lastModified) return false;  // external touch → write
        } catch (IOException ignored) { return false; }

        return true;                                 // everything matches → SKIP
    }

    @Unique private static void mysticmonstrosityfixes$updateManifest(Path p, byte[] bytes) {
        mysticmonstrosityfixes$loadManifestIfNeeded();
        String key = p.toAbsolutePath().toString().replace('\\','/');
        Entry e = new Entry();
        e.size = bytes.length;
        e.crc32 = mysticmonstrosityfixes$crc32(bytes);
        try { e.lastModified = Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException ignored) { e.lastModified = System.currentTimeMillis(); }
        MANIFEST.put(key, e);
        // write-through is fine; if you prefer batch, debounce this call
        mysticmonstrosityfixes$persistManifest();
    }

    @Inject(method = "saveBytes", at = @At("HEAD"), cancellable = true)
    private static void mysticmonstrosityfixes$fastSaveBytes(ResourceLocation id, byte[] bytes, CallbackInfo ci) {
        final Path p = Paths.get("debug","generated_resource_pack")
                .resolve(id.getNamespace()).resolve(id.getPath());
        try {
            final Path parent = p.getParent();
            if (parent != null && CREATED_DIRS.add(parent)) {
                Files.createDirectories(parent);
            }

            // >>> O(1) skip using manifest (no file reads)
            if (Files.exists(p) && mysticmonstrosityfixes$shouldSkipWrite(p, bytes)) {
                ci.cancel(); return;
            }

            // write to temp then atomically move
            final String salt = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
            final Path tmp = (parent != null ? parent : p.getParent()).resolve(p.getFileName() + ".tmp.~ml." + salt);
            try (OutputStream os = new BufferedOutputStream(
                    Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE),
                    64 * 1024)) {
                os.write(bytes);
                os.flush();
            }
            try {
                Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }

            // record fresh metadata so next run can skip without disk reads
            mysticmonstrosityfixes$updateManifest(p, bytes);

        } catch (IOException e) {
            System.err.println("[Moonlight Cache MM Fixes] fast save failed for " + id + ": " + e);
        }
        ci.cancel();
    }
}

