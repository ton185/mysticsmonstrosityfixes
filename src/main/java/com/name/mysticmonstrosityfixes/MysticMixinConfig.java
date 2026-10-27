package com.name.mysticmonstrosityfixes;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mixin options in the same shape Lithium uses. Each mixin package is one key, a parent set to
 * false turns off every option under it, and the file the game reads is
 * {@code config/mysticmonstrosityfixes.properties}.
 */
public final class MysticMixinConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HEADER = """
            # Mystic's Monstrosity Fixes mixin options.
            # A parent set to false turns off every option under it.
            # mixin.experimental is off by default. Set it to true to enable the experimental mixins.
            # Edit this file in the instance config folder.
            """;
    private static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("mixin.loading", true);
        DEFAULTS.put("mixin.fixes", true);
        DEFAULTS.put("mixin.experimental", false);
        DEFAULTS.put("mixin.experimental.bootcache", true);
        DEFAULTS.put("mixin.experimental.blockatlas", true);
    }

    private static final Map<String, Boolean> OPTIONS = new LinkedHashMap<>();
    private static boolean loaded;

    private MysticMixinConfig() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        OPTIONS.putAll(DEFAULTS);
        Path file = configFile();
        if (Files.isRegularFile(file)) {
            applyFile(file);
            appendMissing(file);
        } else {
            writeDefaults(file);
        }
        LOGGER.info("Mixin options loaded from {} (experimental={})", file, OPTIONS.get("mixin.experimental"));
    }

    /**
     * @param mixinClassName the fully qualified mixin class
     * @return false when that mixin's package, or any parent package, is turned off
     */
    public static boolean allow(String mixinClassName) {
        load();
        String marker = ".mixin.";
        int at = mixinClassName == null ? -1 : mixinClassName.indexOf(marker);
        if (at < 0) {
            return true;
        }
        String relative = mixinClassName.substring(at + marker.length());
        int classSplit = relative.lastIndexOf('.');
        if (classSplit < 0) {
            return true;
        }
        String[] parts = relative.substring(0, classSplit).split("\\.");
        String path = "mixin";
        for (String part : parts) {
            path = path + "." + part;
            if (!OPTIONS.getOrDefault(path, false)) {
                return false;
            }
        }
        return true;
    }

    private static Path configFile() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("mysticmonstrosityfixes.properties");
        } catch (Throwable ignored) {
            return Path.of("config", "mysticmonstrosityfixes.properties");
        }
    }

    private static void applyFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            java.util.Properties properties = new java.util.Properties();
            properties.load(reader);
            for (String key : DEFAULTS.keySet()) {
                String value = properties.getProperty(key);
                if (value != null) {
                    OPTIONS.put(key, Boolean.parseBoolean(value.trim()));
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not read mixin options from {}", file, exception);
        }
    }

    private static void appendMissing(Path file) {
        String existing;
        try {
            existing = Files.readString(file);
        } catch (IOException exception) {
            return;
        }
        StringBuilder added = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : DEFAULTS.entrySet()) {
            if (!existing.contains(entry.getKey() + "=")) {
                added.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
            }
        }
        if (added.isEmpty()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(System.lineSeparator());
            writer.write(added.toString());
        } catch (IOException exception) {
            LOGGER.warn("Could not update mixin options in {}", file, exception);
        }
    }

    private static void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                for (Map.Entry<String, Boolean> entry : DEFAULTS.entrySet()) {
                    writer.write(entry.getKey());
                    writer.write('=');
                    writer.write(Boolean.toString(entry.getValue()));
                    writer.write(System.lineSeparator());
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not write mixin options to {}", file, exception);
        }
    }
}
