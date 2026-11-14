package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.example.chaotic_world_content.brokenscript.BrokenScriptCloneEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BielGG's broken-script projectile check follows {@link net.minecraft.world.entity.projectile.Projectile#getOwner()}
 * recursively without tracking the entities it has already seen. A projectile owner loop therefore
 * recurses until the stack overflows. Track the current owner chain per thread and treat a repeat
 * as "not part of the broken script", with a depth cap as a second guard.
 */
@Mixin(value = BrokenScriptCloneEvents.class, remap = false)
public final class BielGGBrokenScriptRecursionFix {
    @Unique
    private static final int mysticmonstrosityfixes$maxDepth = 256;

    @Unique
    private static final Map<Thread, Set<Entity>> mysticmonstrosityfixes$visited = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Thread, Integer> mysticmonstrosityfixes$depth = new ConcurrentHashMap<>();

    @Inject(
            method = "sourceBelongsToBrokenScript(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$breakOwnerLoop(
            Level level, Entity entity, CallbackInfoReturnable<Boolean> callback
    ) {
        if (entity == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        int depth = mysticmonstrosityfixes$depth.getOrDefault(thread, 0);
        if (depth >= mysticmonstrosityfixes$maxDepth) {
            callback.setReturnValue(false);
            callback.cancel();
            return;
        }
        Set<Entity> visited;
        if (depth == 0) {
            visited = Collections.newSetFromMap(new IdentityHashMap<>());
            mysticmonstrosityfixes$visited.put(thread, visited);
        } else {
            visited = mysticmonstrosityfixes$visited.get(thread);
            if (visited == null) {
                visited = Collections.newSetFromMap(new IdentityHashMap<>());
                mysticmonstrosityfixes$visited.put(thread, visited);
            }
        }
        if (!visited.add(entity)) {
            callback.setReturnValue(false);
            callback.cancel();
            return;
        }
        mysticmonstrosityfixes$depth.put(thread, depth + 1);
    }

    @Inject(
            method = "sourceBelongsToBrokenScript(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private static void mysticmonstrosityfixes$clearOwnerChain(
            Level level, Entity entity, CallbackInfoReturnable<Boolean> callback
    ) {
        if (entity == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        Integer depth = mysticmonstrosityfixes$depth.get(thread);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            mysticmonstrosityfixes$depth.remove(thread);
            mysticmonstrosityfixes$visited.remove(thread);
        } else {
            mysticmonstrosityfixes$depth.put(thread, depth - 1);
        }
    }
}
