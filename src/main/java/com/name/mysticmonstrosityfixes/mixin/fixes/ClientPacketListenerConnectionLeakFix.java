package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.ClientNetworkConnectionClearer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerConnectionLeakFix implements ClientNetworkConnectionClearer {

    @Shadow
    @Final
    @Mutable
    private Connection connection;

    @Override
    public void mmf$clearNetworkConnection() {
        this.connection = null;
    }
}
