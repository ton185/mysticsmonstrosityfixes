package com.name.mysticmonstrosityfixes;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Map;

public final class MysticMixinConfigSpec {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATLAS_DOWNSCALING_ENTRIES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        for (Map.Entry<String, Boolean> entry : MysticMixinConfig.defaults().entrySet()) {
            String key = entry.getKey();
            builder.comment(MysticMixinConfig.comment(key))
                    .define(MysticMixinConfig.tomlPath(key), entry.getValue());
        }
        ATLAS_DOWNSCALING_ENTRIES = builder
                .comment(
                        "Sprites to downscale before they are stitched into an atlas, as \"<sprite id regex>=<factor>\".",
                        "The regex must match the whole sprite id without the .png extension, for example \"somemod:entities/giant_sprite\". Use single quotes in TOML when the regex contains backslashes.",
                        "A factor of 1 halves both dimensions, 2 quarters them, and so on. Dimensions are floored and never drop below 1.",
                        "The sprite's maximum mip level falls with the new dimensions, stopping at 0.",
                        "Entries are checked in order and the first match wins. Changes require a game restart.")
                .defineListAllowEmpty("mixin.fixes.atlas_downscaling_entries", List.<String>of(), value -> value instanceof String);
        SPEC = builder.build();
    }

    private MysticMixinConfigSpec() {
    }
}
