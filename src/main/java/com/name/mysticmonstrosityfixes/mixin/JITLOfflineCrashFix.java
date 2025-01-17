package com.name.mysticmonstrosityfixes.mixin;

import net.jitl.client.ClientLoginChecker;
import net.jitl.core.helper.InternetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLoginChecker.class)
public class JITLOfflineCrashFix {
    @Redirect(method = "onPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/jitl/core/helper/InternetHandler;isOnline()Z"), remap = false)
    private static boolean isOnlineNoThrow() {
        try {
            return InternetHandler.isOnline();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Redirect(method = "onPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/jitl/core/helper/InternetHandler;isUpdateAvailable()Z"), remap = false)
    private static boolean isUpdateAvailableNoThrow() {
        try {
            return InternetHandler.isOnline();
        } catch (Exception ignored) {
            return false;
        }
    }
}
