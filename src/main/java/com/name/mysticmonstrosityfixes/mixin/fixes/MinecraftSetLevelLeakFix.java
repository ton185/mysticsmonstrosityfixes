package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.MultiplayerSessionLeakCleanup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftSetLevelLeakFix {

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("RETURN"))
    private void mmf$clearStaleLevelCaches(ClientLevel newLevel, CallbackInfo ci) {

        final Minecraft minecraft = (Minecraft) (Object) this;
        final Player player = minecraft.player;
        final Level oldLevel = player != null ? player.level() : null;
        MultiplayerSessionLeakCleanup.onLevelChanged(oldLevel, newLevel);
    }
}
