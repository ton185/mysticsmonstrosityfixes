package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists the full XP value of an orb instead of truncating it to a signed short.
 * <p>
 * Vanilla writes and reads {@code Value} with {@code putShort}/{@code getShort}, which is enough for
 * its 2477 XP cap but wraps or clips a single orb holding a large award when the chunk is saved. The
 * value is written as an int and restored from the int after vanilla finishes reading; old short tags
 * still load because {@code CompoundTag#getInt} widens any numeric tag.
 * <p>
 * This mixin is applied before the other orb mixins, so at {@code TAIL} its callback runs first. A
 * handler that snapshots the orb value while loading NBT, such as Clumps seeding its clumped map from
 * {@code Value}, therefore sees the restored int instead of vanilla's truncated short.
 */
@Mixin(value = ExperienceOrb.class, priority = 500)
public abstract class ExperienceOrbValuePersistenceFix {

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mmf$saveFullValue(CompoundTag tag, CallbackInfo callback) {

        tag.putInt("Value", ((ExperienceOrb) (Object) this).value);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mmf$readFullValue(CompoundTag tag, CallbackInfo callback) {

        if (tag.contains("Value", Tag.TAG_ANY_NUMERIC)) {
            ((ExperienceOrb) (Object) this).value = tag.getInt("Value");
        }
    }
}
