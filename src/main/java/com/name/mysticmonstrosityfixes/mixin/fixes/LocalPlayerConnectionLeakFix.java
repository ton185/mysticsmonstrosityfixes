package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.LocalPlayerConnectionClearer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerConnectionLeakFix implements LocalPlayerConnectionClearer {

    @Shadow
    @Final
    @Mutable
    public ClientPacketListener connection;

    @Override
    public void mmf$clearConnection() {
        this.connection = null;
    }
}
