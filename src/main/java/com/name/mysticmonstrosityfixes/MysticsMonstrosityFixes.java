package com.name.mysticmonstrosityfixes;

import com.name.mysticmonstrosityfixes.ae2.Ae2CraftingTreeMixinCompat;
import com.name.mysticmonstrosityfixes.ae2.ExtendedAePlusAcoCompat;
import com.name.mysticmonstrosityfixes.atlasviewer.AtlasViewerMipScreenEvents;
import com.name.mysticmonstrosityfixes.itemcollectors.ItemCollectorsInstantPickupFix;
import com.name.mysticmonstrosityfixes.tweaks.EnchantmentLevelBreakAnvilMixinTweak;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MysticsMonstrosityFixes.MODID)
public class MysticsMonstrosityFixes {

    public static final String MODID = "mysticmonstrosityfixes";

    public MysticsMonstrosityFixes(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, MysticMixinConfigSpec.SPEC);
        context.getModEventBus().addListener(this::commonSetup);
        context.getModEventBus().addListener(this::loadComplete);
        Ae2CraftingTreeMixinCompat.installIfEnabled();
        ExtendedAePlusAcoCompat.installIfEnabled();
        EnchantmentLevelBreakAnvilMixinTweak.installIfEnabled();
        if (ModList.get().isLoaded("itemcollectors")
                && MysticMixinConfig.allow("com.name.mysticmonstrosityfixes.mixin.fixes.ItemCollectorBlockEntityMixin")) {
            MinecraftForge.EVENT_BUS.register(ItemCollectorsInstantPickupFix.class);
        }
        if (ModList.get().isLoaded("atlasviewer")
                && MysticMixinConfig.allow("com.name.mysticmonstrosityfixes.mixin.fixes.AtlasViewerMipLevelsFix")) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(AtlasViewerMipScreenEvents.class));
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        Ae2CraftingTreeMixinCompat.installIfEnabled();
        ExtendedAePlusAcoCompat.installIfEnabled();
        EnchantmentLevelBreakAnvilMixinTweak.installIfEnabled();
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        Ae2CraftingTreeMixinCompat.installIfEnabled();
        ExtendedAePlusAcoCompat.installIfEnabled();
        EnchantmentLevelBreakAnvilMixinTweak.installIfEnabled();
    }
}
