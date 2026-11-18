package com.name.mysticmonstrosityfixes;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Mixin switches shared by the mixin plugin and the Forge config. The plugin needs the values
 * before Forge loads its config system, so the TOML file is read directly here as well. The
 * matching {@link MysticMixinConfigSpec} exposes the same options to Forge for in-game editing.
 */
public final class MysticMixinConfig {
    public static final String FILE_NAME = "mysticmonstrosityfixes-common.toml";

    private static final String LEGACY_FILE_NAME = "mysticmonstrosityfixes.properties";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, String> TOML_PATHS = new LinkedHashMap<>();
    private static final Map<String, String> COMMENTS = new LinkedHashMap<>();
    private static final Map<String, String> MIXIN_KEYS = new HashMap<>();
    private static final Map<String, Boolean> OPTIONS = new LinkedHashMap<>();
    private static boolean loaded;

    static {
        option("mixin.fixes.alexs_mobs_ghostly_pickaxe_bake_fix", true,
                "AlexsMobsGhostlyPickaxeBakeFix");
        option("mixin.fixes.botany_pot_lag_fix", true,
                "BotanyPotLagFix");
        option("mixin.fixes.botany_pot_lookup", true,
                "BotanyPotLookupLagFix", "IngredientAccessor", "RecipeManagerAccessor");
        option("mixin.fixes.capsule_disable_recall_enchantment", true,
                "CapsuleDisableRecallEnchantment");
        option("mixin.fixes.eidolon_reprised_heart_lag_fix", true,
                "EidolonRepraisedHeartLagFix");
        option("mixin.fixes.embers", true,
                "EmbersMixin");
        option("mixin.fixes.faktocraft_covers", true,
                "FaktocraftCoverBakeFix", "ModernFixCoverBakeFix");
        option("mixin.fixes.immersive_convergence", true,
                "ImmersiveConvergenceBakeFix", "SplitModelHandlerAccessor");
        option("mixin.fixes.lodestone", true,
                "LodestoneMixin");
        option("mixin.fixes.modernfix_dynamic_model_drain_fix", true,
                "ModernFixDynamicModelDrainFix");
        option("mixin.fixes.multiplayer_rejoin_leak", true,
                "ClientPacketListenerLeakFix", "ClientPacketListenerConnectionLeakFix", "ClientPacketListenerSessionDataLeakFix",
                "LocalPlayerConnectionLeakFix", "MinecraftClearLevelLeakFix", "MinecraftSetLevelLeakFix");
        option("mixin.fixes.resource_reload_leak", true,
                "LanguageReloadLeakFix", "TranslatableContentsLeakFix", "TranslatableContentsCacheAccessor");
        option("mixin.fixes.soulslike_weaponry_load_check", true,
                "MixinSoulslikeWeaponryLoadCheck");
        option("mixin.fixes.voltex_covers", true,
                "VoltexCoverBakeFix", "VoltexDynamicCoverFix");

        option("mixin.fixes.aether_cme", true,
                "AetherCMEFixMixin");
        option("mixin.fixes.atmosphere_shader_compile", true,
                "AtmosphereShaderCompileFix");
        option("mixin.fixes.bielgg_broken_script_recursion", true,
                "BielGGBrokenScriptRecursionFix");
        option("mixin.fixes.bielgg_weapon_spell_slots", true,
                "SwordSpellContainerFix", "AxeSpellContainerFix");
        option("mixin.fixes.blood_magic_null_capability", true,
                "BloodMagicNullCapabilityFix");
        option("mixin.fixes.capsule_preview_crash", true,
                "CapsulePreviewCrashFix");
        option("mixin.fixes.dimension_id", true,
                "DimensionIdManagementMixin", "DimensionIdRecordAccessor");
        option("mixin.fixes.everlasting_abilities_attributes", true,
                "EverlastingAbilitiesAttributeFix");
        option("mixin.fixes.flib_potion_effect_race", true,
                "FLibPotionEffectRaceFix", "MobEffectInstanceAccessor");
        option("mixin.fixes.forge_registry_alias_sync", true,
                "ForgeRegistryAliasSyncFix");
        option("mixin.fixes.ftb_library_null", true,
                "FTBLibraryNullFix");
        option("mixin.fixes.mahou_tsukai_shader_double_close", true,
                "MahouTsukaiShaderDoubleCloseFix");
        option("mixin.fixes.gui_scale_click", true,
                "GuiScaleClickFix");
        option("mixin.fixes.huge_mushroom", true,
                "HugeMushroomSpeciesMixin", "SpeciesAccessor");
        option("mixin.fixes.illage_projectile_category", true,
                "EntityTypeCategoryAccessor");
        option("mixin.fixes.inventory_pets_nbt_null", true,
                "InventoryPetsNbtNullFix");
        option("mixin.fixes.item_collectors_instant_pickup", true,
                "ItemCollectorBlockEntityMixin");
        option("mixin.fixes.jade_enum_plugin_load", true,
                "JadeEnumPluginLoadFix");
        option("mixin.fixes.jitl_client_essence_null", true,
                "JITLClientEssenceNullFix");
        option("mixin.fixes.jitl_death_crash", true,
                "JITLDeathCrashFix");
        option("mixin.fixes.jitl_offline_crash", true,
                "JITLOfflineCrashFix");
        option("mixin.fixes.krypton_reforged_var_int", true,
                "KryptonReforgedVarIntFix");
        option("mixin.fixes.minecontrollers_skull_register", true,
                "MinecontrollersSkullRegisterFix");
        option("mixin.fixes.moving_elevators_cabin_cl", true,
                "MovingElevatorsCabinCLCompat");
        option("mixin.fixes.remove_all_effects_cme", true,
                "LivingEntityRemoveAllEffectsCMEFix");
        option("mixin.fixes.placeable_bamboo_soil", true,
                "PlaceableBambooSoilFix");
        option("mixin.fixes.placeable_big_dripleaf_soil", true,
                "PlaceableBigDripleafSoilFix");
        option("mixin.fixes.placeable_big_dripleaf_stem_soil", true,
                "PlaceableBigDripleafStemSoilFix");
        option("mixin.fixes.placeable_bush_soil", true,
                "PlaceableBushSoilFix");
        option("mixin.fixes.placeable_dead_bush_soil", true,
                "PlaceableDeadBushSoilFix");
        option("mixin.fixes.placeable_sapling_soil", true,
                "PlaceableSaplingSoilFix");
        option("mixin.fixes.sol_carrot_foods_null", true,
                "SoLCarrotFoodsNullFix");
        option("mixin.fixes.solar_craft_shader_load_crash", true,
                "SolarCraftShaderLoadCrashFix");

        option("mixin.experimental", "mixin.experimental.enabled", false,
                "Master switch for experimental mixins. Changes require a game restart.");
        option("mixin.experimental.bootcache", "mixin.experimental.bootcache", true,
                "Enable the experimental boot resource cache mixins. Changes require a game restart.");
        option("mixin.experimental.blockatlas", "mixin.experimental.blockatlas", true,
                "Enable the experimental block atlas mixins. Changes require a game restart.");

        option("mixin.tweaks", "mixin.tweaks.enabled", false,
                "Master switch for tweaks. Changes require a game restart.");
        option("mixin.tweaks.alltheimbaium_disable_supply_points", "mixin.tweaks.alltheimbaium_disable_supply_points", true,
                "Disable Alltheimbaium supply point gains and always report 0 supply points. Changes require a game restart.",
                "AllTheImbaiumSupplyDataTweak", "AllTheImbaiumSupplyEventsTweak");
        option("mixin.tweaks.everlasting_abilities_uncap", "mixin.tweaks.everlasting_abilities_uncap", true,
                "Let Everlasting Abilities exceed the max level of abilities whose max level is above one, except Resistance. Changes require a game restart.",
                "EverlastingAbilitiesUncapTweak", "EverlastingAbilitiesCommandUncapTweak",
                "EverlastingAbilitiesAttributeTweak", "EverlastingAbilitiesTooltipTweak");
    }

    private MysticMixinConfig() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        OPTIONS.putAll(DEFAULTS);
        Path file = configFile(FILE_NAME);
        if (Files.isRegularFile(file)) {
            readToml(file);
        } else {
            Path legacy = configFile(LEGACY_FILE_NAME);
            if (Files.isRegularFile(legacy)) {
                readLegacy(legacy);
                writeToml(file);
                LOGGER.info("Migrated mixin options from {} to {}; the old file can be removed", legacy, file);
            }
        }
        LOGGER.info("Mixin options loaded from {} (experimental={}, tweaks={})",
                file, OPTIONS.get("mixin.experimental"), OPTIONS.get("mixin.tweaks"));
    }

    /**
     * @param mixinClassName the fully qualified mixin class
     * @return false when that mixin's option, or any parent option, is turned off
     */
    public static boolean allow(String mixinClassName) {
        load();
        String option = optionFor(mixinClassName);
        if (option == null) {
            return true;
        }
        String[] parts = option.split("\\.");
        StringBuilder path = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            path.append('.').append(parts[i]);
            Boolean value = OPTIONS.get(path.toString());
            if (value != null && !value) {
                return false;
            }
        }
        return true;
    }

    static Map<String, Boolean> defaults() {
        return DEFAULTS;
    }

    static String tomlPath(String key) {
        return TOML_PATHS.get(key);
    }

    static String comment(String key) {
        return COMMENTS.get(key);
    }

    /**
     * @return the option key for a mixin, or null when the mixin has no key and is left enabled
     */
    private static String optionFor(String mixinClassName) {
        int marker = mixinClassName == null ? -1 : mixinClassName.indexOf(".mixin.");
        if (marker < 0) {
            return null;
        }
        String relative = mixinClassName.substring(marker + ".mixin.".length());
        String key = MIXIN_KEYS.get(relative.substring(relative.lastIndexOf('.') + 1));
        if (key != null) {
            return key;
        }
        if (relative.startsWith("experimental.bootcache.")) {
            return "mixin.experimental.bootcache";
        }
        if (relative.startsWith("experimental.blockatlas.")) {
            return "mixin.experimental.blockatlas";
        }
        if (relative.startsWith("experimental.")) {
            return "mixin.experimental";
        }
        if (relative.startsWith("tweaks.")) {
            return "mixin.tweaks";
        }
        return null;
    }

    private static void option(String key, boolean defaultValue, String... mixinClassNames) {
        String name = key.substring(key.lastIndexOf('.') + 1);
        option(key, key, defaultValue, "Enable the " + name + " mixin. Changes require a game restart.", mixinClassNames);
    }

    private static void option(
            String key, String tomlPath, boolean defaultValue, String comment, String... mixinClassNames
    ) {
        DEFAULTS.put(key, defaultValue);
        TOML_PATHS.put(key, tomlPath);
        COMMENTS.put(key, comment);
        for (String mixinClassName : mixinClassNames) {
            MIXIN_KEYS.put(mixinClassName, key);
        }
    }

    private static Path configFile(String name) {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(name);
        } catch (Throwable ignored) {
            return Path.of("config", name);
        }
    }

    private static void readToml(Path file) {
        try (FileConfig config = FileConfig.of(file, TomlFormat.instance())) {
            config.load();
            for (String key : DEFAULTS.keySet()) {
                Object value = config.get(TOML_PATHS.get(key));
                if (value instanceof Boolean) {
                    OPTIONS.put(key, (Boolean) value);
                }
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not read mixin options from {}", file, exception);
        }
    }

    private static void readLegacy(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            if (properties.containsKey("mixin.loading") || properties.containsKey("mixin.fixes")) {
                LOGGER.warn("The generic mixin.loading and mixin.fixes options are ignored; use the per-mixin options in {}", file);
            }
            boolean migratedLoading = false;
            for (String key : DEFAULTS.keySet()) {
                String value = properties.getProperty(key);
                if (value == null && key.startsWith("mixin.fixes.")) {
                    value = properties.getProperty("mixin.loading." + key.substring("mixin.fixes.".length()));
                    migratedLoading = migratedLoading || value != null;
                }
                if (value != null) {
                    OPTIONS.put(key, Boolean.parseBoolean(value.trim()));
                }
            }
            if (migratedLoading) {
                LOGGER.info("Migrated old mixin.loading options from {}", file);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not read legacy mixin options from {}", file, exception);
        }
    }

    private static void writeToml(Path file) {
        try {
            CommentedConfig config = CommentedConfig.inMemory();
            for (Map.Entry<String, Boolean> entry : DEFAULTS.entrySet()) {
                String path = TOML_PATHS.get(entry.getKey());
                config.set(path, OPTIONS.get(entry.getKey()));
                config.setComment(path, COMMENTS.get(entry.getKey()));
            }
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new TomlWriter().write(config, writer);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not write mixin options to {}", file, exception);
        }
    }
}
