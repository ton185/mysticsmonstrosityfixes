package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.LanguageReloadLeakCleanup;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers every translated component so a language reload can clear the language it cached. The
 * registry holds the component weakly, so this does not extend its lifetime.
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsLeakFix {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mmf$registerCachedTranslation(CallbackInfo ci) {

        LanguageReloadLeakCleanup.register((TranslatableContents) (Object) this);
    }
}
