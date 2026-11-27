package com.name.mysticmonstrosityfixes;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Map;

public final class MysticMixinConfigSpec {

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        for (Map.Entry<String, Boolean> entry : MysticMixinConfig.defaults().entrySet()) {
            String key = entry.getKey();
            builder.comment(MysticMixinConfig.comment(key))
                    .define(MysticMixinConfig.tomlPath(key), entry.getValue());
        }
        SPEC = builder.build();
    }

    private MysticMixinConfigSpec() {
    }
}
