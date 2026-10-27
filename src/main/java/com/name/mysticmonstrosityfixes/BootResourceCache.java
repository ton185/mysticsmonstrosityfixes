package com.name.mysticmonstrosityfixes;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Disk cache of text and JSON reads from mod and resource-pack files. A later boot serves the
 * bytes and the directory listings instead of scanning the jars again. World saves are not stored.
 */
public final class BootResourceCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x4D4D4631;
    private static final int VERSION = 1;
    private static final long MAX_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_FILE = 8 * 1024 * 1024;

    private static final ConcurrentHashMap<String, byte[]> FILES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String[]> LISTINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String[]> NAMESPACES = new ConcurrentHashMap<>();
    private static final AtomicLong STORED_BYTES = new AtomicLong();
    private static final AtomicInteger REUSED = new AtomicInteger();
    private static final AtomicBoolean CAP_LOGGED = new AtomicBoolean();
    private static final ThreadLocal<ListingCapture> CAPTURE = new ThreadLocal<>();

    private static volatile boolean ready;
    private static volatile boolean writable = true;
    private static volatile boolean dirty;
    private static String fingerprint = "";

    private BootResourceCache() {
    }

    public static boolean cacheable(ResourceLocation id) {
        String path = id.getPath();
        if (path.endsWith(".json") || path.endsWith(".mcmeta") || path.endsWith(".txt")
                || path.endsWith(".fsh") || path.endsWith(".vsh") || path.endsWith(".glsl")
                || path.endsWith(".lang")) {
            return true;
        }
        return path.startsWith("font/") && (path.endsWith(".bin") || path.endsWith(".ttf") || path.endsWith(".otf"));
    }

    public static Set<String> namespaces(String packStamp, PackType type) {
        ensureLoaded();
        String[] names = NAMESPACES.get(namespaceKey(packStamp, type));
        if (names == null) {
            return null;
        }
        return new HashSet<>(List.of(names));
    }

    public static void storeNamespaces(String packStamp, PackType type, Set<String> names) {
        ensureLoaded();
        if (names == null) {
            return;
        }
        if (NAMESPACES.putIfAbsent(namespaceKey(packStamp, type), names.toArray(String[]::new)) == null) {
            dirty = true;
        }
    }

    public static boolean serveListing(String packStamp, PackType type, String namespace, String prefix,
                                       PackResources.ResourceOutput output) {
        ensureLoaded();
        String[] paths = LISTINGS.get(listKey(packStamp, type, namespace, prefix));
        if (paths == null) {
            return false;
        }
        byte[][] data = new byte[paths.length][];
        for (int i = 0; i < paths.length; i++) {
            byte[] bytes = FILES.get(fileKey(packStamp, type, namespace, paths[i]));
            if (bytes == null) {
                return false;
            }
            data[i] = bytes;
        }
        for (int i = 0; i < paths.length; i++) {
            byte[] bytes = data[i];
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, paths[i]);
            output.accept(id, () -> new java.io.ByteArrayInputStream(bytes));
        }
        REUSED.addAndGet(paths.length);
        return true;
    }

    public static void beginListing(String packStamp, PackType type, String namespace, String prefix) {
        CAPTURE.set(new ListingCapture(listKey(packStamp, type, namespace, prefix)));
    }

    public static void noteListed(ResourceLocation id) {
        ListingCapture capture = CAPTURE.get();
        if (capture == null) {
            return;
        }
        if (!cacheable(id)) {
            capture.complete = false;
            return;
        }
        capture.paths.add(id.getPath());
    }

    public static void finishListing() {
        ListingCapture capture = CAPTURE.get();
        CAPTURE.remove();
        if (capture == null || !capture.complete || capture.paths.isEmpty()) {
            return;
        }
        if (LISTINGS.putIfAbsent(capture.key, capture.paths.toArray(String[]::new)) == null) {
            dirty = true;
        }
    }

    public static byte[] bytes(String packStamp, PackType type, ResourceLocation id) {
        ensureLoaded();
        byte[] data = peek(packStamp, type, id);
        if (data != null) {
            REUSED.incrementAndGet();
        }
        return data;
    }

    public static byte[] peek(String packStamp, PackType type, ResourceLocation id) {
        ensureLoaded();
        return FILES.get(fileKey(packStamp, type, id.getNamespace(), id.getPath()));
    }

    public static void putFile(String packStamp, PackType type, ResourceLocation id, byte[] data) {
        ensureLoaded();
        if (!writable || data.length > MAX_FILE) {
            ListingCapture capture = CAPTURE.get();
            if (capture != null) {
                capture.complete = false;
            }
            return;
        }
        String key = fileKey(packStamp, type, id.getNamespace(), id.getPath());
        if (FILES.putIfAbsent(key, data) != null) {
            return;
        }
        long total = STORED_BYTES.addAndGet(data.length);
        dirty = true;
        if (total > MAX_BYTES && CAP_LOGGED.compareAndSet(false, true)) {
            writable = false;
            LOGGER.info("Boot resource cache reached 1 GB of text files and stopped storing more");
        }
    }

    public static void flush() {
        ensureLoaded();
        int reused = REUSED.getAndSet(0);
        if (!dirty) {
            if (reused > 0) {
                LOGGER.info("Boot resource cache reused {} stored files", reused);
            }
            return;
        }
        Path dir = FMLPaths.GAMEDIR.get().resolve("mysticmonstrosityfixes_cache");
        Path target = dir.resolve("boot-resources.bin");
        Path temporary = dir.resolve("boot-resources.bin.tmp");
        try {
            Files.createDirectories(dir);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                writeString(out, fingerprint);
                writeMap(out, NAMESPACES);
                writeMap(out, LISTINGS);
                out.writeInt(FILES.size());
                for (var entry : FILES.entrySet()) {
                    writeString(out, entry.getKey());
                    out.writeInt(entry.getValue().length);
                    out.write(entry.getValue());
                }
            }
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
            LOGGER.info("Boot resource cache wrote {} files ({} MB) and reused {} this reload",
                    FILES.size(), STORED_BYTES.get() / (1024 * 1024), reused);
        } catch (IOException | UnsupportedOperationException exception) {
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                dirty = false;
                LOGGER.info("Boot resource cache wrote {} files ({} MB) and reused {} this reload",
                        FILES.size(), STORED_BYTES.get() / (1024 * 1024), reused);
            } catch (IOException second) {
                LOGGER.warn("Boot resource cache could not be saved", second);
            }
        }
    }

    private static void ensureLoaded() {
        if (ready) {
            return;
        }
        synchronized (BootResourceCache.class) {
            if (ready) {
                return;
            }
            try {
                fingerprint = fingerprint();
                load(fingerprint);
            } catch (Exception exception) {
                LOGGER.warn("Boot resource cache could not be read and will be rebuilt", exception);
                FILES.clear();
                LISTINGS.clear();
                NAMESPACES.clear();
                STORED_BYTES.set(0);
                fingerprint = Long.toHexString(System.nanoTime());
                dirty = true;
            }
            ready = true;
        }
    }

    private static void load(String expected) throws IOException {
        Path file = FMLPaths.GAMEDIR.get().resolve("mysticmonstrosityfixes_cache").resolve("boot-resources.bin");
        if (!Files.isRegularFile(file)) {
            dirty = true;
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                dirty = true;
                return;
            }
            if (!expected.equals(readString(in))) {
                dirty = true;
                return;
            }
            readMap(in, NAMESPACES);
            readMap(in, LISTINGS);
            int files = in.readInt();
            long total = 0;
            for (int i = 0; i < files; i++) {
                String key = readString(in);
                int length = in.readInt();
                if (length < 0 || length > MAX_FILE) {
                    throw new IOException("bad cached file length");
                }
                byte[] data = new byte[length];
                in.readFully(data);
                FILES.put(key, data);
                total += length;
            }
            STORED_BYTES.set(total);
            if (total > MAX_BYTES) {
                writable = false;
            }
        }
    }

    private static String fingerprint() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(new byte[]{VERSION});
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
            try (Stream<Path> stream = Files.list(packs)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        rows.add(path.getFileName() + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis());
                    } catch (IOException ignored) {
                        rows.add(path.getFileName() + ":missing");
                    }
                });
            }
        }
        Collections.sort(rows);
        for (String row : rows) {
            digest.update(row.getBytes(StandardCharsets.UTF_8));
        }
        Path options = FMLPaths.GAMEDIR.get().resolve("options.txt");
        if (Files.isRegularFile(options)) {
            for (String line : Files.readAllLines(options)) {
                if (line.startsWith("resourcePacks:")) {
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte piece : hash) {
            builder.append(Character.forDigit((piece >> 4) & 0xF, 16));
            builder.append(Character.forDigit(piece & 0xF, 16));
        }
        return builder.toString();
    }

    private static void writeMap(DataOutputStream out, ConcurrentHashMap<String, String[]> map) throws IOException {
        out.writeInt(map.size());
        for (var entry : map.entrySet()) {
            writeString(out, entry.getKey());
            String[] values = entry.getValue();
            out.writeInt(values.length);
            for (String value : values) {
                writeString(out, value);
            }
        }
    }

    private static void readMap(DataInputStream in, ConcurrentHashMap<String, String[]> map) throws IOException {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = readString(in);
            int count = in.readInt();
            String[] values = new String[count];
            for (int j = 0; j < count; j++) {
                values[j] = readString(in);
            }
            map.put(key, values);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FILE) {
            throw new IOException("bad cached string");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String namespaceKey(String packStamp, PackType type) {
        return packStamp + '\n' + type.ordinal();
    }

    private static String listKey(String packStamp, PackType type, String namespace, String prefix) {
        return packStamp + '\n' + type.ordinal() + '\n' + namespace + '\n' + prefix;
    }

    private static String fileKey(String packStamp, PackType type, String namespace, String path) {
        return packStamp + '\n' + type.ordinal() + '\n' + namespace + '\n' + path;
    }

    public static String packStamp(String packId, String sourceStamp) {
        return packId + '\n' + sourceStamp;
    }

    public static boolean worldOwned(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/saves/") || normalized.contains("/datapacks/");
    }

    private static final class ListingCapture {
        private final String key;
        private final List<String> paths = new ArrayList<>();
        private boolean complete = true;

        private ListingCapture(String key) {
            this.key = key;
        }
    }
}
