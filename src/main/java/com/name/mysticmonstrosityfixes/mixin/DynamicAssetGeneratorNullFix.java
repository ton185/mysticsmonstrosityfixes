package com.name.mysticmonstrosityfixes.mixin;

import dev.lukebemish.dynamicassetgenerator.impl.CacheReference;
import dev.lukebemish.dynamicassetgenerator.impl.client.ForegroundExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForegroundExtractor.class)
public class DynamicAssetGeneratorNullFix {
    @Inject(at = @At("HEAD"), cancellable = true, remap = false, method = "lambda$reset$7")
    private static void nullFix(String s, CacheReference e, CallbackInfo ci) {
        if (e.getHeld() == null) {
            ci.cancel();
        }
    }
}
