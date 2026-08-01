package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.botanypots.BotanyPotLookupCache;
import net.darkhax.botanypots.BotanyPotHelper;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.darkhax.botanypots.data.recipes.crop.Crop;
import net.darkhax.botanypots.data.recipes.soil.Soil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serves soil and crop lookups from a cache instead of copying and scanning the whole recipe list on every call. See
 * {@link BotanyPotLookupCache} for what is cached and when it is dropped.
 */
@Mixin(value = BotanyPotHelper.class, remap = false)
public class BotanyPotLookupLagFix {

    @Inject(method = "findSoil", at = @At("HEAD"), cancellable = true)
    private static void mmf$cachedSoilLookup(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack soilStack, CallbackInfoReturnable<Soil> cir) {

        final BotanyPotLookupCache cache = BotanyPotLookupCache.get(level);

        if (cache != null && cache.cachesSoil()) {
            final Soil found = cache.findSoil(level, pos, pot, soilStack);
            cir.setReturnValue(BotanyPotHelper.EVENT_DISPATCHER.postSoilLookup(level, pos, pot, soilStack, found));
        }
    }

    @Inject(method = "findCrop", at = @At("HEAD"), cancellable = true)
    private static void mmf$cachedCropLookup(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack stack, CallbackInfoReturnable<Crop> cir) {

        final BotanyPotLookupCache cache = BotanyPotLookupCache.get(level);

        if (cache != null && cache.cachesCrop()) {
            final Crop found = cache.findCrop(level, pos, pot, stack);
            cir.setReturnValue(BotanyPotHelper.EVENT_DISPATCHER.postCropLookup(level, pos, pot, stack, found));
        }
    }
}
