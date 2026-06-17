package com.name.mysticmonstrosityfixes.mixin;

import com.lhy.wcwt.compat.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ByteBufCodecs.class)
public class WcwtByteBufCodecsFix {

    /**
     * @author Mysticpasta1
     * @reason Handle null registryAccess crashes on dedicated server during network codec registration
     */
    @Overwrite(remap = false)
    private static Registry<Item> itemRegistry(RegistryAccess registryAccess) {
        if (registryAccess != null) {
            return registryAccess.registry(Registries.ITEM).orElseThrow();
        }
        return BuiltInRegistries.ITEM;
    }
}
