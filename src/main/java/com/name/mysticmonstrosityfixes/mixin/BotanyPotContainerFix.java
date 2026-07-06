package com.name.mysticmonstrosityfixes.mixin;

import net.darkhax.botanypots.BotanyPotHelper;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.darkhax.botanypots.block.inv.BotanyPotContainer;
import net.darkhax.botanypots.data.recipes.crop.Crop;
import net.darkhax.botanypots.data.recipes.soil.Soil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BotanyPotContainer.class)
public class BotanyPotContainerFix {

    @Unique
    private ItemStack lastSoilStack = ItemStack.EMPTY;
    @Unique
    private ItemStack lastCropStack = ItemStack.EMPTY;

    @Shadow
    private Soil soil;
    @Shadow
    private Crop crop;
    @Shadow
    private BlockEntityBotanyPot potEntity;

    @Unique
    private BotanyPotContainer self() {
        return (BotanyPotContainer) (Object) this;
    }

    @Redirect(method = {"setChanged", "update"}, at = @At(value = "INVOKE", target = "Lnet/darkhax/botanypots/BotanyPotHelper;findSoil(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;Lnet/minecraft/world/item/ItemStack;)Lnet/darkhax/botanypots/data/recipes/soil/Soil;"), remap = false)
    private Soil useCachedSoil(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack stack) {
        ItemStack current = self().getSoilStack();
        if (ItemStack.isSameItemSameTags(lastSoilStack, current)) {
            return this.soil;
        }
        this.lastSoilStack = current.copy();
        this.soil = BotanyPotHelper.findSoil(level, pos, pot, stack);
        return this.soil;
    }

    @Redirect(method = {"setChanged", "update"}, at = @At(value = "INVOKE", target = "Lnet/darkhax/botanypots/BotanyPotHelper;findCrop(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/darkhax/botanypots/block/BlockEntityBotanyPot;Lnet/minecraft/world/item/ItemStack;)Lnet/darkhax/botanypots/data/recipes/crop/Crop;"), remap = false)
    private Crop useCachedCrop(Level level, BlockPos pos, BlockEntityBotanyPot pot, ItemStack stack) {
        ItemStack current = self().getCropStack();
        if (ItemStack.isSameItemSameTags(lastCropStack, current)) {
            return this.crop;
        }
        this.lastCropStack = current.copy();
        this.crop = BotanyPotHelper.findCrop(level, pos, pot, stack);
        return this.crop;
    }
}
