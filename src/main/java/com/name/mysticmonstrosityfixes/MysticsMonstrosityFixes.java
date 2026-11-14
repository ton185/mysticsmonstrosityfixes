package com.name.mysticmonstrosityfixes;

import com.name.mysticmonstrosityfixes.itemcollectors.ItemCollectorsInstantPickupFix;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MysticsMonstrosityFixes.MODID)
public class MysticsMonstrosityFixes {

    public static final String MODID = "mysticmonstrosityfixes";

    public MysticsMonstrosityFixes(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, MysticMixinConfigSpec.SPEC);
        if (ModList.get().isLoaded("itemcollectors")
                && MysticMixinConfig.allow("com.name.mysticmonstrosityfixes.mixin.fixes.ItemCollectorBlockEntityMixin")) {
            MinecraftForge.EVENT_BUS.register(ItemCollectorsInstantPickupFix.class);
        }
    }
}
