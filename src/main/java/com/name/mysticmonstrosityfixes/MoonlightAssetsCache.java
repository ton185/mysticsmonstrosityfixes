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
        Path m = activeDir.resolve("manifest.json");
        if (!Files.exists(m)) return false;
        try {
            Manifest onDisk = GSON.fromJson(Files.readString(m), Manifest.class);
            if (!Objects.equals(onDisk.mc, manifest.mc)
                    || !Objects.equals(onDisk.moonlight, manifest.moonlight)
                    || !Objects.equals(onDisk.configHash, manifest.configHash)
                    || !Objects.equals(onDisk.mods, manifest.mods)) return false;

            Path root = activeDir.resolve("assets");
            if (!Files.isDirectory(root)) return false;

            Files.walk(root).forEach(p -> {
                if (!Files.isRegularFile(p)) return;
                Path rel = root.relativize(p);
                String ns = rel.getName(0).toString();
                String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\','/');
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, path);
                try {
                    byte[] bytes = Files.readAllBytes(p);
                    String want = onDisk.sha1.get(ns + ":" + path);
                    if (want != null && want.equals(sha1(bytes))) {
                        sink.accept(id, bytes);
                    }
                } catch (IOException ignored) {}
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void put(ResourceLocation id, byte[] bytes) {
        STAGED.put(id, bytes);
    }

    public static void persist() {
        if (activeDir == null || manifest == null) return;
        Path root = activeDir.resolve("assets");
        try {
            for (var e : STAGED.entrySet()) {
                ResourceLocation id = e.getKey();
                byte[] bytes = e.getValue();
                Path p = root.resolve(id.getNamespace()).resolve(id.getPath());
                Files.createDirectories(p.getParent());
                try (OutputStream os = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    os.write(bytes);
                }
                manifest.sha1.put(id.getNamespace() + ":" + id.getPath(), sha1(bytes));
            }
            Files.writeString(activeDir.resolve("manifest.json"), GSON.toJson(manifest),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            System.err.println("[Moonlight Cache MM Fixes] cache persist failed: " + e);
        } finally {
            STAGED.clear();
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
