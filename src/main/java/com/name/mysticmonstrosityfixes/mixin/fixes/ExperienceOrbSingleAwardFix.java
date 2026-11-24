package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spawns every XP reward as one orb instead of letting vanilla split it into pieces of at most 2477
 * XP.
 * <p>
 * {@code ExperienceOrb#award} calls {@code tryMergeToExisting} once per piece, and that merge check
 * scans every orb in a one block cube around the drop. The XP multipliers in this pack can make a
 * single kill award millions of points, so one death can run hundreds of scans, each streaming over
 * every orb near the corpse. Clumps and ServerCore make the scan more expensive by building and
 * reducing the merge maps. One award should create one orb, so the whole amount is spawned once and
 * the merge check is skipped entirely.
 * <p>
 * The high priority is not a dependency on Clumps or any other mod. It only makes this HEAD callback
 * run first when another mixin also injects at the start of {@code award}, so the cancellation always
 * happens before any merge logic. Spawning the orb directly also bypasses Clumps' spawn path without
 * calling its code: Clumps hooks {@code tryMergeToExisting}, the merge helpers, and
 * {@code playerTouch}, but not {@code award} or the orb constructor. Its instant pickup is untouched,
 * because {@code playerTouch} seeds the clumped map from the orb's value, so the single orb still pays
 * out the whole award at once. Orbs merged together after they spawn keep Clumps' normal behavior.
 */
@Mixin(value = ExperienceOrb.class, priority = 2000)
public abstract class ExperienceOrbSingleAwardFix {

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private static void mmf$spawnSingleOrb(ServerLevel level, Vec3 pos, int amount, CallbackInfo callback) {

        if (amount > 0) {
            level.addFreshEntity(new ExperienceOrb(level, pos.x(), pos.y(), pos.z(), amount));
        }
        callback.cancel();
    }
}
