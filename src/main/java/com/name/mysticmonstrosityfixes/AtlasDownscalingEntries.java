package com.name.mysticmonstrosityfixes;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class AtlasDownscalingEntries {
    public static final String FILE_NAME = "mysticmonstrosityfixes_atlas_downscaling_entries.txt";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> HEADER = List.of(
            "# Atlas downscaling entries, one per line.",
            "# Format: <sprite id regex>=<factor>",
            "# The regex must match the whole sprite id without the .png extension, for example somemod:entities/.*=2",
            "# A factor of 1 halves both dimensions, 2 quarters them, and so on.",
            "# Dimensions are floored and never drop below 1; the maximum mip level falls with them, stopping at 0.",
            "# Blank lines and lines starting with # are ignored.",
            "# Use AtlasViewer to find oversized sprites to downscale. (https://www.curseforge.com/minecraft/mc-mods/atlasviewer)",
            "# Entry changes apply after a resource reload (F3+T)."
    );
    private static boolean readFailureLogged;

    private AtlasDownscalingEntries() {
    }

    public static List<String> read() {
        Path file = file();
        ensureFile(file);
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> entries = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                String entry = lines.get(i).trim();
                if (i == 0 && !entry.isEmpty() && entry.charAt(0) == '\uFEFF') {
                    entry = entry.substring(1).trim();
                }
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                entries.add(entry);
            }
            return entries;
        } catch (IOException | RuntimeException exception) {
            if (!readFailureLogged) {
                readFailureLogged = true;
                LOGGER.warn("Could not read atlas downscaling entries from {}; no sprites will be downscaled",
                        file, exception);
            }
            return List.of();
        }
    }

    public static Path file() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        } catch (Throwable ignored) {
            return Path.of("config", FILE_NAME);
        }
    }

    private static void ensureFile(Path file) {
        if (Files.isRegularFile(file)) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, HEADER, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            LOGGER.info("Created atlas downscaling entries file at {}", file);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not create atlas downscaling entries file at {}", file, exception);
        }
    }
}
