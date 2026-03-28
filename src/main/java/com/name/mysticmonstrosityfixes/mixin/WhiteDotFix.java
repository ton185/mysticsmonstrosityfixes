package com.name.mysticmonstrosityfixes.mixin;

import net.minecraft.client.renderer.block.model.FaceBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FaceBakery.class)
public class WhiteDotFix {

    @ModifyArg(
        method = "bakeQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;<init>([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZ)V"
        ),
        index = 0
    )
    private int[] fixUVs(int[] data) {
        double[] coords = new double[8];

        for (int v = 0; v < 4; v++) {
            int base = v * 8;
            coords[v * 2]     = Float.intBitsToFloat(data[base + 4]); // U
            coords[v * 2 + 1] = Float.intBitsToFloat(data[base + 5]); // V
        }

        coords = mystics_monstrosity_fixes$fix(coords);

        for (int v = 0; v < 4; v++) {
            int base = v * 8;
            data[base + 4] = Float.floatToRawIntBits((float) coords[v * 2]);
            data[base + 5] = Float.floatToRawIntBits((float) coords[v * 2 + 1]);
        }

        return data;
    }

    @Unique
    private double[] mystics_monstrosity_fixes$fix(double[] coords) {
        double min = coords[0];
        double max = coords[0];
        for (int i = 1; i < coords.length; i++) {
            if (coords[i] < min) min = coords[i];
            if (coords[i] > max) max = coords[i];
        }

        for (int i = 0; i < coords.length; i++) {
            if (Math.abs(coords[i] - min) < 1e-9) {
                coords[i] = min + 2e-5;
            } else if (Math.abs(coords[i] - max) < 1e-9) {
                coords[i] = max - 2e-5;
            }
        }

        return coords;
    }
}