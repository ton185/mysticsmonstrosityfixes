package com.mystic.mysticsmonstrosityfixes;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MysticsMonstrosityFixes.MODID)
public class MysticsMonstrosityFixes {
    public static final String MODID = "mysticsmonstrosityfixes";
    private static final Logger LOGGER = LogUtils.getLogger();
    public MysticsMonstrosityFixes(IEventBus modEventBus, ModContainer modContainer) {
    }
}
