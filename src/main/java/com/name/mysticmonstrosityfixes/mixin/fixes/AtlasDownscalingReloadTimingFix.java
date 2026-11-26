package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.AtlasDownscalingTiming;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableResourceManager.class)
public class AtlasDownscalingReloadTimingFix {
    @Shadow
    @Final
    private PackType type;

    @Inject(method = "createReload", at = @At("HEAD"))
    private void mmf$resetTiming(
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> initialTask,
            List<PackResources> packs,
            CallbackInfoReturnable<ReloadInstance> cir
    ) {
        if (type == PackType.CLIENT_RESOURCES) {
            AtlasDownscalingTiming.reset();
        }
    }

    @Inject(method = "createReload", at = @At("RETURN"), cancellable = true)
    private void mmf$timeReload(
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> initialTask,
            List<PackResources> packs,
            CallbackInfoReturnable<ReloadInstance> cir
    ) {
        if (type == PackType.CLIENT_RESOURCES) {
            cir.setReturnValue(new TimedReloadInstance(cir.getReturnValue()));
        }
    }

    private static final class TimedReloadInstance implements ReloadInstance {
        private final ReloadInstance delegate;
        private final CompletableFuture<Void> done;

        private TimedReloadInstance(ReloadInstance delegate) {
            this.delegate = delegate;
            this.done = delegate.done().thenRun(AtlasDownscalingTiming::log);
        }

        @Override
        public CompletableFuture<?> done() {
            return done;
        }

        @Override
        public float getActualProgress() {
            return delegate.getActualProgress();
        }
    }
}
