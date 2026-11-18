package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerSessionDataLeakFix {

    @Shadow
    @Final
    @Mutable
    private ClientAdvancements advancements;

    @Shadow
    @Mutable
    public CommandDispatcher<SharedSuggestionProvider> commands;

    @Inject(method = "close", at = @At("RETURN"))
    private void mmf$releaseSessionData(CallbackInfo ci) {

        this.advancements = null;
        this.commands = new CommandDispatcher<>();
    }
}
