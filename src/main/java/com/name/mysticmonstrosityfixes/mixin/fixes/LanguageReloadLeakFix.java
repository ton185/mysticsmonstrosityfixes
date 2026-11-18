package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.LanguageReloadLeakCleanup;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after a language reload has installed the new language. Every live translated component
 * drops the language it last decomposed with, so the previous {@code ClientLanguage} can be
 * collected instead of being pinned by chat history, HUD text, or a mod cache.
 */
@Mixin(Language.class)
public abstract class LanguageReloadLeakFix {

    @Inject(method = "inject", at = @At("TAIL"))
    private static void mmf$releaseCachedTranslations(Language language, CallbackInfo ci) {

        LanguageReloadLeakCleanup.onLanguageReload();
    }
}
