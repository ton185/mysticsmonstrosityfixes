package com.name.mysticmonstrosityfixes.mixin;

import com.ferreusveritas.dynamictrees.tree.species.Species;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes private Species internals used by the HugeMushroomSpecies rot override so it can faithfully mirror the
 * base Species.rot implementation.
 */
@Mixin(value = Species.class, remap = false)
public interface SpeciesAccessor {

    @Accessor(value = "doesRot", remap = false)
    boolean mmf$getDoesRot();

    @Accessor(value = "upFirst", remap = false)
    static Direction[] mmf$getUpFirst() {
        throw new AssertionError("Mixin accessor was not applied.");
    }
}