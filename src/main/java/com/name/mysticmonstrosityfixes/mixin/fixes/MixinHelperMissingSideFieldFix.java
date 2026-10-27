package com.name.mysticmonstrosityfixes.mixin.fixes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinHelper audits every mixin config and, when {@code mixinClassesClient} or
 * {@code mixinClassesServer} is unset, looks up fields named {@code client} and {@code server}.
 * Mixin 0.8.5 does not have those fields, so each config logs
 * "Failed to get field MixinConfig.server/client". The audit already treats a failed lookup as
 * "no mixins", so returning null here keeps that result without the error.
 */
@Mixin(targets = "net.tysontheember.mixinhelper.ReflectionHelper", remap = false)
public class MixinHelperMissingSideFieldFix {
    @Inject(method = "getFieldValue", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skipMissingMixinConfigSideFields(Object target, Class<?> owner, String fieldName,
                                                         CallbackInfoReturnable<Object> cir) {
        if (owner == null || fieldName == null) {
            return;
        }
        if (!"org.spongepowered.asm.mixin.transformer.MixinConfig".equals(owner.getName())) {
            return;
        }
        if ("client".equals(fieldName) || "server".equals(fieldName)) {
            cir.setReturnValue(null);
        }
    }
}
