package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Minecontrollers reflects over every field of the skull block-entity type and logs
 * "registerSkullBlocks error" for each field that is not the valid-block set. Registration
 * itself still succeeds on the one matching field. This does that one update and does not
 * log the other fields.
 */
@Mixin(targets = "com.abadon.minecontrollers.MinecontrollersBlocks", remap = false)
public class MinecontrollersSkullRegisterFix {
    @Inject(method = "registerSkullBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void registerSkullsQuietly(CallbackInfo ci) {
        ci.cancel();
        try {
            if (!addCustomSkulls(BlockEntityType.SKULL)) {
                LogUtils.getLogger().error("Minecontrollers: registerSkullBlocks error");
            }
        } catch (Throwable thrown) {
            LogUtils.getLogger().error("Minecontrollers: registerSkullBlocks error", thrown);
        }
    }

    private static boolean addCustomSkulls(BlockEntityType<?> skullType) throws ReflectiveOperationException {
        Class<?> blocksClass = Class.forName("com.abadon.minecontrollers.MinecontrollersBlocks");
        Object registered = blocksClass.getField("skullBlocks").get(null);
        if (!(registered instanceof Set<?> skullBlocks)) {
            return false;
        }

        Class<?> type = skullType.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(skullType);
                if (!(value instanceof Set<?> validBlocks) || !validBlocks.contains(Blocks.PLAYER_HEAD)) {
                    continue;
                }
                Set<Block> expanded = new HashSet<>();
                for (Object block : validBlocks) {
                    if (block instanceof Block valid) {
                        expanded.add(valid);
                    }
                }
                for (Object entry : skullBlocks) {
                    Object block = entry.getClass().getMethod("get").invoke(entry);
                    if (block instanceof Block skull) {
                        expanded.add(skull);
                    }
                }
                field.set(skullType, expanded);
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
