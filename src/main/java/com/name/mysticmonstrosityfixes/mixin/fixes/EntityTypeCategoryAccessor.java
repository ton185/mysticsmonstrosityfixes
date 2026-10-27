package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityType.class)
public interface EntityTypeCategoryAccessor {
    @Accessor("category")
    @Mutable
    void mysticmonstrosityfixes$setCategory(MobCategory category);
}
