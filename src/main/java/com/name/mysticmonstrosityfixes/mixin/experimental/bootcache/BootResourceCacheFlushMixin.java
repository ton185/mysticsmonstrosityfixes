package com.name.mysticmonstrosityfixes.mixin.experimental.bootcache;

import com.name.mysticmonstrosityfixes.BootResourceCache;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public class BootResourceCacheFlushMixin {
    @Inject(method = "createReload", at = @At("RETURN"))
    private void mysticmonstrosityfixes$saveBootCache(Executor backgroundExecutor, Executor gameExecutor,
                                                      CompletableFuture<Void> waitingFor, List<?> listeners,
                                                      CallbackInfoReturnable<ReloadInstance> callback) {
        ReloadInstance reload = callback.getReturnValue();
        if (reload != null) {
            reload.done().whenComplete((value, error) -> BootResourceCache.flush());
        }
    }
}
