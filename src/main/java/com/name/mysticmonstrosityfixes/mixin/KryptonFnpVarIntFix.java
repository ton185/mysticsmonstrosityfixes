package com.name.mysticmonstrosityfixes.mixin;

import dev.tonimatas.packetfixer.util.Config;
import one.pkg.kreno.shared.network.VarIntByteDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Krypton hardcodes its VarInt byte limit to 5 (oversized packets allowed) or 3, independent of Packet Fixer, so the two
 * disagree and oversized packets get dropped by the decoder Krypton puts in the pipeline. Packet Fixer is the source of
 * truth for that limit, so both branches of the initializer are replaced with its value.
 */
@Mixin(value = VarIntByteDecoder.class)
public abstract class KryptonFnpVarIntFix {

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 3), remap = false)
    private int mmf$defaultVarIntSize(int original) {
        return Config.getVarInt21Size();
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 5), remap = false)
    private int mmf$oversizedVarIntSize(int original) {
        return Config.getVarInt21Size();
    }
}