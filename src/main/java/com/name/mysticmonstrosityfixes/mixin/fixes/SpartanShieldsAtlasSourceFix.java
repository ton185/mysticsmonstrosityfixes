package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Spartan Shields' shield_patterns atlas source uses "source": "entity". Directory sources are namespace-less, so
 * that typo copies every mod's entity textures into the shield atlas even though the shield atlas only draws shield
 * patterns. Correct the source path when the DirectoryLister is constructed.
 * <p>
 * This used to modify the FileToIdConverter argument inside DirectoryLister.run, but Iris also mixes into that method
 * (net.irisshaders.iris.mixin.texture.pbr.MixinDirectoryLister). The merged method no longer exposes the injection
 * point, so that mixin was dropped. The constructor is untouched by Iris, and changing the source path there keeps
 * the prefix unchanged, so the mod's sprite ids do not change and the correct vanilla entity/shield source is left
 * alone.
 */
@Mixin(DirectoryLister.class)
public class SpartanShieldsAtlasSourceFix {

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private static String mmf$fixShieldPatternsSource(String sourcePath) {

        if ("entity".equals(sourcePath)) {
            return "entity/shield";
        }
        if ("entity/".equals(sourcePath)) {
            return "entity/shield/";
        }
        return sourcePath;
    }
}
