package com.name.mysticmonstrosityfixes.mixin;

import com.mc_goodch.diamethyst_arrows.event.ModEventBusEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.PrintStream;

@Mixin(ModEventBusEvents.class)
public class DiamethystArrowNoTabNameLogger {
    @Redirect(at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;println(Ljava/lang/String;)V"), method = "extraCreativeModeTabs", remap = false)
    private static void dontLogTabName(PrintStream instance, String x) {

    }
}
