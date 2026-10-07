# Mystic's Fixes
Some fixes originally for Mystic's Monstrosity modpack

## What?
Basically just a collection of hacky fixes or small changes to other mods, either for compatibility or to fix crashes that we decided to release to the public now.

## Current Fixes

### Crash fixes

- Fix crash in Blood Magic when the player does not have a curios inventory [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/BloodMagicNullCapabilityFix.java)]
- Fix a crash in FTB Library's JEI integration when the JEI runtime is null [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/FTBLibraryNullFix.java)]
- Fix a crash in Inventory Pets when the item's NBT is missing a required key [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/InventoryPetsNbtNullFix.java)]
- Fix crashes in JITL when the player's essence or stats capabilities are missing [[one](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLClientEssenceNullFix.java), [two](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLDeathCrashFix.java)]
- Fix a crash in JITL when checking for updates while offline [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLOfflineCrashFix.java)]
- Fix a ConcurrentModificationException in the vanilla game event listener registry when a listener adds or removes listeners mid-iteration (seen with Aether) [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/AetherCMEFixMixin.java)]
- Fix a crash in Solar Craft when loading shaders with zero window dimensions [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/SolarCraftShaderLoadCrashFix.java)]
- Fix a crash in SoL Carrot when the food blacklist list is null [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/SoLCarrotFoodsNullFix.java)]
- Fix a crash when Capsule mod tries to render a preview for improperly coded blocks [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/CapsulePreviewCrashFix.java)]

### Compatibility fixes

- Make Krypton FNP's VarInt decoder respect Packet Fixer's configured VarInt size, so oversized packets do not get dropped [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/KryptonFnpVarIntFix.java)]
- Make Moving Elevators' fake cabin level a Colorful Lighting wrapper level, so CL falls back to the real world's lighting instead of seeing no light data [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/MovingElevatorsCabinCLCompat.java)]
- Make Embers and Lodestone skip their world render passes while Immersive Portals is rendering a portal [[Embers](src/main/java/com/name/mysticmonstrosityfixes/mixin/EmbersMixin.java), [Lodestone](src/main/java/com/name/mysticmonstrosityfixes/mixin/LodestoneMixin.java)]
- Give AE2's spatial storage dimension an ID from q_misc_util's dimension record, which saves can wipe [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/DimensionIdManagementMixin.java)] ([accessor](src/main/java/com/name/mysticmonstrosityfixes/mixin/DimensionIdRecordAccessor.java))
- Replace Dynamic Trees Plus' broken `HugeMushroomSpecies.rot` override with Dynamic Trees' own `Species.rot` behaviour [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/HugeMushroomSpeciesMixin.java)] ([accessor](src/main/java/com/name/mysticmonstrosityfixes/mixin/SpeciesAccessor.java))
- Disable Capsule's recall enchantment, it causes lag [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/CapsuleDisableRecallEnchantment.java)]

### Performance fixes

- "Fix" lag when Eidolon tries to render HP overlay for users with lots of health (fully disabled the renderer) [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/EidolonRepraisedHeartLagFix.java)]
- Fix Soulslike weaponry lag (it checks if a mod is loaded every tick, we cache the result) [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/MixinSoulslikeWeaponryLoadCheck.java)]
- Cut Botany Pots' per-tick cost: coalesce the duplicate block entity syncs caused by auto-harvesting and hoppers, skip exporting when nothing is stored, and skip ticking fully idle pots [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/BotanyPotLagFix.java)]
- Serve Botany Pots' soil and crop recipe lookups from a cache instead of rescanning the whole recipe list on every call [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/BotanyPotLookupLagFix.java)]

### Internal mixins

Accessors that exist only to support the fixes above, nothing user-facing on their own.

- Expose Forge's ingredient invalidation counter so ingredient-derived caches can be dropped on tag changes [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/IngredientAccessor.java)]
- Expose the recipe manager's recipe map so caches can detect a recipe reload [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/RecipeManagerAccessor.java)]