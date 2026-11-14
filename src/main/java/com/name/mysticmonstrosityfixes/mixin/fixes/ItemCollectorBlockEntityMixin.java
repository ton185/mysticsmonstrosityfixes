package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.itemcollectors.CollectorAccess;
import com.supermartijn642.itemcollectors.CollectorBlock;
import com.supermartijn642.itemcollectors.CollectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.function.Supplier;

/**
 * Exposes the collector's filter and output insertion to the join-level event handler. The handler
 * collects fresh item drops before they become item entities. The mod's normal tick scan stays
 * unchanged, so anything that was already in the world is still picked up on the next tick.
 */
@Mixin(value = CollectorBlockEntity.class, remap = false)
public abstract class ItemCollectorBlockEntityMixin implements CollectorAccess {
    @Shadow
    @Final
    private Supplier<Boolean> hasFilter;

    @Shadow
    @Final
    private List<ItemStack> filter;

    @Shadow
    private boolean filterWhitelist;

    @Shadow
    private boolean filterDurability;

    @Unique
    private LazyOptional<IItemHandler> mysticmonstrosityfixes$output = LazyOptional.empty();

    @Unique
    private long mysticmonstrosityfixes$outputTick = Long.MIN_VALUE;

    @Override
    public boolean mysticmonstrosityfixes$matchesFilter(ItemStack stack) {
        if (!this.hasFilter.get()) {
            return true;
        }
        boolean matches = false;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack filterStack = this.filter.get(slot);
            if (!filterStack.isEmpty() && ItemStack.isSameItem(filterStack, stack)
                    && (!this.filterDurability || ItemStack.isSameItemSameTags(filterStack, stack))) {
                matches = true;
                break;
            }
        }
        return matches == this.filterWhitelist;
    }

    @Override
    public ItemStack mysticmonstrosityfixes$collect(ItemStack stack) {
        if (stack.isEmpty() || !this.mysticmonstrosityfixes$matchesFilter(stack)) {
            return stack;
        }
        IItemHandler output = this.mysticmonstrosityfixes$getOutput();
        if (output == null || output.getSlots() <= 0) {
            return stack;
        }
        for (int slot = 0; slot < output.getSlots() && !stack.isEmpty(); slot++) {
            if (output.isItemValid(slot, stack)) {
                stack = output.insertItem(slot, stack, false);
            }
        }
        return stack;
    }

    @Unique
    private IItemHandler mysticmonstrosityfixes$getOutput() {
        CollectorBlockEntity self = (CollectorBlockEntity) (Object) this;
        Level level = self.getLevel();
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick != this.mysticmonstrosityfixes$outputTick || !this.mysticmonstrosityfixes$output.isPresent()) {
            this.mysticmonstrosityfixes$output = this.mysticmonstrosityfixes$resolveOutput();
            this.mysticmonstrosityfixes$outputTick = tick;
        }
        return this.mysticmonstrosityfixes$output.orElse(null);
    }

    @Unique
    private LazyOptional<IItemHandler> mysticmonstrosityfixes$resolveOutput() {
        CollectorBlockEntity self = (CollectorBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) {
            return LazyOptional.empty();
        }
        BlockState state = self.getBlockState();
        if (!state.hasProperty(CollectorBlock.DIRECTION)) {
            return LazyOptional.empty();
        }
        Direction direction = state.getValue(CollectorBlock.DIRECTION);
        BlockPos outputPos = self.getBlockPos().relative(direction);
        if (!level.isLoaded(outputPos)) {
            return LazyOptional.empty();
        }
        BlockEntity output = level.getBlockEntity(outputPos);
        if (output == null) {
            return LazyOptional.empty();
        }
        return output.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite());
    }
}
