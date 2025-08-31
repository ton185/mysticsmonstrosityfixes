package com.name.mysticmonstrosityfixes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class MoonlightAssetsCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = Paths.get(".cache", "moonlight-assets");
    private static final Set<Path> CREATED_DIRS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int IO_BUFFER = 64 * 1024;
    private static final int PARALLELISM = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final Map<ResourceLocation, byte[]> STAGED = new ConcurrentHashMap<>();
    private static Path activeDir;
    private static Manifest manifest;

    public static final class Manifest {
        public String mc;
        public String moonlight;
        public Map<String,String> mods;
        public String configHash;
        public Map<String,String> sha1 = new HashMap<>();
    }

    public static void begin(String mc, String moonlight, Map<String,String> mods, String configHash) throws IOException {
        Files.createDirectories(ROOT);
        String key = sha1((mc + "|" + moonlight + "|" + mods + "|" + configHash).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        activeDir = ROOT.resolve(key);
        Files.createDirectories(activeDir.resolve("assets"));
        manifest = new Manifest();
        manifest.mc = mc; manifest.moonlight = moonlight; manifest.mods = mods; manifest.configHash = configHash;
    }

    public static boolean tryWarmStart(BiConsumer<ResourceLocation, byte[]> sink) {
        if (activeDir == null) return false;

        final Path manifestPath = activeDir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) return false;

        try {
            final Manifest onDisk = GSON.fromJson(Files.readString(manifestPath), Manifest.class);
            if (!Objects.equals(onDisk.mc, manifest.mc)
                    || !Objects.equals(onDisk.moonlight, manifest.moonlight)
                    || !Objects.equals(onDisk.configHash, manifest.configHash)
                    || !Objects.equals(onDisk.mods, manifest.mods)) {
                return false;
            }

            final Path root = activeDir.resolve("assets");
            if (!Files.isDirectory(root)) return false;

            if (onDisk.sha1 == null || onDisk.sha1.isEmpty()) return false;

            final var pool = java.util.concurrent.Executors.newFixedThreadPool(
                    PARALLELISM, r -> { var t = new Thread(r, "mmfixes-warmstart"); t.setDaemon(true); return t; });

            final var submitted = new java.util.concurrent.atomic.AtomicInteger(0);
            final var hadAny = new java.util.concurrent.atomic.AtomicBoolean(false);

            for (var entry : onDisk.sha1.entrySet()) {
                final String key = entry.getKey(); // ns:path
                final int colon = key.indexOf(':');
                if (colon <= 0 || colon == key.length() - 1) continue;
                final String ns = key.substring(0, colon);
                final String path = key.substring(colon + 1);

                final Path p = root.resolve(ns).resolve(path);

                if (!Files.exists(p)) continue;

                submitted.incrementAndGet();
                pool.execute(() -> {
                    try {
                        long sizeL = Files.size(p);
                        if (sizeL <= 0 || sizeL > Integer.MAX_VALUE) return; // guard
                        int size = (int) sizeL;

                        byte[] bytes;
                        try (var in = Files.newInputStream(p, StandardOpenOption.READ)) {
                            bytes = in.readNBytes(size);
                            if (bytes.length != size) return;
                        }

                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, path);
                        sink.accept(id, bytes);
                        hadAny.set(true);
                    } catch (Throwable ignored) {
                    }
                });
            }

            if (submitted.get() == 0) {
                pool.shutdownNow();
                return false;
            }

            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }

            return hadAny.get();

        } catch (Exception e) {
            return false;
        }
    }

    public static void put(ResourceLocation id, byte[] bytes) {
        STAGED.put(id, bytes);
    }

    public static void persist() {
        if (activeDir == null || manifest == null) return;

        if (STAGED.isEmpty()) return;

        final Path root = activeDir.resolve("assets");

        final java.util.List<java.util.Map.Entry<ResourceLocation, byte[]>> work =
                new java.util.ArrayList<>(STAGED.entrySet());
        STAGED.clear();

        final java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(PARALLELISM, r -> {
                    Thread t = new Thread(r, "mmfixes-cache-writer");
                    t.setDaemon(true);
                    return t;
                });

        final java.util.Map<String, String> updatedSha1 = new java.util.HashMap<>(work.size());

        try {
            for (var e : work) {
                pool.execute(() -> {
                    final ResourceLocation id = e.getKey();
                    final byte[] bytes = e.getValue();
                    final String key = id.getNamespace() + ":" + id.getPath();

                    final String digest = sha1(bytes);

                    final String prev = manifest.sha1.get(key);
                    if (digest.equals(prev)) {
                        updatedSha1.put(key, digest);
                        return;
                    }

                    final Path p = root.resolve(id.getNamespace()).resolve(id.getPath());
                    final Path parent = p.getParent();

                    try {
                        if (parent != null && CREATED_DIRS.add(parent)) {
                            java.nio.file.Files.createDirectories(parent);
                        }

                        final String salt = Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong(), 36);
                        final Path tmp = (parent != null ? parent : p.getParent())
                                .resolve(p.getFileName() + ".tmp.~ml." + salt);

                        try (java.io.OutputStream os = new java.io.BufferedOutputStream(
                                java.nio.file.Files.newOutputStream(
                                        tmp,
                                        java.nio.file.StandardOpenOption.CREATE,
                                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                                        java.nio.file.StandardOpenOption.WRITE),
                                IO_BUFFER)) {
                            os.write(bytes);
                            os.flush();
                        }

                        try {
                            java.nio.file.Files.move(tmp, p,
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                            java.nio.file.Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }

                        // record digest for manifest
                        updatedSha1.put(key, digest);

                    } catch (java.io.IOException ex) {
                        System.err.println("[Moonlight Cache MM Fixes] cache write failed for " + id + ": " + ex);
                    }
                });
            }

            // wait for all writes
            pool.shutdown();
            try { pool.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            manifest.sha1.putAll(updatedSha1);

            final Path man = activeDir.resolve("manifest.json");
            final String json = GSON.toJson(manifest);
            final byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            final Path tmpMan = man.resolveSibling(man.getFileName() + ".tmp.~ml");
            try (java.io.OutputStream os = new java.io.BufferedOutputStream(
                    java.nio.file.Files.newOutputStream(
                            tmpMan,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE),
                    IO_BUFFER)) {
                os.write(bytes);
                os.flush();
            }
            try {
                java.nio.file.Files.move(tmpMan, man,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmpMan, man, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Throwable t) {
            System.err.println("[Moonlight Cache MM Fixes] cache persist failed: " + t);
        }
    }

    private static String sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length*2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
