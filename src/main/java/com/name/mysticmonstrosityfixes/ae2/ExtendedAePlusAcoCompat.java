package com.name.mysticmonstrosityfixes.ae2;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.MysticMixinConfig;
import com.name.mysticmonstrosityfixes.mixin.MixinConfigAccess;
import org.slf4j.Logger;

import java.util.List;

/**
 * Removes ACO 1.5.33's ExtendedAE Plus integration mixins when ExtendedAE Plus 1.6.4's split
 * long/BigInteger storage shape makes their @Shadow targets disappear. The replacement accessors in
 * this mod then provide the same exact-amount behavior against the new shape.
 */
public final class ExtendedAePlusAcoCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OPTION = "mixin.fixes.extendedae_aco_exact_storage_compat";
    private static final String ACO_CONFIG = "ae2_crafting_optimizer.mixins.json";
    private static final List<String> LEGACY_MIXINS = List.of(
            "com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusInfinityDataStorageConsistencyMixin",
            "com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellConsistencyMixin",
            "com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor");

    private static volatile boolean installed;
    private static volatile boolean approved;

    private ExtendedAePlusAcoCompat() {
    }

    public static boolean installIfEnabled() {
        try {
            if (!MysticMixinConfig.isEnabled(OPTION)) {
                return false;
            }
            return install();
        } catch (Throwable throwable) {
            LOGGER.warn("Could not disable ACO's incompatible ExtendedAE Plus exact-storage mixins", throwable);
            return false;
        }
    }

    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        if (!approved || !AcoCompat.hasLegacyExactStorageApi() || !ExtendedAePlusCompat.isSplitStorageShape()) {
            return false;
        }
        Object config = MixinConfigAccess.findConfig(ACO_CONFIG);
        if (config == null) {
            LOGGER.debug("ACO's mixin config is not ready for the ExtendedAE Plus compatibility fix yet");
            return false;
        }
        boolean removed = false;
        for (String mixin : LEGACY_MIXINS) {
            removed |= MixinConfigAccess.removeMixinClass(config, mixin);
        }
        if (!removed) {
            return false;
        }
        LOGGER.info("Replaced ACO's ExtendedAE Plus 1.5-era exact-storage mixins with the 1.6.4 split-storage access bridge");
        installed = true;
        return true;
    }

    public static boolean shouldRepair() {
        boolean value = AcoCompat.hasLegacyExactStorageApi() && ExtendedAePlusCompat.isSplitStorageShape();
        if (value) {
            approved = true;
        }
        return value;
    }
}
