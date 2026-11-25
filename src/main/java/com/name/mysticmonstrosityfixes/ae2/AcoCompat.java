package com.name.mysticmonstrosityfixes.ae2;

import net.minecraftforge.fml.ModList;

/**
 * Keeps ACO-specific fixes on the 1.x line. ACO 2.0 is an experimental rewrite with different
 * internals; the fixes then skip rather than touching classes that may no longer have the expected
 * shape.
 */
public final class AcoCompat {

    private static volatile Boolean legacy;

    private AcoCompat() {
    }

    public static boolean isLegacy() {
        Boolean cached = legacy;
        if (cached != null) {
            return cached;
        }
        String version = version();
        if (version == null) {
            return false;
        }
        int major = major(version);
        if (major < 0) {
            return false;
        }
        boolean value = major < 2;
        legacy = value;
        return value;
    }

    public static boolean hasLegacyExactStorageApi() {
        return isLegacy() && ModResourceLoader.hasClass(
                "com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess");
    }

    public static boolean isPresent() {
        return ModResourceLoader.hasClass("com.syaru.ae2craftingoptimizer.config.ACOConfig");
    }

    private static String version() {
        try {
            var container = ModList.get().getModContainerById("ae2_crafting_optimizer");
            if (container.isPresent()) {
                return container.get().getModInfo().getVersion().toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object loading = loader.getMethod("getLoadingModList").invoke(null);
            Object file = loading.getClass().getMethod("getModFileById", String.class)
                    .invoke(loading, "ae2_crafting_optimizer");
            if (file == null) {
                return null;
            }
            Object mods = file.getClass().getMethod("getMods").invoke(file);
            if (mods instanceof Iterable<?> iterable) {
                for (Object info : iterable) {
                    Object id = info.getClass().getMethod("getModId").invoke(info);
                    if ("ae2_crafting_optimizer".equals(id)) {
                        Object version = info.getClass().getMethod("getVersion").invoke(info);
                        return version == null ? null : version.toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int major(String version) {
        if (version == null || version.isEmpty()) {
            return -1;
        }
        int start = 0;
        while (start < version.length() && !Character.isDigit(version.charAt(start))) {
            start++;
        }
        if (start >= version.length()) {
            return -1;
        }
        int dot = version.indexOf('.', start);
        String major = dot < 0 ? version.substring(start) : version.substring(start, dot);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
