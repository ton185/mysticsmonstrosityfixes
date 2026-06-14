package com.name.mysticmonstrosityfixes.mixin;

import net.soulsweaponry.util.WeaponUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WeaponUtil.class)
public class MixinSoulslikeWeaponryLoadCheck {
    @Shadow
    public static boolean isModLoaded(String modId) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    public static boolean isFightModLoaded = false;
    public static boolean isFightModCached = false;

    /**
     * @author ton185
     * @reason Cache modLoad results
     */
    @Overwrite(remap = false)
    public static boolean isFightModLoaded() {
        if (isFightModCached) return isFightModLoaded;
        isFightModCached = true;
        return isFightModLoaded = isModLoaded("bettercombat") || isModLoaded("epicfight");
    }
}
