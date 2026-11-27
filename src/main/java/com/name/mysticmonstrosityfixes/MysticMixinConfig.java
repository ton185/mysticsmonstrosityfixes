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
        fix("mixin.fixes.avaritia_recipe_lookup_hang",
                "Replace Avaritia's exponential shapeless recipe matching with a polynomial matcher.",
                "AvaritiaShapelessRecipeHangFix", "AvaritiaCatalystRecipeHangFix");
        fix("mixin.fixes.botany_pot_lag_fix",
                "Cut Botany Pots tick cost by collapsing repeated syncs and skipping idle pots and empty exports.",
                "BotanyPotLagFix");
        fix("mixin.fixes.botany_pot_lookup",
                "Cache Botany Pots soil and crop recipe lookups instead of scanning the full recipe list on every call.",
                "BotanyPotLookupLagFix", "IngredientAccessor", "RecipeManagerAccessor");
        fix("mixin.fixes.capsule_disable_recall_enchantment",
                "Disable Capsule's recall enchantment tick, which was a source of lag.",
                "CapsuleDisableRecallEnchantment");
        fix("mixin.fixes.eidolon_reprised_heart_lag_fix",
                "Disable Eidolon Repraised's heart overlay, which lagged for players with a lot of health.",
                "EidolonRepraisedHeartLagFix");
        fix("mixin.fixes.embers",
                "Skip Embers world rendering while Immersive Portals is drawing a portal.",
                "EmbersMixin");
        fix("mixin.fixes.faktocraft_covers",
                "Wrap Faktocraft cover models on first request instead of baking every coverable state during the bake event.",
                "FaktocraftCoverBakeFix", "ModernFixCoverBakeFix");
        fix("mixin.fixes.immersive_convergence",
                "Bake Immersive Convergence split models from the machines it registered instead of walking every baked-model key.",
                "ImmersiveConvergenceBakeFix", "SplitModelHandlerAccessor");
        fix("mixin.fixes.lodestone",
                "Skip Lodestone's depth-buffer copy while Immersive Portals is drawing a portal.",
                "LodestoneMixin");
        fix("mixin.fixes.modernfix_bake_event_iteration",
                "Keep mods from forcing ModernFix to bake models outside their own namespace when they iterate the bake event registry.",
                "ModernFixBakeEventIterationFix", "ModernFixDynamicModelProviderAccessor");
        fix("mixin.fixes.modernfix_dynamic_model_drain_fix",
                "Drain ModernFix's pending blockstate model set in insertion order so model loading does not scan a growing hash table.",
                "ModernFixDynamicModelDrainFix");
        fix("mixin.fixes.multiplayer_rejoin_leak",
                "Clear client connection and session references on rejoin and level changes to stop memory leaks.",
                "ClientPacketListenerLeakFix", "ClientPacketListenerConnectionLeakFix", "ClientPacketListenerSessionDataLeakFix",
                "LocalPlayerConnectionLeakFix", "MinecraftClearLevelLeakFix", "MinecraftSetLevelLeakFix");
        fix("mixin.fixes.resource_reload_leak",
                "Release stale translation caches on language and resource reloads to stop old language maps leaking.",
                "LanguageReloadLeakFix", "TranslatableContentsLeakFix", "TranslatableContentsCacheAccessor");
        fix("mixin.fixes.soulslike_weaponry_load_check",
                "Cache Soulslike Weaponry's combat-mod check instead of repeating it every tick.",
                "MixinSoulslikeWeaponryLoadCheck");
        fix("mixin.fixes.spartan_shields_shield_patterns_atlas",
                "Correct Spartan Shields' shield_patterns atlas source so it reads only entity/shield textures instead of every entity texture.",
                "SpartanShieldsAtlasSourceFix");
        fix("mixin.fixes.voltex_covers",
                "Wrap Voltex cover and structural-glass models on first request instead of baking every coverable state.",
                "VoltexCoverBakeFix", "VoltexDynamicCoverFix");

        fix("mixin.fixes.ae2_crafting_tree_mixin_conflict",
                "Keep AE2 Crafting Optimizer's memoized crafting-tree lookup and Insane AE2 Addons' provider-card filter on the same query.",
                "Ae2CraftingTreeMixinConflictFix");
        fix("mixin.fixes.aco_ae2_version_audit_bypass",
                "Skip ACO's AE2 version requirement in its experimental audit so the compiled crafting graph stays enabled on patch releases such as AE2 15.4.12. The other audit checks still run; if the build is genuinely incompatible, ACO will fail when the feature is used.",
                "AcoAe2VersionAuditBypassFix");
        fix("mixin.fixes.extendedae_aco_exact_storage_compat",
                "Replace ACO 1.5.33's ExtendedAE Plus exact-storage mixins when ExtendedAE Plus 1.6.4 splits its Long/BigInteger amount maps; the replacement access bridge keeps ACO's exact BigInteger cell snapshots and mutations working.",
                "ExtendedAePlusBigIntegerCellAccessFix", "InfinityDataStorageExactTotalFix");
        fix("mixin.fixes.advanced_quantum_engineering_paused_host_snapshot",
                "Repair AQE 2.2.x paused opaque host snapshots when an ACO payload is paused (for example ACO was removed); the paused host currently builds an invalid overcommitted snapshot and crashes CPU loading. The opaque payload is preserved.",
                "AqePausedHostSnapshotFix");
        fix("mixin.fixes.aether_cme",
                "Stop a concurrent modification crash in the game-event listener list, seen with Aether, by iterating a snapshot.",
                "AetherCMEFixMixin");
        fix("mixin.fixes.apothic_attribute_number_format",
                "Format attribute values on Apothic Attributes' attribute screen with locale grouping, M/B/T suffixes below one quadrillion, and scientific notation at or above it.",
                "ApothicAttributeNumberFormatFix");
        register("mixin.fixes.atlas_downscaling", "mixin.fixes.atlas_downscaling", true,
                "Downscale sprites listed in config/mysticmonstrosityfixes_atlas_downscaling_entries.txt (one \"<sprite id regex>=<factor>\" entry per line) before they are stitched into an atlas. Entry file changes apply after a resource reload (F3+T); changing this option requires a game restart.",
                "AtlasDownscalingFix", "AtlasDownscalingNativeImageAccessor", "AtlasDownscalingReloadTimingFix");
        option("mixin.fixes.atlas_downscaling_logging", "mixin.fixes.atlas_downscaling_logging", false,
                "Log sprites downscaled by mixin.fixes.atlas_downscaling to logs/mmf_downscaled_atlas_sprites.txt with their resulting dimensions. Does nothing unless both mixin.fixes.atlas_downscaling and this option are enabled.");
        fix("mixin.fixes.atlasviewer_mip_levels",
                "Let AtlasViewer's mip sliders go to each sprite's one-pixel mip level, including odd-sized sprites, and generate the missing mip levels on demand, releasing them when the slider returns to 0, the atlas is switched, or the screen is closed.",
                "AtlasViewerMipLevelsFix", "AtlasTextureMipAccess", "AtlasMipUploadCleanupFix", "SpriteInfoScreenMipExportFix");
        fix("mixin.fixes.atmosphere_shader_compile",
                "Rename Let There Be Atmosphere's reserved GLSL word \"packed\" so its sky shaders compile under EmberLens.",
                "AtmosphereShaderCompileFix");
        fix("mixin.fixes.bielgg_broken_script_recursion",
                "Stop BielGG's Spells from stack-overflowing when a projectile's owner chain loops back on itself.",
                "BielGGBrokenScriptRecursionFix");
        fix("mixin.fixes.bielgg_weapon_spell_slots",
                "Stop BielGG's Spells from adding empty Iron's Spellbooks containers and NBT to every sword and axe.",
                "SwordSpellContainerFix", "AxeSpellContainerFix");
        fix("mixin.fixes.blood_magic_null_capability",
                "Fix a crash in Blood Magic when the player does not have a curios inventory.",
                "BloodMagicNullCapabilityFix");
        fix("mixin.fixes.capsule_preview_crash",
                "Fix a crash when Capsule renders a preview for improperly coded blocks.",
                "CapsulePreviewCrashFix");
        fix("mixin.fixes.dimension_id",
                "Give Applied Energistics 2's spatial storage dimension an Immersive Portals dimension id when the id record is missing it.",
                "DimensionIdManagementMixin", "DimensionIdRecordAccessor");
        fix("mixin.fixes.everlasting_abilities_attributes",
                "Apply Everlasting Abilities attribute bonuses permanently on level changes instead of reapplying them every tick.",
                "EverlastingAbilitiesAttributeFix");
        fix("mixin.fixes.experience_orb_single_award",
                "Spawn each XP award as one orb instead of splitting it into pieces that each scan nearby orbs for merging.",
                "ExperienceOrbSingleAwardFix", "ExperienceOrbValuePersistenceFix");
        fix("mixin.fixes.fastrecipes_condition_context",
                "Give FastRecipes' recipe manager the server's real recipe condition context so tag-based conditions are not silently dropped.",
                "FastRecipesConditionContextFix", "RecipeManagerConditionContextAccessor");
        fix("mixin.fixes.fastrecipes_polymorph_compat",
                "Route FastRecipes' recipe lookups through Polymorph so block entities and player crafting grids can use the selected recipe and the client receives every matching recipe.",
                "FastRecipesPolymorphCompatFix", "TransientCraftingContainerAccessor");
        fix("mixin.fixes.flib_potion_effect_race",
                "Fix a rare FLib potion merge crash when an effect disappears between its check and lookup.",
                "FLibPotionEffectRaceFix", "MobEffectInstanceAccessor");
        fix("mixin.fixes.full_mob_effect_amplifier",
                "Send full-int mob effect amplifiers in the effect update packet and use them when the client builds the effect instance, so levels above 127 no longer wrap negative. Both sides must use the same setting.",
                "ClientboundUpdateMobEffectPacketFullAmplifierFix", "ClientPacketListenerFullEffectAmplifierFix");
        fix("mixin.fixes.forge_registry_alias_sync",
                "Keep synced Forge registry names on their direct registration path so stale aliases cannot orphan ids and crash the client.",
                "ForgeRegistryAliasSyncFix");
        fix("mixin.fixes.ftb_library_null",
                "Fix a crash in FTB Library's JEI integration when the JEI runtime is null.",
                "FTBLibraryNullFix");
        fix("mixin.fixes.mahou_tsukai_shader_double_close",
                "Close Mahou Tsukai's clip-shader uniform buffers once instead of double-freeing them.",
                "MahouTsukaiShaderDoubleCloseFix");
        fix("mixin.fixes.gui_scale_click",
                "Keep a left click in any menu when GUI Scale Additions rebuilds the open screen on mouse release.",
                "GuiScaleClickFix");
        fix("mixin.fixes.huge_mushroom",
                "Replace Dynamic Trees Plus's broken huge-mushroom rot with normal Dynamic Trees rot behavior.",
                "HugeMushroomSpeciesMixin", "SpeciesAccessor");
        fix("mixin.fixes.illage_projectile_category",
                "Tag Illage and Spillage's thrown axe and bone as miscellaneous to stop the \"has no attributes\" error.",
                "EntityTypeCategoryAccessor");
        fix("mixin.fixes.inventory_pets_nbt_null",
                "Fix a crash in Inventory Pets when the item's NBT is missing a required key.",
                "InventoryPetsNbtNullFix");
        fix("mixin.fixes.item_collectors_instant_pickup",
                "Insert new drops into Item Collectors before they become item entities, avoiding loot explosions.",
                "ItemCollectorBlockEntityMixin");
        fix("mixin.fixes.jade_enum_plugin_load",
                "Let Jade load enum plugins, including Applied Quantum Inscriber's Jade plugin.",
                "JadeEnumPluginLoadFix");
        fix("mixin.fixes.jitl_client_essence_null",
                "Fix a crash in Journey Into the Light when the player's essence capability is missing.",
                "JITLClientEssenceNullFix");
        fix("mixin.fixes.jitl_death_crash",
                "Fix a crash in Journey Into the Light when the player's stats capability is missing.",
                "JITLDeathCrashFix");
        fix("mixin.fixes.jitl_offline_crash",
                "Fix a crash in Journey Into the Light when checking for updates while offline.",
                "JITLOfflineCrashFix");
        fix("mixin.fixes.krypton_reforged_var_int",
                "Raise Krypton Reforged's VarInt byte limit to the size configured by Packet Fixer.",
                "KryptonReforgedVarIntFix");
        fix("mixin.fixes.minecontrollers_skull_register",
                "Stop Minecontrollers from logging a skull-registration error for every unrelated field on the skull block entity.",
                "MinecontrollersSkullRegisterFix");
        fix("mixin.fixes.moving_elevators_cabin_cl",
                "Point Moving Elevators' cabin level at the real world so Colorful Lighting can light it.",
                "MovingElevatorsCabinCLCompat");
        fix("mixin.fixes.remove_all_effects_cme",
                "Remove effects from a snapshot so effect listeners can add or remove effects without a concurrent modification crash.",
                "LivingEntityRemoveAllEffectsCMEFix");
        fix("mixin.fixes.placeable_bamboo_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to bamboo.",
                "PlaceableBambooSoilFix");
        fix("mixin.fixes.placeable_big_dripleaf_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to big dripleaf.",
                "PlaceableBigDripleafSoilFix");
        fix("mixin.fixes.placeable_big_dripleaf_stem_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to big dripleaf stems.",
                "PlaceableBigDripleafStemSoilFix");
        fix("mixin.fixes.placeable_bush_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to bushes.",
                "PlaceableBushSoilFix");
        fix("mixin.fixes.placeable_dead_bush_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to dead bushes.",
                "PlaceableDeadBushSoilFix");
        fix("mixin.fixes.placeable_sapling_soil",
                "Keep Placeable Plants from crashing on startup and apply its soil rule to saplings.",
                "PlaceableSaplingSoilFix");
        fix("mixin.fixes.sol_carrot_foods_null",
                "Fix a crash in Spice of Life: Carrot Edition when the food blacklist list is null.",
                "SoLCarrotFoodsNullFix");
        fix("mixin.fixes.solar_craft_shader_load_crash",
                "Fix a crash in Solar Craft when loading shaders with zero window dimensions.",
                "SolarCraftShaderLoadCrashFix");
        fix("mixin.fixes.vram_debug_line",
                "Add a VRAM usage line below the heap allocation lines on the F3 debug screen. The line is omitted when no VRAM source works.",
                "DebugScreenVramLineFix");

        option("mixin.experimental", "mixin.experimental.enabled", false,
                "Master switch for experimental mixins.");
        option("mixin.experimental.bootcache", "mixin.experimental.bootcache", true,
                "Cache boot-time reads of text and JSON from mod jars and resource packs.");
        option("mixin.experimental.blockatlas", "mixin.experimental.blockatlas", true,
                "Split the block texture atlas into pages and keep block, item, and GUI shaders sampling the right texture.");

        option("mixin.tweaks", "mixin.tweaks.enabled", false,
                "Master switch for tweaks.");
        option("mixin.tweaks.alltheimbaium_disable_supply_points", "mixin.tweaks.alltheimbaium_disable_supply_points", true,
                "Disable Alltheimbaium supply point gains and always report 0 supply points.",
                "AllTheImbaiumSupplyDataTweak", "AllTheImbaiumSupplyEventsTweak");
        option("mixin.tweaks.avaritia_catalyst_cookie_consumes", "mixin.tweaks.avaritia_catalyst_cookie_consumes", true,
                "Consume one infinity catalyst cookie when eaten instead of leaving the stack untouched.",
                "AvaritiaCatalystCookieConsumeTweak");
        option("mixin.tweaks.everlasting_abilities_uncap", "mixin.tweaks.everlasting_abilities_uncap", true,
                "Let Everlasting Abilities exceed the max level of abilities whose max level is above one, except Resistance.",
                "EverlastingAbilitiesUncapTweak", "EverlastingAbilitiesCommandUncapTweak",
                "EverlastingAbilitiesAttributeTweak", "EverlastingAbilitiesTooltipTweak");
        option("mixin.tweaks.disable_enchantmentlevelbreak_anvil_mixin",
                "mixin.tweaks.disable_enchantmentlevelbreak_anvil_mixin", true,
                "Disable Enchantment Level Break's anvil mixin and use vanilla anvil combining.");
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
        String option = optionFor(mixinClassName);
        return option == null || isEnabled(option);
    }

    /**
     * @param key a registered option key such as {@code mixin.tweaks.enabled}
     * @return false when that option, or any of its parent options, is turned off
     */
    public static boolean isEnabled(String key) {
        load();
        if (key == null) {
            return true;
        }
        String[] parts = key.split("\\.");
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

    private static void fix(String key, String summary, String... mixinClassNames) {
        register(key, key, true, summary + " Changes require a game restart.", mixinClassNames);
    }

    private static void option(
            String key, String tomlPath, boolean defaultValue, String summary, String... mixinClassNames
    ) {
        register(key, tomlPath, defaultValue, summary + " Changes require a game restart.", mixinClassNames);
    }

    private static void register(
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
