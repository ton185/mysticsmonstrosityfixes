package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Spartan Shields' shield_patterns atlas source uses "source": "entity". Directory sources are namespace-less, so
 * that typo copies every mod's entity textures into the shield atlas even though the shield atlas only draws shield
 * patterns. When a directory source lists entity/, list entity/shield instead. The sprite prefix is unchanged, and
 * the correct vanilla entity/shield source is left alone.
 */
@Mixin(DirectoryLister.class)
public class SpartanShieldsAtlasSourceFix {

    @ModifyArg(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/FileToIdConverter;<init>(Ljava/lang/String;Ljava/lang/String;)V"
            ),
            index = 0
    )
    private String mmf$fixShieldPatternsSource(String path) {

        return "entity/".equals(path) ? "entity/shield/" : path;
    }
}
