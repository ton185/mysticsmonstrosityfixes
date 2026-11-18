package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the per-component language cache so {@code LanguageReloadLeakCleanup} can release the
 * previous {@code ClientLanguage} and the strings translated from it when the language reloads.
 */
@Mixin(TranslatableContents.class)
public interface TranslatableContentsCacheAccessor {

    @Accessor("decomposedWith")
    void mmf$setDecomposedWith(Language language);

    @Accessor("decomposedParts")
    void mmf$setDecomposedParts(List<FormattedText> parts);
}
