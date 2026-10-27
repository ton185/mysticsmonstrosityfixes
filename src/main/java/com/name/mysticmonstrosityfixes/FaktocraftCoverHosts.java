package com.name.mysticmonstrosityfixes;

import java.util.Map;

/**
 * Coverable Faktocraft blocks, keyed by {@code namespace:path}. The value is whether the block is
 * the drilled block. This lives outside a mixin class: Mixin rejects a non-private static field
 * on a mixin, and the dynamic-model hook has to read the set from ModernFix's class.
 */
public final class FaktocraftCoverHosts {
    public static volatile Map<String, Boolean> HOSTS;

    private FaktocraftCoverHosts() {
    }

    public static Boolean drilledFlag(String namespace, String path) {
        Map<String, Boolean> hosts = HOSTS;
        if (hosts == null) {
            return null;
        }
        return hosts.get(namespace + ":" + path);
    }
}
