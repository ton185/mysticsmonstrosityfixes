/*
 * This file contains code derived from https://github.com/Lythom/capsule/tree/1.20 (MIT licensed)
 *
 * Original Copyright (c) 2020 Samuel Bouchet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * all I added to the changeTemplateIfDirty method is some try-catches. A hacky fix.
 */

package com.name.mysticmonstrosityfixes.mixin;


import capsule.client.render.CapsuleTemplateRenderer;
import capsule.client.render.FakeWorld;
import capsule.client.render.vbo.MultiVBORenderer;
import capsule.structure.CapsuleTemplate;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;
import java.util.List;

@Mixin(CapsuleTemplateRenderer.class)
public class CapsulePreviewCrashFix{
    @Shadow
    private StructurePlaceSettings lastPlacementSettings;

    @Shadow
    private boolean isWorldDirty;

    @Shadow
    public MultiVBORenderer renderBuffer;

    @Shadow
    public FakeWorld templateWorld;

    /**
     * @author ton185
     * @reason Wrap certain places in try catch to prevent blocks with bad handling from crashing the game
     */
    @Overwrite(remap = false)
    public boolean changeTemplateIfDirty(CapsuleTemplate template, Level world, BlockPos destPos, BlockPos
            offPos, StructurePlaceSettings placementSettings, int placeFlag) {
        if (lastPlacementSettings == null ||
                (placementSettings != null && (
                        placementSettings.getRotation() != lastPlacementSettings.getRotation()
                                || placementSettings.getMirror() != lastPlacementSettings.getMirror()))
        ) {
            isWorldDirty = true;
        }
        if (!isWorldDirty) return true;
        if (renderBuffer != null) renderBuffer.close();
        renderBuffer = null;
        lastPlacementSettings = placementSettings;
        if (template.palettes.isEmpty()) {
            return false;
        } else {
            templateWorld = new FakeWorld(world);

            List<StructureTemplate.StructureBlockInfo> list = CapsuleTemplate.Palette.getRandomPalette(placementSettings, template.palettes, destPos).blocks();
            if (!list.isEmpty() && template.size.getX() >= 1 && template.size.getY() >= 1 && template.size.getZ() >= 1) {
                BoundingBox mutableboundingbox = placementSettings.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(placementSettings.shouldKeepLiquids() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list2 = Lists.newArrayListWithCapacity(list.size());
                int i = Integer.MAX_VALUE;
                int j = Integer.MAX_VALUE;
                int k = Integer.MAX_VALUE;
                int l = Integer.MIN_VALUE;
                int i1 = Integer.MIN_VALUE;
                int j1 = Integer.MIN_VALUE;

                for (StructureTemplate.StructureBlockInfo template$blockinfo : CapsuleTemplate.processBlockInfos(template, templateWorld, offPos, placementSettings, list)) {
                    BlockPos blockpos = template$blockinfo.pos();
                    if (mutableboundingbox == null || mutableboundingbox.isInside(blockpos)) {
                        FluidState fluidstate = placementSettings.shouldKeepLiquids() ? templateWorld.getFluidState(blockpos) : null;
                        BlockState blockstate = template$blockinfo.state().mirror(placementSettings.getMirror()).rotate(templateWorld, blockpos, placementSettings.getRotation());
                        if (template$blockinfo.nbt() != null) {
                            BlockEntity BlockEntity = templateWorld.getBlockEntity(blockpos);
                            Clearable.tryClear(BlockEntity);
                            templateWorld.setBlock(blockpos, Blocks.BARRIER.defaultBlockState(), 20);
                        }

                        if (templateWorld.setBlock(blockpos, blockstate, placeFlag)) {
                            i = Math.min(i, blockpos.getX());
                            j = Math.min(j, blockpos.getY());
                            k = Math.min(k, blockpos.getZ());
                            l = Math.max(l, blockpos.getX());
                            i1 = Math.max(i1, blockpos.getY());
                            j1 = Math.max(j1, blockpos.getZ());
                            list2.add(Pair.of(blockpos, template$blockinfo.nbt()));

                            if (fluidstate != null && blockstate.getBlock() instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) blockstate.getBlock()).placeLiquid(templateWorld, blockpos, blockstate, fluidstate);
                                if (!fluidstate.isSource()) {
                                    list1.add(blockpos);
                                }
                            }
                        }
                    }
                }


                boolean flag = true;
                Direction[] adirection = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                while (flag && !list1.isEmpty()) {
                    flag = false;
                    Iterator<BlockPos> iterator = list1.iterator();

                    while (iterator.hasNext()) {
                        BlockPos blockpos2 = iterator.next();
                        BlockPos blockpos3 = blockpos2;
                        FluidState fluidstate2 = templateWorld.getFluidState(blockpos2);

                        for (int k1 = 0; k1 < adirection.length && !fluidstate2.isSource(); ++k1) {
                            BlockPos blockpos1 = blockpos3.relative(adirection[k1]);
                            FluidState fluidstate1 = templateWorld.getFluidState(blockpos1);
                            if (fluidstate1.getHeight(templateWorld, blockpos1) > fluidstate2.getHeight(templateWorld, blockpos3) || fluidstate1.isSource() && !fluidstate2.isSource()) {
                                fluidstate2 = fluidstate1;
                                blockpos3 = blockpos1;
                            }
                        }

                        if (fluidstate2.isSource()) {
                            BlockState blockstate2 = templateWorld.getBlockState(blockpos2);
                            Block block = blockstate2.getBlock();
                            if (block instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) block).placeLiquid(templateWorld, blockpos2, blockstate2, fluidstate2);
                                flag = true;
                                iterator.remove();
                            }
                        }
                    }
                }

                if (i <= l) {
                    try {
                        if (!placementSettings.getKnownShape()) {
                            try {
                                DiscreteVoxelShape voxelshapepart = new BitSetDiscreteVoxelShape(l - i + 1, i1 - j + 1, j1 - k + 1);
                                int l1 = i;
                                int i2 = j;
                                int j2 = k;

                                for (Pair<BlockPos, CompoundTag> pair1 : list2) {
                                    BlockPos blockpos5 = pair1.getFirst();
                                    voxelshapepart.fill(blockpos5.getX() - l1, blockpos5.getY() - i2, blockpos5.getZ() - j2);
                                }

                                StructureTemplate.updateShapeAtEdge(templateWorld, placeFlag, voxelshapepart, l1, i2, j2);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        for (Pair<BlockPos, CompoundTag> pair : list2) {
                            try {
                                BlockPos blockpos4 = pair.getFirst();
                                if (!placementSettings.getKnownShape()) {
                                    BlockState blockstate1 = templateWorld.getBlockState(blockpos4);
                                    BlockState blockstate3 = Block.updateFromNeighbourShapes(blockstate1, templateWorld, blockpos4);
                                    if (blockstate1 != blockstate3) {
                                        templateWorld.setBlock(blockpos4, blockstate3, placeFlag & -2 | 16);
                                    }

                                    templateWorld.blockUpdated(blockpos4, blockstate3.getBlock());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                isWorldDirty = false;
                return true;
            } else {
                isWorldDirty = false;
                return false;
            }
        }
    }
}
