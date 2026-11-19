package com.name.mysticmonstrosityfixes.mixin.tweaks;

import com.committee.nova.avaritia_delight.item.InfinityCatalystCookieItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avaritia's Delight's infinity catalyst cookie returns the input stack from {@code finishUsingItem}, so one cookie
 * extends every active effect forever. Shrink the stack like normal food after the mod has applied its effect
 * extension, consume trigger, and stat. Creative players keep the item, matching vanilla food.
 */
@Mixin(value = InfinityCatalystCookieItem.class, remap = false)
public class AvaritiaCatalystCookieConsumeTweak {

    @Inject(method = "m_5922_", at = @At("RETURN"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$consumeCookie(
            ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> callback
    ) {

        final ItemStack result = callback.getReturnValue();
        if (result.isEmpty()) {
            return;
        }
        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return;
        }

        result.shrink(1);
        if (result.isEmpty()) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
