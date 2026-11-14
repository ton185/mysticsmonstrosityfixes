package com.name.mysticmonstrosityfixes.itemcollectors;

import com.supermartijn642.itemcollectors.CollectorBlockEntity;
import com.supermartijn642.itemcollectors.ItemCollectorsConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Item Collectors normally scans for item entities every tick. Loot explosions create thousands of
 * entities before that scan can run, and each one ticks and renders for at least a moment. Catch
 * item entities as they try to join the level, insert them into the nearest matching collector,
 * and cancel the join so they never enter the world.
 */
public final class ItemCollectorsInstantPickupFix {
    private static final Map<Level, Cache> CACHES = new WeakHashMap<>();

    private ItemCollectorsInstantPickupFix() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide || event.loadedFromDisk() || !itemEntity.isAlive()) {
            return;
        }
        ItemStack original = itemEntity.getItem();
        if (original.isEmpty()) {
            return;
        }
        CompoundTag data = itemEntity.getPersistentData();
        if (data.contains("PreventRemoteMovement") && !data.contains("AllowMachineRemoteMovement")) {
            return;
        }

        List<CollectorEntry> collectors = collectorsNear(level, itemEntity);
        if (collectors.isEmpty()) {
            return;
        }

        AABB itemBox = itemEntity.getBoundingBox();
        Vec3 itemPos = itemEntity.position();
        CollectorBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (CollectorEntry entry : collectors) {
            if (!entry.area().intersects(itemBox) || entry.collector().isRemoved()
                    || !(entry.collector() instanceof CollectorAccess access)) {
                continue;
            }
            if (!access.mysticmonstrosityfixes$matchesFilter(original)) {
                continue;
            }
            BlockPos pos = entry.collector().getBlockPos();
            double dx = itemPos.x - (pos.getX() + 0.5);
            double dy = itemPos.y - (pos.getY() + 0.5);
            double dz = itemPos.z - (pos.getZ() + 0.5);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry.collector();
            }
        }
        if (nearest == null) {
            return;
        }

        ItemStack remainder = ((CollectorAccess) nearest).mysticmonstrosityfixes$collect(original.copy());
        if (!remainder.isEmpty()) {
            for (CollectorEntry entry : collectors) {
                if (entry.collector() == nearest || !entry.area().intersects(itemBox)
                        || entry.collector().isRemoved()
                        || !(entry.collector() instanceof CollectorAccess access)) {
                    continue;
                }
                if (!access.mysticmonstrosityfixes$matchesFilter(remainder)) {
                    continue;
                }
                remainder = access.mysticmonstrosityfixes$collect(remainder);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
        if (remainder.isEmpty()) {
            itemEntity.setItem(ItemStack.EMPTY);
            event.setCanceled(true);
        } else if (remainder.getCount() != original.getCount()) {
            itemEntity.setItem(remainder);
        }
    }

    private static List<CollectorEntry> collectorsNear(Level level, ItemEntity entity) {
        Cache cache = CACHES.get(level);
        if (cache == null) {
            cache = new Cache();
            CACHES.put(level, cache);
        }
        long tick = level.getGameTime();
        if (cache.tick != tick) {
            cache.tick = tick;
            cache.chunks.clear();
        }

        ChunkPos chunkPos = entity.chunkPosition();
        long key = chunkPos.toLong();
        List<CollectorEntry> entries = cache.chunks.get(key);
        if (entries != null) {
            return entries;
        }

        entries = new ArrayList<>();
        int radius = chunkRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x + dx, chunkPos.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof CollectorBlockEntity collector && !collector.isRemoved()) {
                        entries.add(new CollectorEntry(collector, collector.getAffectedArea()));
                    }
                }
            }
        }
        cache.chunks.put(key, entries);
        return entries;
    }

    private static int chunkRadius() {
        try {
            int max = Math.max(
                    ItemCollectorsConfig.basicCollectorMaxRange.get(),
                    ItemCollectorsConfig.advancedCollectorMaxRange.get()
            );
            return Math.max(1, (max + 15) / 16);
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private record CollectorEntry(CollectorBlockEntity collector, AABB area) {
    }

    private static final class Cache {
        private long tick = Long.MIN_VALUE;
        private final Long2ObjectMap<List<CollectorEntry>> chunks = new Long2ObjectOpenHashMap<>();
    }
}
