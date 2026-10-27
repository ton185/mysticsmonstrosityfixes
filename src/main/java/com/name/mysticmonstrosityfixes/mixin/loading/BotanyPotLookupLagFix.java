package com.name.mysticmonstrosityfixes.mixin.loading;

import net.darkhax.botanypots.BotanyPotHelper;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.darkhax.botanypots.data.recipes.crop.Crop;
import net.darkhax.botanypots.data.recipes.soil.Soil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Serves soil and crop lookups from a cache instead of copying and scanning the whole recipe list on every call. The
 * cache lives in {@code BotanyPotLookupCache}.
 * <p>
 * The cache is reached by name. Naming that class here makes Mixin read it while this mixin is being applied, which is
 * the moment a pot recipe is first constructed. When that read fails, world creation aborts. A miss turns the cache
 * off and the original lookup runs.
 */
@Mixin(value = BotanyPotHelper.class, remap = false)
public class BotanyPotLookupLagFix {

    private static final String mmf$CACHE = "com.name.mysticmonstrosityfixes.botanypots.BotanyPotLookupCache";

    @Unique
    private static volatile boolean mmf$resolved;

    @Unique
    private static MethodHandle mmf$get;

    @Unique
    private static MethodHandle mmf$cachesSoil;

    @Unique
    private static MethodHandle mmf$findSoil;

    @Unique
    private static MethodHandle mmf$cachesCrop;

    @Unique
    private static MethodHandle mmf$findCrop;

    @Inject(method = "findSoil", at = @At("HEAD"), cancellable = true)
    private static void mmf$cachedSoilLookup(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack soilStack, CallbackInfoReturnable<Soil> cir) {

        final Object cache = mmf$cache(level);
        final Object[] found = new Object[1];

        if (cache == null || !Boolean.TRUE.equals(mmf$invoke(mmf$cachesSoil, cache)) || !mmf$invokeInto(mmf$findSoil, found, cache, level, pos, pot, soilStack)) {
            return;
        }

        cir.setReturnValue(BotanyPotHelper.EVENT_DISPATCHER.postSoilLookup(level, pos, pot, soilStack, (Soil) found[0]));
    }

    @Inject(method = "findCrop", at = @At("HEAD"), cancellable = true)
    private static void mmf$cachedCropLookup(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack stack, CallbackInfoReturnable<Crop> cir) {

        final Object cache = mmf$cache(level);
        final Object[] found = new Object[1];

        if (cache == null || !Boolean.TRUE.equals(mmf$invoke(mmf$cachesCrop, cache)) || !mmf$invokeInto(mmf$findCrop, found, cache, level, pos, pot, stack)) {
            return;
        }

        cir.setReturnValue(BotanyPotHelper.EVENT_DISPATCHER.postCropLookup(level, pos, pot, stack, (Crop) found[0]));
    }

    @Unique
    private static Object mmf$cache(Level level) {

        if (!mmf$resolved) {
            mmf$resolve();
        }

        return mmf$invoke(mmf$get, level);
    }

    /**
     * @return The call's result, or null when the handle is missing or the call throws. A lookup that found nothing
     *         also returns null, so callers use this only for results where null means "do not use the cache".
     */
    @Unique
    private static Object mmf$invoke(MethodHandle handle, Object... args) {

        if (handle == null) {
            return null;
        }

        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * @return false when the handle is missing or the call throws. A lookup that found nothing returns true and stores
     *         null, so the caller still posts that result.
     */
    @Unique
    private static boolean mmf$invokeInto(MethodHandle handle, Object[] out, Object... args) {

        if (handle == null) {
            return false;
        }

        try {
            out[0] = handle.invokeWithArguments(args);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Unique
    private static synchronized void mmf$resolve() {

        if (mmf$resolved) {
            return;
        }

        try {
            final Class<?> type = Class.forName(mmf$CACHE);
            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            mmf$get = lookup.findStatic(type, "get", MethodType.methodType(type, Level.class));
            mmf$cachesSoil = lookup.findVirtual(type, "cachesSoil", MethodType.methodType(boolean.class));
            mmf$findSoil = lookup.findVirtual(type, "findSoil", MethodType.methodType(Soil.class, Level.class, BlockPos.class, BlockEntityBotanyPot.class, ItemStack.class));
            mmf$cachesCrop = lookup.findVirtual(type, "cachesCrop", MethodType.methodType(boolean.class));
            mmf$findCrop = lookup.findVirtual(type, "findCrop", MethodType.methodType(Crop.class, Level.class, BlockPos.class, BlockEntityBotanyPot.class, ItemStack.class));
        } catch (ReflectiveOperationException exception) {
            LoggerFactory.getLogger("mysticmonstrosityfixes").warn("Botany pot lookup cache is off", exception);
        }

        mmf$resolved = true;
    }
}
