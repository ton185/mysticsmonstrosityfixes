package com.name.mysticmonstrosityfixes.mixin;

import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityBotanyPot.class)
public class BotanyPotLagFix {

    private static boolean throttleGrowthShouldReset = false;

    @Inject(at = @At("HEAD"), method = "tickPot", cancellable = true, remap = false)
    private static void skipEmptyPots(Level level, BlockPos pos, BlockState state, BlockEntityBotanyPot pot, CallbackInfo ci) {
        if (pot.getSoil() == null || pot.getCrop() == null) {
            ci.cancel();
        }
    }

    @Redirect(method = "tickPot", at = @At(value = "INVOKE", target = "Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;areGrowthConditionsMet()Z"), remap = false)
    private static boolean throttleGrowth(BlockEntityBotanyPot pot) {
        if (!pot.areGrowthConditionsMet()) {
            throttleGrowthShouldReset = true;
            return false;
        }
        throttleGrowthShouldReset = (pot.getLevel().getGameTime() + pot.getBlockPos().asLong()) % 4 != 0;
        return !throttleGrowthShouldReset;
    }

    @Redirect(method = "tickPot", at = @At(value = "INVOKE", target = "Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;resetGrowth()V"), remap = false)
    private static void skipResetOnThrottle(BlockEntityBotanyPot pot) {
        if (throttleGrowthShouldReset) {
            throttleGrowthShouldReset = false;
            pot.resetGrowth();
        }
    }
}
