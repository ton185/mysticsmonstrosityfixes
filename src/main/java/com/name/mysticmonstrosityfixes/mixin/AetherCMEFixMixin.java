package com.name.mysticmonstrosityfixes.mixin;

import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(EuclideanGameEventListenerRegistry.class)
public class AetherCMEFixMixin {

    /**
     * Redirect the iterator used in dispatch (m_245521_) to iterate over a
     * snapshot copy, preventing ConcurrentModificationException when a listener
     * triggers a game event that adds/removes other listeners mid-iteration.
     */
    @Redirect(
        method = "visitInRangeListeners",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
        )
    )
    private java.util.Iterator<GameEventListener> fixCME(List<GameEventListener> list) {
        return new ArrayList<>(list).iterator();
    }
}