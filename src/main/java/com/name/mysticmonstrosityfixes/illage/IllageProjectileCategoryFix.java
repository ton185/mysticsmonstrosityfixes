package com.name.mysticmonstrosityfixes.illage;

import com.name.mysticmonstrosityfixes.MysticsMonstrosityFixes;
import com.name.mysticmonstrosityfixes.mixin.fixes.EntityTypeCategoryAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Illage and Spillage registers its thrown axe and bone as {@link MobCategory#MONSTER}.
 * They are projectiles, so they have no attributes, and {@code DefaultAttributes.validate}
 * logs "Entity illageandspillage:axe/bone has no attributes" for every check.
 * Projectiles belong in {@link MobCategory#MISC}, which skips that check.
 */
@Mod.EventBusSubscriber(modid = MysticsMonstrosityFixes.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class IllageProjectileCategoryFix {
    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        markMisc("Axe");
        markMisc("Bone");
    }

    private static void markMisc(String fieldName) {
        try {
            Class<?> types = Class.forName("com.yellowbrossproductions.illageandspillage.init.ModEntityTypes");
            Object registryObject = types.getField(fieldName).get(null);
            Object entityType = registryObject.getClass().getMethod("get").invoke(registryObject);
            if (entityType instanceof EntityType<?> type) {
                ((EntityTypeCategoryAccessor) type).mysticmonstrosityfixes$setCategory(MobCategory.MISC);
            }
        } catch (ClassNotFoundException ignored) {
            // Illage and Spillage is not installed.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not retag illageandspillage projectile " + fieldName, exception);
        }
    }
}
