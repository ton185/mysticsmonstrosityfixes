package com.name.mysticmonstrosityfixes.mixin.fixes;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.name.mysticmonstrosityfixes.ae2.ExactAmountView;
import com.name.mysticmonstrosityfixes.ae2.MmfExactStorageHolder;
import com.name.mysticmonstrosityfixes.ae2.MmfInfinityStorageAccess;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import com.syaru.ae2craftingoptimizer.integration.ExactBigIntegerCellConsistency;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Re-implements ACO 1.5.33's ExtendedAE Plus BigInteger cell accessor against ExtendedAE Plus 1.6.4.
 * ACO's exact storage bridge needs a live amount map, a stable identity across simulation/commit,
 * exact total/type setters, and a save hook.
 */
@Mixin(value = InfinityBigIntegerCellInventory.class, priority = 500, remap = false)
public abstract class ExtendedAePlusBigIntegerCellAccessFix
        implements ExtendedAePlusBigIntegerCellInventoryAccess, MmfExactStorageHolder {

    @Unique
    private ExactAmountView mysticmonstrosityfixes$exactView;

    @Shadow
    private InfinityDataStorage getExistingCellStorage() {
        throw new AssertionError();
    }

    @Shadow
    private InfinityDataStorage getWritableCellStorage() {
        throw new AssertionError();
    }

    @Shadow
    private void saveChanges() {
        throw new AssertionError();
    }

    @Shadow
    private UUID assignNewUUID() {
        throw new AssertionError();
    }

    @Shadow
    public abstract boolean hasUUID();

    @Shadow
    public abstract UUID getUUID();

    @Override
    public InfinityDataStorage mmf$storage(boolean create) {
        InfinityDataStorage storage = getExistingCellStorage();
        if (storage != null) {
            return storage;
        }
        if (!create) {
            return null;
        }
        if (!hasUUID()) {
            assignNewUUID();
        }
        return getWritableCellStorage();
    }

    @Override
    public void mmf$recalculateItemCount() {
        InfinityDataStorage storage = mmf$storage(false);
        if (storage instanceof MmfInfinityStorageAccess access) {
            access.mmf$recalculateItemCount();
        }
    }

    @Inject(method = "insert", at = @At("RETURN"), remap = false)
    private void mysticmonstrosityfixes$recordAfterInsert(
            AEKey key, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> callback
    ) {
        mysticmonstrosityfixes$recordAfterNormalMutation(mode, callback);
    }

    @Inject(method = "extract", at = @At("RETURN"), remap = false)
    private void mysticmonstrosityfixes$recordAfterExtract(
            AEKey key, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> callback
    ) {
        mysticmonstrosityfixes$recordAfterNormalMutation(mode, callback);
    }

    @Unique
    private void mysticmonstrosityfixes$recordAfterNormalMutation(
            Actionable mode, CallbackInfoReturnable<Long> callback
    ) {
        if (mode != Actionable.MODULATE) {
            return;
        }
        Long changed = callback.getReturnValue();
        if (changed == null || changed <= 0) {
            return;
        }
        InfinityDataStorage storage = mmf$storage(false);
        if (storage == null) {
            return;
        }
        Object2ObjectMap<AEKey, BigInteger> map = aco$getExactStoredAmounts();
        if (ExactBigIntegerCellConsistency.expectedTotal(map).isPresent()) {
            ExactBigIntegerCellConsistency.record(map, storage.getItemCount());
        }
    }

    @Override
    public Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts() {
        if (mysticmonstrosityfixes$exactView == null) {
            mysticmonstrosityfixes$exactView = new ExactAmountView(this);
        }
        return mysticmonstrosityfixes$exactView;
    }

    @Override
    public int aco$getExactStoredTypeCount() {
        InfinityDataStorage storage = mmf$storage(false);
        return storage == null ? 0 : storage.size();
    }

    @Override
    public void aco$setExactStoredTypeCount(int types) {
    }

    @Override
    public BigInteger aco$getExactStoredTotal() {
        InfinityDataStorage storage = mmf$storage(false);
        return storage == null ? BigInteger.ZERO : storage.getItemCount();
    }

    @Override
    public void aco$setExactStoredTotal(BigInteger total) {
        InfinityDataStorage storage = mmf$storage(true);
        if (storage instanceof MmfInfinityStorageAccess access) {
            access.mmf$setExactItemCount(total);
        }
    }

    @Override
    public void aco$saveExactChanges() {
        saveChanges();
    }

    @Override
    public boolean aco$hasExactStorageUuid() {
        return hasUUID();
    }

    @Override
    public UUID aco$getExactStorageUuid() {
        return getUUID();
    }

    @Override
    public UUID aco$assignExactStorageUuid() {
        return assignNewUUID();
    }
}
