package com.name.mysticmonstrosityfixes.vram_usage;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Reads system-wide VRAM usage for the F3 debug line. Windows uses the GPU Adapter Memory
 * performance counters and the display driver registry entries, which cover every vendor. macOS
 * reads the IOAccelerator statistics, including Apple Silicon's unified-memory counters. Other
 * systems use the OpenGL memory-info extensions, with DRM sysfs as a Linux fallback. Every provider
 * is best-effort: a failure omits the line instead of surfacing an error in the debug screen.
 */
public final class VramUsage {
    private static final long REFRESH_NANOS = 5_00_000_000L;
    private static final long MEGABYTE = 1024L * 1024L;
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS_NAME.startsWith("windows");
    private static final boolean LINUX = OS_NAME.contains("linux");
    private static final boolean MAC = OS_NAME.contains("mac") || OS_NAME.contains("darwin");

    // GL_NVX_gpu_memory_info, values are KB.
    private static final int NVX_DEDICATED_VIDMEM = 0x9047;
    private static final int NVX_TOTAL_AVAILABLE_MEMORY = 0x9048;
    private static final int NVX_CURRENT_AVAILABLE_VIDMEM = 0x9049;

    // GL_ATI_meminfo returns a 4-tuple in KB; the first value is the total free pool.
    private static final int ATI_TEXTURE_FREE_MEMORY = 0x87FC;
    private static final IntBuffer ATI_MEMORY_INFO = BufferUtils.createIntBuffer(4);

    private static String cachedLine;
    private static long nextRefreshNanos;

    private VramUsage() {
    }

    /**
     * @return the formatted debug line, or null when VRAM usage cannot be read
     */
    public static synchronized String debugLine() {
        long now = System.nanoTime();
        if (now < nextRefreshNanos) {
            return cachedLine;
        }
        nextRefreshNanos = now + REFRESH_NANOS;
        try {
            Usage usage = current();
            cachedLine = format(usage);
        } catch (Throwable ignored) {
            cachedLine = null;
        }
        return cachedLine;
    }

    private static Usage current() {
        Usage usage;
        if (WINDOWS) {
            usage = windows();
            if (valid(usage)) {
                return usage;
            }
        }
        if (MAC) {
            usage = mac();
            if (valid(usage)) {
                return usage;
            }
        }
        usage = nvx();
        if (valid(usage)) {
            return usage;
        }
        usage = ati();
        if (valid(usage)) {
            return usage;
        }
        usage = sysfs();
        return valid(usage) ? usage : null;
    }

    private static Usage windows() {
        try {
            return WindowsVram.query();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Usage mac() {
        try {
            return MacVram.query();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Usage nvx() {
        try {
            if (!GL.getCapabilities().GL_NVX_gpu_memory_info) {
                return null;
            }
            long dedicated = Integer.toUnsignedLong(GL11.glGetInteger(NVX_DEDICATED_VIDMEM));
            long totalAvailable = Integer.toUnsignedLong(GL11.glGetInteger(NVX_TOTAL_AVAILABLE_MEMORY));
            long available = Integer.toUnsignedLong(GL11.glGetInteger(NVX_CURRENT_AVAILABLE_VIDMEM));
            long total = dedicated > 0L ? dedicated : totalAvailable;
            if (available > total && totalAvailable >= available) {
                total = totalAvailable;
            }
            if (total <= 0L || available > total) {
                return null;
            }
            return new Usage((total - available) * 1024L, total * 1024L);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Usage ati() {
        try {
            if (!GL.getCapabilities().GL_ATI_meminfo) {
                return null;
            }
            long total = totalBytes();
            if (total <= 0L) {
                return null;
            }
            ATI_MEMORY_INFO.clear();
            GL11.glGetIntegerv(ATI_TEXTURE_FREE_MEMORY, ATI_MEMORY_INFO);
            long free = Integer.toUnsignedLong(ATI_MEMORY_INFO.get(0)) * 1024L;
            if (free <= 0L || free > total) {
                return null;
            }
            return new Usage(total - free, total);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Usage sysfs() {
        try {
            long[] values = readSysfs();
            return values == null ? null : new Usage(values[0], values[1]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long totalBytes() {
        try {
            if (WINDOWS) {
                return WindowsVram.totalBytes();
            }
            if (LINUX) {
                long[] values = readSysfs();
                return values == null ? 0L : values[1];
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    /**
     * @return {used bytes, total bytes} from the DRM VRAM counters, or null when no card exposes them
     */
    private static long[] readSysfs() {
        if (!LINUX) {
            return null;
        }
        Path root = Path.of("/sys/class/drm");
        if (!Files.isDirectory(root)) {
            return null;
        }
        long used = 0L;
        long total = 0L;
        boolean found = false;
        try (DirectoryStream<Path> cards = Files.newDirectoryStream(root, "card[0-9]*")) {
            for (Path card : cards) {
                Path device = card.resolve("device");
                Long cardTotal = readLong(device.resolve("mem_info_vram_total"));
                Long cardUsed = readLong(device.resolve("mem_info_vram_used"));
                if (cardTotal == null || cardUsed == null || cardTotal <= 0L) {
                    continue;
                }
                used += Math.max(0L, Math.min(cardUsed, cardTotal));
                total += cardTotal;
                found = true;
            }
        } catch (Throwable ignored) {
            return null;
        }
        return found ? new long[]{used, total} : null;
    }

    private static Long readLong(Path path) {
        try {
            String text = Files.readString(path).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean valid(Usage usage) {
        return usage != null && usage.totalBytes() > 0L
                && usage.usedBytes() >= 0L && usage.usedBytes() <= usage.totalBytes();
    }

    private static String format(Usage usage) {
        if (!valid(usage)) {
            return null;
        }
        long totalMb = usage.totalBytes() / MEGABYTE;
        long usedMb = Math.min(totalMb, usage.usedBytes() / MEGABYTE);
        if (totalMb <= 0L) {
            return null;
        }
        long percent = Math.max(0L, Math.min(100L, usedMb * 100L / totalMb));
        return String.format(Locale.ROOT, "VRAM: %2d%% %d/%d MB", percent, usedMb, totalMb);
    }

    record Usage(long usedBytes, long totalBytes) {
    }
}
