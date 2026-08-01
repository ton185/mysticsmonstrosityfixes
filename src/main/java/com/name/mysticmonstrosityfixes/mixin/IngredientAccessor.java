package com.name.mysticmonstrosityfixes.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exposes Forge's ingredient invalidation counter. Ingredients cache the stacks they match, and Forge bumps this counter
 * whenever item tags change, which makes it a reliable signal that anything derived from ingredient matching has to be
 * recomputed.
 */
@Mixin(Ingredient.class)
public interface IngredientAccessor {

    @Accessor(value = "INVALIDATION_COUNTER", remap = false)
    static AtomicInteger mmf$getInvalidationCounter() {
        throw new AssertionError("Mixin accessor was not applied.");
    }
}
