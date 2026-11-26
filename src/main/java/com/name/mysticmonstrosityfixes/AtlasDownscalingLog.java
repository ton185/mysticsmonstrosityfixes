package com.name.mysticmonstrosityfixes;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes the sprites that {@link AtlasDownscaling} shrinks to {@code logs/mmf_downscaled_atlas_sprites.txt}.
 * The sprite id column is widened as needed by rewriting the file, so every row stays aligned with
 * the header.
 */
public final class AtlasDownscalingLog {
    public static final String FILE_NAME = "mmf_downscaled_atlas_sprites.txt";
    private static final String PATTERN_HEADER = "PATTERN";
    private static final String SPRITE_HEADER = "SPRITE_ID";
    private static final String DIMENSIONS_HEADER = "DIMENSIONS";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final char[] DIGITS = new char[10];
    private static final char[] SPACES = new char[64];
    private static int indexWidth = 1;
    private static int patternWidth = PATTERN_HEADER.length();
    private static int spriteWidth = SPRITE_HEADER.length();
    private static Writer writer;
    private static boolean headerWritten;

    static {
        Arrays.fill(SPACES, ' ');
    }

    private AtlasDownscalingLog() {
    }

    public static void log(ResourceLocation sprite, String pattern, int width, int height) {
        if (!MysticMixinConfig.isEnabled("mixin.fixes.atlas_downscaling")
                || !MysticMixinConfig.isEnabled("mixin.fixes.atlas_downscaling_logging")) {
            return;
        }
        String spriteId = sprite.getNamespace() + ':' + sprite.getPath();
        Entry entry = new Entry(pattern, spriteId, width, height);
        synchronized (LOCK) {
            ENTRIES.add(entry);
            int newIndexWidth = digits(ENTRIES.size());
            int newPatternWidth = pattern.length();
            int newSpriteWidth = spriteId.length();
            boolean widened = newIndexWidth > indexWidth
                    || newPatternWidth > patternWidth
                    || newSpriteWidth > spriteWidth;
            indexWidth = Math.max(indexWidth, newIndexWidth);
            patternWidth = Math.max(patternWidth, newPatternWidth);
            spriteWidth = Math.max(spriteWidth, newSpriteWidth);
            if (widened || !headerWritten) {
                writeAll();
            } else {
                append(entry);
            }
        }
    }

    private static void writeAll() {
        headerWritten = false;
        closeWriter();
        Path file = logFile();
        try {
            createParent(file);
            Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            try {
                writeHeader(out);
                for (int i = 0; i < ENTRIES.size(); i++) {
                    writeRow(out, i + 1, ENTRIES.get(i));
                }
                out.flush();
                writer = out;
                headerWritten = true;
            } catch (IOException | RuntimeException exception) {
                closeQuietly(out);
                throw exception;
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not write downscaled atlas sprite log to {}", file, exception);
        }
    }

    private static void append(Entry entry) {
        try {
            Writer out = writer;
            if (out == null) {
                throw new IOException("Log writer is closed");
            }
            writeRow(out, ENTRIES.size(), entry);
            out.flush();
        } catch (IOException | RuntimeException exception) {
            closeWriter();
            headerWritten = false;
            LOGGER.warn("Could not append to downscaled atlas sprite log {}", logFile(), exception);
        }
    }

    private static void writeHeader(Writer out) throws IOException {
        writeSpaces(out, indexWidth - 1);
        out.write('#');
        out.write(' ');
        out.write(PATTERN_HEADER);
        writeSpaces(out, patternWidth - PATTERN_HEADER.length());
        out.write(' ');
        out.write(SPRITE_HEADER);
        writeSpaces(out, spriteWidth - SPRITE_HEADER.length());
        out.write(' ');
        out.write(DIMENSIONS_HEADER);
        out.write(System.lineSeparator());
    }

    private static void writeRow(Writer out, int index, Entry entry) throws IOException {
        writeInt(out, index, indexWidth);
        out.write(' ');
        out.write(entry.pattern());
        writeSpaces(out, patternWidth - entry.pattern().length());
        out.write(' ');
        out.write(entry.spriteId());
        writeSpaces(out, spriteWidth - entry.spriteId().length());
        out.write(' ');
        writeInt(out, entry.width(), 0);
        out.write('x');
        writeInt(out, entry.height(), 0);
        out.write(System.lineSeparator());
    }

    private static void writeInt(Writer out, int value, int width) throws IOException {
        int position = DIGITS.length;
        do {
            DIGITS[--position] = (char) ('0' + value % 10);
            value /= 10;
        } while (value > 0);
        int digits = DIGITS.length - position;
        if (width > digits) {
            writeSpaces(out, width - digits);
        }
        out.write(DIGITS, position, digits);
    }

    private static void writeSpaces(Writer out, int count) throws IOException {
        while (count > 0) {
            int chunk = Math.min(count, SPACES.length);
            out.write(SPACES, 0, chunk);
            count -= chunk;
        }
    }

    private static int digits(int value) {
        int digits = 1;
        while (value >= 10) {
            value /= 10;
            digits++;
        }
        return digits;
    }

    private static void closeWriter() {
        Writer out = writer;
        writer = null;
        closeQuietly(out);
    }

    private static void closeQuietly(Writer out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static Path logFile() {
        try {
            return FMLPaths.GAMEDIR.get().resolve("logs").resolve(FILE_NAME);
        } catch (Throwable ignored) {
            return Path.of("logs", FILE_NAME);
        }
    }

    private static void createParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private record Entry(String pattern, String spriteId, int width, int height) {
    }
}
