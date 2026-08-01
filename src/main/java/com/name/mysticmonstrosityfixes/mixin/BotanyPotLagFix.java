package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.botanypots.PotSyncCoalescer;
import net.darkhax.bookshelf.api.util.WorldHelper;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.darkhax.botanypots.block.inv.BotanyPotContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cuts the per pot tick cost without changing how fast pots grow.
 * <p>
 * The expensive part of a pot tick is not the growth logic, it is the bookkeeping around it:
 * <ul>
 * <li>Every inventory change calls {@code markDirty}, which serialises the whole 14 slot pot to NBT and broadcasts it to
 * every player tracking the chunk. Auto harvesting and hopper exporting do that several times per moved stack, so a
 * single harvest can send a dozen identical packets. They are collapsed into the one sync the tick actually needs.</li>
 * <li>A hopper pot looks up the inventory below itself every 20 ticks even when it has nothing to export.</li>
 * <li>Pots with nothing in them still run the full tick body.</li>
 * </ul>
 */
@Mixin(value = BlockEntityBotanyPot.class, remap = false)
public abstract class BotanyPotLagFix implements PotSyncCoalescer {

    @Unique
    private boolean mmf$syncQueued;

    @Override
    public boolean mmf$takeQueuedSync() {

        final boolean queued = this.mmf$syncQueued;
        this.mmf$syncQueued = false;
        return queued;
    }

    @Unique
    private BlockEntityBotanyPot mmf$self() {
        return (BlockEntityBotanyPot) (Object) this;
    }

    /**
     * Records that the pot wants to be synced instead of sending the packet immediately. The tick that made the change
     * flushes it, so the client still sees the change on the same tick, it just sees it once.
     */
    @Redirect(method = "markDirty", at = @At(value = "INVOKE", target = "Lnet/darkhax/bookshelf/api/util/WorldHelper;updateBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Z)V"))
    private void mmf$queueSync(BlockEntity blockEntity, boolean limitDistance) {

        this.mmf$syncQueued = true;
    }

    /**
     * Exporting first looks up the inventory below the pot, which is not free. There is no point doing that when every
     * storage slot is empty.
     */
    @Inject(method = "attemptExport", at = @At("HEAD"), cancellable = true)
    private void mmf$skipEmptyExport(CallbackInfo ci) {

        final BotanyPotContainer inventory = this.mmf$self().getInventory();

        for (int slot : BotanyPotContainer.STORAGE_SLOT) {
            if (!inventory.getItem(slot).isEmpty()) {
                return;
            }
        }

        ci.cancel();
    }

    /**
     * Skips the tick body for pots that have nothing to do at all. The pot must be completely empty, so there is nothing
     * for the container to revalidate and nothing to export, and it must not be holding leftover growth state that the
     * tick would otherwise have to reset.
     */
    @Inject(method = "tickPot", at = @At("HEAD"), cancellable = true)
    private static void mmf$skipIdlePots(Level level, BlockPos pos, BlockState state, BlockEntityBotanyPot pot, CallbackInfo ci) {

        if (pot.getSoil() == null && pot.getCrop() == null && pot.getGrowthTime() == -1 && !pot.isCropHarvestable() && pot.getComparatorLevel() == 0 && pot.getInventory().isEmpty()) {
            mmf$flushSync(pot);
            ci.cancel();
        }
    }

    @Inject(method = "tickPot", at = @At("RETURN"))
    private static void mmf$flushSyncOnTick(Level level, BlockPos pos, BlockState state, BlockEntityBotanyPot pot, CallbackInfo ci) {

        mmf$flushSync(pot);
    }

    @Unique
    private static void mmf$flushSync(BlockEntityBotanyPot pot) {

        if (((PotSyncCoalescer) pot).mmf$takeQueuedSync() && !pot.isRemoved()) {
            WorldHelper.updateBlockEntity(pot, false);
        }
    }
}
