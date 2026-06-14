# Mystics Monstrosity Fixes
Some fixes for Mystic's Monstrosity modpack

## What?
Basically just a collection of hacky fixes or small changes to other mods, either for compatibility or to fix crashes.

## Current Fixes

- Fix crash in Blood Magic when the player does not have a curios inventory [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/BloodMagicNullCapabilityFix.java)]
- Fix a crash in FTB Library's JEI integration when the JEI runtime is null [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/FTBLibraryNullFix.java)]
- Fix a crash in Inventory Pets when the item's NBT is missing a required key [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/InventoryPetsNbtNullFix.java)]
- Fix crashes in JITL when the player's essence or stats capabilities are missing [[one](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLClientEssenceNullFix.java), [two](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLDeathCrashFix.java)]
- Fix a crash in JITL when checking for updates while offline [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/JITLOfflineCrashFix.java)]
- Fix an issue in Krypton Reforged's VarInt decoding [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/KryptonReforgedVarIntFix.java)]
- Fix a crash in Solar Craft when loading shaders with zero window dimensions [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/SolarCraftShaderLoadCrashFix.java)]
- Fix a crash in SoL Carrot when the food blacklist list is null [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/SolCarrotFoodsNullFix.java)]
- Fix white dot rendering artifacts by adjusting UV coordinates in model baking [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/WhiteDotFix.java)]
- Fix a crash when Capsule mod tries to render a preview for improperly coded blocks [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/CapsulePreviewCrashFix.java)]
- "Fix" lag when Eidolon tries to render HP overlay for users with lots of health (fully disabled the renderer) [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/EidolonRepraisedHeartLagFix.java)]
- Fix Soulslike weaponry lag (it checks if a mod is loaded every tick, we cache the result) [[code](src/main/java/com/name/mysticmonstrosityfixes/mixin/MixinSoulslikeWeaponryLoadCheck.java)]
