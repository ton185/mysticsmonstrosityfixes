package com.name.mysticmonstrosityfixes.mixin.tweaks;

import com.name.mysticmonstrosityfixes.tweaks.EverlastingAbilitiesUncapper;
import net.minecraft.network.chat.Component;
import org.cyclops.everlastingabilities.client.gui.ContainerScreenAbilityContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Drops the max level from the ability tooltip for uncapped abilities, so no infinity marker or
 * level fraction is shown.
 */
@Mixin(value = ContainerScreenAbilityContainer.class, remap = false)
public abstract class EverlastingAbilitiesTooltipTweak {
    @Redirect(
            method = "drawAbilitiesTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean mysticmonstrosityfixes$hideUncappedMax(List<Component> lines, Object line) {
        return lines.add(EverlastingAbilitiesUncapper.rewriteLevelLine(lines, (Component) line));
    }
}
