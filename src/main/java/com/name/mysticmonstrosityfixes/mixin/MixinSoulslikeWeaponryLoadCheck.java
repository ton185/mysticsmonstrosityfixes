package com.name.mysticmonstrosityfixes.mixin;

import net.soulsweaponry.util.WeaponUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WeaponUtil.class)
public class MixinSoulslikeWeaponryLoadCheck {
    @Shadow
    public static boolean isModLoaded(String modId) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Unique
    private static boolean mystics_monstrosity_fixes$isFightModLoaded = false;
    @Unique
    private static boolean mystics_monstrosity_fixes$isFightModCached = false;

    /**
     * @author ton185
     * @reason Cache modLoad results
     */
    @Overwrite(remap = false)
    public static boolean isFightModLoaded() {
        if (mystics_monstrosity_fixes$isFightModCached) return mystics_monstrosity_fixes$isFightModLoaded;
        mystics_monstrosity_fixes$isFightModCached = true;
        return mystics_monstrosity_fixes$isFightModLoaded = isModLoaded("bettercombat") || isModLoaded("epicfight");
    }
}
