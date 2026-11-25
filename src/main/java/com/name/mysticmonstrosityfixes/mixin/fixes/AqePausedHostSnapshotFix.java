package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.syaru.advancedquantumengineering.integration.AQEHostSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/**
 * AQE 2.2.x marks a paused opaque host as overcommitted but builds its snapshot with
 * reserved == physicalCapacity and available == 0. AQEHostSnapshot requires an overcommitted ledger to
 * have reserved > capacity, so loading a crafting CPU whose ACO payload is paused (for example after
 * ACO is removed) throws "invalid overcommitted host snapshot" and aborts the server.
 * <p>
 * The paused host intentionally preserves the opaque payload for when ACO returns. This injects a
 * valid ledger for the snapshot without dropping that payload: a genuinely overcommitted paused host
 * keeps overcommitted=true, otherwise the snapshot reports the real remaining capacity.
 */
@Mixin(targets = "com.syaru.advancedquantumengineering.integration.PausedOpaqueBigCraftingHost", remap = false)
public abstract class AqePausedHostSnapshotFix {

    @Shadow
    public abstract BigInteger physicalCapacity();

    @Shadow
    public abstract BigInteger reserved();

    @Shadow
    public abstract String backendId();

    @Inject(method = "snapshot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$validPausedSnapshot(long revision, CallbackInfoReturnable<AQEHostSnapshot> callback) {
        BigInteger capacity = physicalCapacity();
        BigInteger reserved = reserved();
        String backend = backendId();
        if (backend == null) {
            backend = "paused";
        }
        long safeRevision = Math.max(0L, revision);
        if (reserved.compareTo(capacity) > 0) {
            callback.setReturnValue(new AQEHostSnapshot(
                    safeRevision, capacity, reserved, BigInteger.ZERO, 0L, 0L, 0L, true, backend));
        } else {
            callback.setReturnValue(new AQEHostSnapshot(
                    safeRevision, capacity, reserved, capacity.subtract(reserved), 0L, 0L, 0L, false, backend));
        }
    }
}
