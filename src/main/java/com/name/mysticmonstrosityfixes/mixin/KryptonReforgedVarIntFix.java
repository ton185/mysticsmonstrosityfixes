package com.name.mysticmonstrosityfixes.mixin;

import dev.tonimatas.packetfixer.util.Config;
import me.steinborn.krypton.mod.shared.network.VarintByteDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = VarintByteDecoder.class)
public abstract class KryptonReforgedVarIntFix {
    @ModifyConstant(method = "process", constant = @Constant(intValue = 3), remap = false)
    public int newSize(int original) {
        return Config.getVarInt21Size();
    }
}
