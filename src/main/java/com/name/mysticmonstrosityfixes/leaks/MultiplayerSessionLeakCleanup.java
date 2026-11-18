package com.name.mysticmonstrosityfixes.leaks;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * Best-effort cleanup of client session state that optional mods store in static fields.
 * <p>
 * The heap dump taken after three multiplayer reconnects showed every old session still reachable
 * from static mod state:
 * <ul>
 * <li>Blue Skies keeps a {@code DungeonAmbientSoundHandler} in {@code SkiesClientEvents}. The handler
 * captures the {@code LocalPlayer} that existed when the class initialized, and only ever fills a
 * null player, so it never follows a reconnect.</li>
 * <li>Tetratic Combat keeps a static {@code Map<Player, ItemStack>} on {@code Player} and only ever
 * adds entries, never removing a player that left the world.</li>
 * <li>TinyMultiBlockLib creates a {@code FakeClientLevel} for every {@code ClientLevel} and keeps the
 * newest one in the static {@code FakeClientLevel.INSTANCE}. Each fake level retains its real
 * {@code ClientLevel} and {@code ClientPacketListener}, and older fake levels are captured by
 * FastChunkGen's {@code CheckedThreadLocalRandom.owner} supplier and stored forever in RSLS's
 * static {@code WorldRandomHolder.randoms} set.</li>
 * <li>Cyclic's packager keeps five static memoization maps keyed by item or crafting recipe. The
 * item-keyed maps are only rebuilt when empty, and the recipe-keyed maps are never cleared, so
 * recipes from previous sessions accumulate there. The second heap dump showed
 * {@code UtilPackager} retaining about 2.5 GB through 101k old shaped recipes.</li>
 * <li>AE2WTLib's {@code CraftingTerminalHandler.players} is a {@code WeakHashMap} whose values hold
 * a strong reference back to the key player, so the weak key never expires.</li>
 * <li>GeckoLib's static {@code GeckoLibNetwork.SYNCED_ANIMATABLES} map keeps entity and block-entity
 * animatables after their level is gone. In the second dump a stale Yeti entity kept an entire old
 * {@code ClientLevel} and listener alive.</li>
 * <li>Smart Particles adds a {@code spHeapParticles} array to {@code ParticleEngine}. Vanilla
 * {@code setLevel} clears its own particle collections but not that array, so a stale particle can
 * keep the previous {@code ClientLevel} alive into the next session.</li>
 * <li>Opposing Force's static {@code ClientProxy.ENTITY_SOUND_INSTANCE_MAP} keeps tickable entity
 * sound instances forever; each sound holds its entity, which holds the old level and listener.</li>
 * <li>Sophisticated Core's {@code LinkerCraftingDiagnostics.lastRequestedMenu} is a static menu that
 * holds the player and therefore the closed connection.</li>
 * </ul>
 * The classes live in optional mods, so they are accessed reflectively and every lookup failure is
 * ignored. The listener mixin clears the large recipe graph on every connection close, and this
 * cleanup runs again after {@code Minecraft.clearLevel} has nulled the level and player, so the old
 * session cannot be re-acquired by a tick during the disconnect transition.
 */
public final class MultiplayerSessionLeakCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BLUE_SKIES_MOD_ID = "blue_skies";
    private static final String BLUE_SKIES_EVENTS = "com.legacy.blue_skies.client.events.SkiesClientEvents";
    private static final String BLUE_SKIES_AMBIENT_HANDLER = "com.legacy.blue_skies.client.audio.ambient.DungeonAmbientSoundHandler";
    private static final String TETRATIC_PLAYER_MAP = "playerItemStackMap";
    private static final String RSLS_WORLD_RANDOM_HOLDER = "com.ishland.fabric.rsls.common.WorldRandomHolder";
    private static final String TINY_MULTIBLOCK_FAKE_LEVEL = "net.nikdo53.tinymultiblocklib.client.FakeClientLevel";
    private static final String CYCLIC_UTIL_PACKAGER = "com.lothrazar.cyclic.block.packager.UtilPackager";
    private static final String AE2WTLIB_CRAFTING_HANDLER = "de.mari_023.ae2wtlib.wct.CraftingTerminalHandler";
    private static final String GECKOLIB_NETWORK = "software.bernie.geckolib.network.GeckoLibNetwork";
    private static final String GECKOLIB_SYNCED_ANIMATABLES = "SYNCED_ANIMATABLES";
    private static final String SMART_PARTICLES_HEAP = "spHeapParticles";
    private static final String SMART_PARTICLES_KEEP = "spKeep";
    private static final String OPPOSING_FORCE_CLIENT_PROXY = "com.unusualmodding.opposing_force.utils.ClientProxy";
    private static final String OPPOSING_FORCE_SOUND_MAP = "ENTITY_SOUND_INSTANCE_MAP";
    private static final String SOPHISTICATED_LINKER_DIAGNOSTICS = "net.p3pp3rf1y.sophisticatedcore.client.LinkerCraftingDiagnostics";
    private static final String SOPHISTICATED_REQUESTED_MENU = "lastRequestedMenu";
    private static final String COMBATNOUVEAU_PROJECTILE_UTIL = "net.minecraft.world.entity.projectile.ProjectileUtil";
    private static final String COMBATNOUVEAU_PICKED_ENTITY = "combatnouveau$pickedEntity";
    private static final String RYOAMICLIGHTS_PLAYER_LAST_WORLD = "ryoamiclights$lastWorld";
    private static final String COO_ZETA_WRAPPER_MEMO = "com.misanthropy.collections_of_optimizations.core.ZetaWrapperMemo";
    private static final String COO_IAF_ENTITY_DATA = "coo$iafEntityData";
    private static final String IAF_ENTITY_DATA_PROVIDER = "com.github.alexthe666.iceandfire.entity.props.EntityDataProvider";
    private static final String IAF_CLIENT_CACHE = "CLIENT_CACHE";
    private static final String IMMERSIVE_HOTBAR_CAMERA_PLAYER = "immersiveHotbar$cameraPlayer";
    private static final String[] IMMERSIVE_HOTBAR_CONTROLLERS = {
            "derp.immersivehotbar.animation.hotbar.OffhandTransferController",
            "derp.immersivehotbar.animation.hotbar.HotbarDropController"
    };
    private static final String IMMERSIVE_HOTBAR_CONTROLLER_OWNER = "owner";
    private static final String IMMERSIVE_HOTBAR_CONTROLLER_INSTANCE = "HUD";
    private static final String NUCLEARCRAFT_TOOLTIP_HANDLER = "igentuman.nc.handler.event.client.TooltipHandler";
    private static final String NUCLEARCRAFT_PROCESSED_EVENT = "processedEvent";
    private static final String ITEM_TOOLTIP_EVENT = "net.minecraftforge.event.entity.player.ItemTooltipEvent";
    private static final String MOWZIES_GECKO_FIRST_PERSON_RENDERER = "com.bobmowzie.mowziesmobs.client.render.entity.player.GeckoFirstPersonRenderer";
    private static final String MOWZIES_GECKO_FIRST_PERSON = "GECKO_PLAYER_FIRST_PERSON";
    private static final String MOWZIES_GECKO_FIRST_PERSON_TYPE = "com.bobmowzie.mowziesmobs.client.render.entity.player.GeckoPlayer$GeckoPlayerFirstPerson";
    private static final String IRONS_ADJUSTMENT_MODIFIER = "io.redspace.ironsspellbooks.render.animation.IronsAdjustmentModifier";
    private static final String IRONS_ADJUSTMENT_INSTANCE = "INSTANCE";
    private static final String IRONS_ADJUSTMENT_ANIM = "anim";
    private static final String PLAYER_ANIMATION = "dev.kosmx.playerAnim.api.layered.IAnimation";
    private static final String WOOT_GUIDE_LIVING_ENTITIES = "wootrevived.woot.events.client.GuideCacheLivingEntities";
    private static final String WOOT_LIVING_ENTITIES = "livingEntities";
    private static final String FORESTRY_BREEDING_TRACKER_MANAGER = "forestry.core.genetics.root.BreedingTrackerManager";
    private static final String FORESTRY_BREEDING_HANDLER = "BREEDING_HANDLER";
    private static final String FORESTRY_TRACKER_MAP = "trackerByUID";
    private static final String ARS_CAMERA_CONTROLLER = "com.hollingsworth.arsnouveau.common.camera.CameraController";
    private static final String ARS_CAMERA_STORAGE = "cameraStorage";
    private static final String HEXCASTING_BRAINSWEEPEE = "at.petrak.hexcasting.common.recipe.ingredient.brainsweep.BrainsweepeeIngredient";
    private static final String HEXCASTING_EXAMPLE_ENTITIES = "cachedExampleEntity";
    private static final String EUPHORIA_SHADER_REFRESH = "com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh";
    private static final String EUPHORIA_LAST_LEVEL = "lastLevel";
    private static final String PNEUMATICCRAFT_AREA_RENDER_MANAGER = "me.desht.pneumaticcraft.client.render.area.AreaRenderManager";
    private static final String PNEUMATICCRAFT_LEVEL = "level";
    private static final String MEKANISM = "mekanism.common.Mekanism";
    private static final String MEKANISM_PLAYER_STATE = "playerState";
    private static final String MEKANISM_PLAYER_STATE_WORLD = "world";
    private static final String CAPSULE_PREVIEW_HANDLER = "capsule.client.CapsulePreviewHandler";
    private static final String CAPSULE_DISPATCHER = "dispatcher";
    private static final String SECURITYCRAFT_CAMERA_VIEW = "net.geforcemods.securitycraft.entity.camera.CameraViewAreaExtension";
    private static final String SECURITYCRAFT_DISPATCHER = "chunkRenderDispatcher";
    private static final String CHUNK_RENDER_DISPATCHER = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher";
    private static final String[][] STATIC_LEVEL_CACHES = {
            {"com.github.alexmodguy.alexcaves.client.ClientProxy", "lastBossLevel"},
            {"com.natamus.collective_common_forge.events.CollectiveClientEvents", "lastPickerCacheLevel"},
            {"dev.norevy.weatherrefind.WeatherEngine", "lastLevel"},
            {"com.github.dumann089.theatricalextralights.client.ConfettiBurstClient", "batchedLevel"},
            {"com.lion.graveyard.entities.animation.AnimationPreviewClient", "lastWorld"},
            {"dev.cataclysmnouveau.client.VortexDiagnostics", "world"},
            {"jp.aquafactory.apprenticecodex.event.client.PsychicDriveGauntletsClientEvents", "presentationLevel"},
            {"jp.aquafactory.apprenticecodex.event.client.PsychicDriveGauntletsClientState", "level"},
            {"jp.aquafactory.apprenticecodex.event.client.PsychicDriveGauntletsEffects", "level"},
            {"jp.aquafactory.apprenticecodex.event.client.PsychicDriveGauntletsInputEvents", "inputLevel"},
            {"jp.aquafactory.apprenticecodex.event.client.PsychicDriveGauntletsUseInput", "level"},
            {"jp.aquafactory.apprenticecodex.event.client.SpellrifleSprintState", "level"},
            {"jp.aquafactory.apprenticecodex.item.spellreaperscythe.ScytheRecallRenderEvent", "world"},
            {"jp.aquafactory.apprenticecodex.renderer.HoverrideBroomImpulseRenderEvent", "lastRushEffectLevel"},
            {"jp.aquafactory.apprenticecodex.spell.deepsensor.DeepSensorClientDisplay", "trackedLevel"},
            {"jp.aquafactory.apprenticecodex.spell.otherworldlens.OtherworldLensClientController", "activationLevel"}
    };
    private static final String[] CYCLIC_PACKAGER_CACHES = {
            "fourItemRecipeCache",
            "nineItemRecipeCache",
            "ingredientsInRecipeCache",
            "itemValidCache",
            "recipeValidCache"
    };

    private static Field blueSkiesHandlerField;
    private static Field blueSkiesPlayerField;
    private static boolean blueSkiesUnavailable;
    private static Field tetraticPlayerMapField;
    private static boolean tetraticUnavailable;
    private static Field worldRandomLockField;
    private static Field worldRandomSetField;
    private static boolean worldRandomUnavailable;
    private static Field fakeClientLevelInstanceField;
    private static boolean fakeClientLevelUnavailable;
    private static Field[] cyclicPackagerCacheFields;
    private static boolean cyclicPackagerUnavailable;
    private static Field ae2PlayersField;
    private static boolean ae2Unavailable;
    private static Field geckoLibSyncedAnimatableField;
    private static boolean geckoLibUnavailable;
    private static Field smartParticlesHeapField;
    private static Field smartParticlesKeepField;
    private static boolean smartParticlesUnavailable;
    private static Field opposingForceSoundMapField;
    private static boolean opposingForceUnavailable;
    private static Field sophisticatedRequestedMenuField;
    private static boolean sophisticatedUnavailable;
    private static Field combatNouveauPickedEntityField;
    private static boolean combatNouveauUnavailable;
    private static Field ryoamicLightsLastWorldField;
    private static boolean ryoamicLightsUnavailable;
    private static Method cooZetaForgetEventsMethod;
    private static boolean cooZetaMemoUnavailable;
    private static Field cooIafEntityDataField;
    private static boolean cooIafEntityDataUnavailable;
    private static Field iafClientCacheField;
    private static boolean iafClientCacheUnavailable;
    private static Field immersiveHotbarCameraPlayerField;
    private static boolean immersiveHotbarUnavailable;
    private static final Field[] immersiveHotbarControllerOwnerFields = new Field[IMMERSIVE_HOTBAR_CONTROLLERS.length];
    private static final Object[] immersiveHotbarControllerInstances = new Object[IMMERSIVE_HOTBAR_CONTROLLERS.length];
    private static final boolean[] immersiveHotbarControllerUnavailable = new boolean[IMMERSIVE_HOTBAR_CONTROLLERS.length];
    private static Field nuclearCraftTooltipEventField;
    private static boolean nuclearCraftTooltipUnavailable;
    private static Field mowziesGeckoFirstPersonField;
    private static boolean mowziesGeckoUnavailable;
    private static Field ironsAdjustmentAnimField;
    private static Object ironsAdjustmentInstance;
    private static boolean ironsAdjustmentUnavailable;
    private static Field wootLivingEntitiesField;
    private static boolean wootLivingEntitiesUnavailable;
    private static Field forestryTrackerMapField;
    private static Object forestryBreedingHandler;
    private static boolean forestryUnavailable;
    private static Field arsCameraStorageField;
    private static boolean arsCameraStorageUnavailable;
    private static Field hexcastingExampleEntitiesField;
    private static boolean hexcastingUnavailable;
    private static Field euphoriaLastLevelField;
    private static boolean euphoriaUnavailable;
    private static Field pneumaticAreaLevelField;
    private static Object pneumaticAreaRenderManager;
    private static boolean pneumaticUnavailable;
    private static Field mekanismPlayerStateWorldField;
    private static Object mekanismPlayerState;
    private static boolean mekanismUnavailable;
    private static Field capsuleDispatcherField;
    private static boolean capsuleUnavailable;
    private static Field securityCraftDispatcherField;
    private static boolean securityCraftUnavailable;
    private static Method securityCraftClearMethod;
    private static boolean securityCraftClearUnavailable;
    private static Field[] staticLevelCacheFields;
    private static boolean staticLevelCachesResolved;

    private MultiplayerSessionLeakCleanup() {
    }

    /**
     * Best-effort cleanup on connection close. The level and player may still be set here and ticks
     * can briefly restore these caches, so {@link #onLevelCleared()} runs the final cleanup after
     * {@code Minecraft.clearLevel} has nulled both.
     */
    public static void onConnectionClosed() {

        runSafely("Blue Skies ambient handler", MultiplayerSessionLeakCleanup::clearBlueSkiesAmbientHandler);
        runSafely("Tetratic Combat player cache", MultiplayerSessionLeakCleanup::clearTetraticPlayerCache);
    }

    /**
     * Runs once per level teardown, after {@code Minecraft.clearLevel} has nulled the level and player.
     * This is the authoritative pass: it releases the player caches again and also breaks the
     * TinyMultiBlockLib / RSLS fake-level chain, the Cyclic/AE2WTLib/GeckoLib session caches, the
     * Smart Particles heap, Opposing Force's entity sound map, and Sophisticated Core's requested
     * linker menu, all of which can otherwise carry an old level into the next session.
     */
    public static void onLevelCleared() {

        runSafely("Blue Skies ambient handler", MultiplayerSessionLeakCleanup::clearBlueSkiesAmbientHandler);
        runSafely("Tetratic Combat player cache", MultiplayerSessionLeakCleanup::clearTetraticPlayerCache);
        runSafely("RSLS world random registry", MultiplayerSessionLeakCleanup::clearWorldRandomHolder);
        runSafely("TinyMultiBlockLib fake level", MultiplayerSessionLeakCleanup::clearFakeClientLevel);
        runSafely("Cyclic packager caches", MultiplayerSessionLeakCleanup::clearCyclicPackagerCaches);
        runSafely("AE2WTLib crafting handlers", MultiplayerSessionLeakCleanup::clearAe2WtlibHandlers);
        runSafely("GeckoLib animatable registry", MultiplayerSessionLeakCleanup::clearGeckoLibAnimatableRegistry);
        runSafely("Smart Particles heap", MultiplayerSessionLeakCleanup::clearSmartParticles);
        runSafely("Opposing Force entity sounds", MultiplayerSessionLeakCleanup::clearOpposingForceSoundCache);
        runSafely("Sophisticated Core linker menu", MultiplayerSessionLeakCleanup::clearSophisticatedLinkerMenu);
        runSafely("CombatNouveau picked entity", MultiplayerSessionLeakCleanup::clearCombatNouveauPickedEntity);
        runSafely("CoO memoized event wrappers", MultiplayerSessionLeakCleanup::clearCooZetaWrapperMemos);
        runSafely("CoO Ice and Fire entity data", MultiplayerSessionLeakCleanup::clearCooIceAndFireEntityData);
        runSafely("Immersive Hotbar camera player", MultiplayerSessionLeakCleanup::clearImmersiveHotbarCameraPlayer);
        runSafely("Immersive Hotbar animation owners", MultiplayerSessionLeakCleanup::clearImmersiveHotbarControllerOwners);
        runSafely("NuclearCraft tooltip event", MultiplayerSessionLeakCleanup::clearNuclearCraftTooltipEvent);
        runSafely("Mowzie's Gecko first-person player", MultiplayerSessionLeakCleanup::clearMowziesGeckoFirstPerson);
        runSafely("Iron's Spellbooks animation modifier", MultiplayerSessionLeakCleanup::clearIronsAdjustmentModifier);
        runSafely("Woot guide living entities", MultiplayerSessionLeakCleanup::clearWootLivingEntities);
        runSafely("Forestry breeding trackers", MultiplayerSessionLeakCleanup::clearForestryBreedingTrackers);
        runSafely("HUD overlay level caches", MultiplayerSessionLeakCleanup::clearHudOverlayLevelCaches);
        runSafely("Ars Nouveau camera storage", MultiplayerSessionLeakCleanup::clearArsCameraStorage);
        runSafely("Hexcasting example entities", MultiplayerSessionLeakCleanup::clearHexcastingExampleEntities);
        runSafely("Euphoria Patcher dimension level", MultiplayerSessionLeakCleanup::clearEuphoriaShaderLevel);
        runSafely("PneumaticCraft area render level", MultiplayerSessionLeakCleanup::clearPneumaticCraftAreaLevel);
        runSafely("Mekanism player state world", MultiplayerSessionLeakCleanup::clearMekanismPlayerStateWorld);
        runSafely("Capsule preview dispatcher", MultiplayerSessionLeakCleanup::clearCapsulePreviewDispatcher);
        runSafely("SecurityCraft camera dispatcher", MultiplayerSessionLeakCleanup::clearSecurityCraftCameraDispatcher);
        runSafely("static level caches", () -> clearStaleStaticLevelCaches(null));
    }

    public static void onLevelChanged(Level oldLevel, ClientLevel newLevel) {

        if (newLevel == null) {
            return;
        }
        runSafely("Blue Skies ambient handler", MultiplayerSessionLeakCleanup::clearBlueSkiesAmbientHandler);
        runSafely("Tetratic Combat player cache", MultiplayerSessionLeakCleanup::clearTetraticPlayerCache);
        runSafely("AE2WTLib crafting handlers", MultiplayerSessionLeakCleanup::clearAe2WtlibHandlers);
        runSafely("GeckoLib animatable registry", MultiplayerSessionLeakCleanup::clearGeckoLibAnimatableRegistry);
        runSafely("Smart Particles heap", MultiplayerSessionLeakCleanup::clearSmartParticlesFields);
        runSafely("Opposing Force entity sounds", MultiplayerSessionLeakCleanup::clearOpposingForceSoundCache);
        runSafely("Sophisticated Core linker menu", MultiplayerSessionLeakCleanup::clearSophisticatedLinkerMenu);
        runSafely("CombatNouveau picked entity", MultiplayerSessionLeakCleanup::clearCombatNouveauPickedEntity);
        runSafely("CoO memoized event wrappers", MultiplayerSessionLeakCleanup::clearCooZetaWrapperMemos);
        runSafely("CoO Ice and Fire entity data", MultiplayerSessionLeakCleanup::clearCooIceAndFireEntityData);
        runSafely("Immersive Hotbar camera player", MultiplayerSessionLeakCleanup::clearImmersiveHotbarCameraPlayer);
        runSafely("NuclearCraft tooltip event", MultiplayerSessionLeakCleanup::clearNuclearCraftTooltipEvent);
        runSafely("Woot guide living entities", MultiplayerSessionLeakCleanup::clearWootLivingEntities);
        runSafely("Hexcasting example entities", MultiplayerSessionLeakCleanup::clearHexcastingExampleEntities);
        runSafely("static level caches", () -> clearStaleStaticLevelCaches(newLevel));
        runSafely("RSLS world random registry", () -> pruneWorldRandomHolder(newLevel));
        if (oldLevel != null && oldLevel != newLevel) {
            runSafely("RyoamicLights player worlds", () -> clearRyoamicLightsLastWorld(oldLevel, newLevel));
        }
    }

    /**
     * Runs one optional cleanup and never lets a missing mod, a changed field, or a broken static
     * initializer abort the remaining cleanups. A missing targeted mod is expected to be a no-op.
     */
    private static void runSafely(String name, Runnable cleanup) {

        try {
            cleanup.run();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable throwable) {
            LOGGER.error("Client session cleanup step '{}' failed", name, throwable);
        }
    }

    private static void clearBlueSkiesAmbientHandler() {

        final Field handlerField = blueSkiesHandlerField();
        if (handlerField == null) {
            return;
        }

        try {
            final Object handler = handlerField.get(null);
            if (handler == null) {
                return;
            }
            if (blueSkiesPlayerField == null) {
                final Field player = handler.getClass().getDeclaredField("player");
                if (!Player.class.isAssignableFrom(player.getType())) {
                    return;
                }
                player.setAccessible(true);
                blueSkiesPlayerField = player;
            }
            blueSkiesPlayerField.set(handler, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear the Blue Skies ambient handler's player", exception);
        }
    }

    private static Field blueSkiesHandlerField() {

        if (blueSkiesHandlerField != null) {
            return blueSkiesHandlerField;
        }
        if (blueSkiesUnavailable) {
            return null;
        }
        if (!ModList.get().isLoaded(BLUE_SKIES_MOD_ID)) {
            blueSkiesUnavailable = true;
            return null;
        }
        try {
            final Class<?> events = Class.forName(BLUE_SKIES_EVENTS, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field handler = events.getDeclaredField("dungeonAmbientSoundHandler");
            if (!Modifier.isStatic(handler.getModifiers()) || !BLUE_SKIES_AMBIENT_HANDLER.equals(handler.getType().getName())) {
                blueSkiesUnavailable = true;
                return null;
            }
            handler.setAccessible(true);
            blueSkiesHandlerField = handler;
            return handler;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // The mod is present but this revision does not have the field; stop looking on later logouts.
            blueSkiesUnavailable = true;
            LOGGER.debug("Blue Skies ambient handler not found; skipping its logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            // Retry on the next logout instead of disabling the cleanup for the rest of the process.
            LOGGER.debug("Could not resolve the Blue Skies ambient handler; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearTetraticPlayerCache() {

        final Field playerMapField = tetraticPlayerMapField();
        if (playerMapField == null) {
            return;
        }

        try {
            if (playerMapField.get(null) instanceof Map<?, ?> playerMap) {
                playerMap.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Tetratic Combat's player cache", exception);
            tetraticPlayerMapField = null;
        }
    }

    private static Field tetraticPlayerMapField() {

        if (tetraticPlayerMapField != null) {
            return tetraticPlayerMapField;
        }
        if (tetraticUnavailable) {
            return null;
        }
        try {
            final Field playerMap = Player.class.getDeclaredField(TETRATIC_PLAYER_MAP);
            if (Modifier.isStatic(playerMap.getModifiers()) && Map.class.isAssignableFrom(playerMap.getType())) {
                playerMap.setAccessible(true);
                tetraticPlayerMapField = playerMap;
                return playerMap;
            }
            tetraticUnavailable = true;
            return null;
        } catch (NoSuchFieldException exception) {
            // Tetratic Combat is optional; its field only exists when its mixin applied.
            tetraticUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Tetratic Combat's player cache; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearWorldRandomHolder() {

        final Field lockField = worldRandomLockField();
        final Field setField = worldRandomSetField();
        if (lockField == null || setField == null) {
            return;
        }

        try {
            final Object randoms = setField.get(null);
            if (!(randoms instanceof Set<?> randomSet)) {
                return;
            }
            if (!withWorldRandomLock(lockField.get(null), randomSet::clear)) {
                LOGGER.debug("RSLS's world random registry no longer uses a supported lock; skipping its cleanup");
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear RSLS's world random registry", exception);
        }
    }

    private static boolean withWorldRandomLock(Object lock, Runnable action) {

        if (lock instanceof StampedLock stampedLock) {
            final long stamp = stampedLock.writeLock();
            try {
                action.run();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
            return true;
        }
        if (lock instanceof Lock typedLock) {
            typedLock.lock();
            try {
                action.run();
            } finally {
                typedLock.unlock();
            }
            return true;
        }
        return false;
    }

    private static void pruneWorldRandomHolder(ClientLevel keep) {

        final Field lockField = worldRandomLockField();
        final Field setField = worldRandomSetField();
        if (lockField == null || setField == null) {
            return;
        }

        try {
            final Object randoms = setField.get(null);
            if (!(randoms instanceof Set<?> randomSet)) {
                return;
            }
            final Runnable prune = () -> {
                final Iterator<?> iterator = randomSet.iterator();
                while (iterator.hasNext()) {
                    final ClientLevel ownerLevel = worldRandomOwnerLevel(iterator.next());
                    if (ownerLevel != null && ownerLevel != keep) {
                        iterator.remove();
                    }
                }
            };
            if (!withWorldRandomLock(lockField.get(null), prune)) {
                LOGGER.debug("RSLS's world random registry no longer uses a supported lock; skipping its cleanup");
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not prune RSLS's world random registry", exception);
        }
    }

    private static ClientLevel worldRandomOwnerLevel(Object random) {

        if (random == null) {
            return null;
        }
        try {
            final Field[] fields = random.getClass().getDeclaredFields();
            Field ownerField = null;
            for (Field field : fields) {
                if ("owner".equals(field.getName())) {
                    ownerField = field;
                    break;
                }
            }
            if (ownerField == null) {
                for (Field field : fields) {
                    if (!Supplier.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    if (ownerField != null) {
                        return null;
                    }
                    ownerField = field;
                }
            }
            if (ownerField == null || !Supplier.class.isAssignableFrom(ownerField.getType())) {
                return null;
            }
            ownerField.setAccessible(true);
            if (!(ownerField.get(random) instanceof Supplier<?> owner)) {
                return null;
            }
            ClientLevel clientLevel = null;
            for (Field field : owner.getClass().getDeclaredFields()) {
                if (!Level.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                if (!(field.get(owner) instanceof ClientLevel level)) {
                    continue;
                }
                if (clientLevel != null && clientLevel != level) {
                    return null;
                }
                clientLevel = level;
            }
            return clientLevel;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve the level of a world random", exception);
        }
        return null;
    }

    private static void clearCombatNouveauPickedEntity() {

        final Field pickedEntity = combatNouveauPickedEntityField();
        if (pickedEntity == null) {
            return;
        }

        try {
            pickedEntity.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear CombatNouveau's picked entity", exception);
            combatNouveauPickedEntityField = null;
        }
    }

    private static Field combatNouveauPickedEntityField() {

        if (combatNouveauPickedEntityField != null) {
            return combatNouveauPickedEntityField;
        }
        if (combatNouveauUnavailable) {
            return null;
        }
        try {
            final Class<?> projectileUtil = Class.forName(COMBATNOUVEAU_PROJECTILE_UTIL, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field pickedEntity = projectileUtil.getDeclaredField(COMBATNOUVEAU_PICKED_ENTITY);
            if (!Modifier.isStatic(pickedEntity.getModifiers()) || Modifier.isFinal(pickedEntity.getModifiers())
                    || !Entity.class.isAssignableFrom(pickedEntity.getType())) {
                combatNouveauUnavailable = true;
                return null;
            }
            pickedEntity.setAccessible(true);
            combatNouveauPickedEntityField = pickedEntity;
            return pickedEntity;
        } catch (NoSuchFieldException exception) {
            combatNouveauUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve CombatNouveau's picked entity; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearRyoamicLightsLastWorld(Level oldLevel, Level newLevel) {

        final Field lastWorld = ryoamicLightsLastWorldField();
        if (lastWorld == null) {
            return;
        }

        if (oldLevel instanceof ClientLevel clientLevel) {
            for (AbstractClientPlayer player : clientLevel.players()) {
                clearRyoamicLightsLastWorld(lastWorld, player, newLevel);
            }
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            clearRyoamicLightsLastWorld(lastWorld, minecraft.player, newLevel);
        }
    }

    private static void clearRyoamicLightsLastWorld(Field lastWorld, Player player, Level keep) {

        try {
            if (lastWorld.get(player) != keep) {
                lastWorld.set(player, null);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear RyoamicLights' player level", exception);
        }
    }

    private static void clearCooZetaWrapperMemos() {

        final Method forgetEvents = cooZetaForgetEventsMethod();
        if (forgetEvents == null) {
            return;
        }

        try {
            forgetEvents.invoke(null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Collections of Optimizations' memoized event wrappers", exception);
        }
    }

    private static Method cooZetaForgetEventsMethod() {

        if (cooZetaForgetEventsMethod != null) {
            return cooZetaForgetEventsMethod;
        }
        if (cooZetaMemoUnavailable) {
            return null;
        }
        try {
            final Class<?> memo = Class.forName(COO_ZETA_WRAPPER_MEMO, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Method forgetEvents = memo.getDeclaredMethod("forgetEvents");
            if (!Modifier.isStatic(forgetEvents.getModifiers()) || forgetEvents.getParameterCount() != 0) {
                cooZetaMemoUnavailable = true;
                return null;
            }
            forgetEvents.setAccessible(true);
            cooZetaForgetEventsMethod = forgetEvents;
            return forgetEvents;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            cooZetaMemoUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Collections of Optimizations' event memo; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearCooIceAndFireEntityData() {

        final Field cachedData = cooIafEntityDataField();
        if (cachedData == null) {
            return;
        }

        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                if (cachedData.get(player) != null) {
                    cachedData.set(player, null);
                }
            } catch (Exception | LinkageError exception) {
                LOGGER.debug("Could not clear Collections of Optimizations' cached Ice and Fire entity data", exception);
            }
        }

        final Field clientCacheField = iafClientCacheField();
        if (clientCacheField == null) {
            return;
        }
        try {
            if (clientCacheField.get(null) instanceof Map<?, ?> clientCache) {
                clientCache.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Ice and Fire's client entity data cache", exception);
        }
    }

    private static Field cooIafEntityDataField() {

        if (cooIafEntityDataField != null) {
            return cooIafEntityDataField;
        }
        if (cooIafEntityDataUnavailable) {
            return null;
        }
        try {
            final Field field = LivingEntity.class.getDeclaredField(COO_IAF_ENTITY_DATA);
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                    || !LazyOptional.class.isAssignableFrom(field.getType())) {
                cooIafEntityDataUnavailable = true;
                return null;
            }
            field.setAccessible(true);
            cooIafEntityDataField = field;
            return field;
        } catch (NoSuchFieldException exception) {
            cooIafEntityDataUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Collections of Optimizations' Ice and Fire entity data field; will retry on the next level change", exception);
            return null;
        }
    }

    private static Field iafClientCacheField() {

        if (iafClientCacheField != null) {
            return iafClientCacheField;
        }
        if (iafClientCacheUnavailable) {
            return null;
        }
        try {
            final Class<?> provider = Class.forName(IAF_ENTITY_DATA_PROVIDER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field clientCache = provider.getDeclaredField(IAF_CLIENT_CACHE);
            if (!Modifier.isStatic(clientCache.getModifiers()) || !Map.class.isAssignableFrom(clientCache.getType())) {
                iafClientCacheUnavailable = true;
                return null;
            }
            clientCache.setAccessible(true);
            iafClientCacheField = clientCache;
            return clientCache;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            iafClientCacheUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Ice and Fire's client entity data cache; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearImmersiveHotbarControllerOwners() {

        for (int index = 0; index < IMMERSIVE_HOTBAR_CONTROLLERS.length; index++) {
            final Field owner = immersiveHotbarControllerOwnerField(index);
            final Object controller = immersiveHotbarControllerInstances[index];
            if (owner == null || controller == null) {
                continue;
            }
            try {
                if (owner.get(controller) != null) {
                    owner.set(controller, null);
                }
            } catch (Exception | LinkageError exception) {
                LOGGER.debug("Could not clear Immersive Hotbar's cached animation player", exception);
            }
        }
    }

    private static Field immersiveHotbarControllerOwnerField(int index) {

        if (immersiveHotbarControllerOwnerFields[index] != null) {
            return immersiveHotbarControllerOwnerFields[index];
        }
        if (immersiveHotbarControllerUnavailable[index]) {
            return null;
        }
        final String className = IMMERSIVE_HOTBAR_CONTROLLERS[index];
        try {
            final Class<?> controller = Class.forName(className, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field instanceField = controller.getDeclaredField(IMMERSIVE_HOTBAR_CONTROLLER_INSTANCE);
            if (!Modifier.isStatic(instanceField.getModifiers())) {
                immersiveHotbarControllerUnavailable[index] = true;
                return null;
            }
            instanceField.setAccessible(true);
            final Object instance = instanceField.get(null);
            final Field owner = controller.getDeclaredField(IMMERSIVE_HOTBAR_CONTROLLER_OWNER);
            if (instance == null || Modifier.isStatic(owner.getModifiers()) || Modifier.isFinal(owner.getModifiers())
                    || !Player.class.isAssignableFrom(owner.getType())) {
                immersiveHotbarControllerUnavailable[index] = true;
                return null;
            }
            owner.setAccessible(true);
            immersiveHotbarControllerInstances[index] = instance;
            immersiveHotbarControllerOwnerFields[index] = owner;
            return owner;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            immersiveHotbarControllerUnavailable[index] = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Immersive Hotbar controller {}", className, exception);
            return null;
        }
    }

    private static void clearMowziesGeckoFirstPerson() {

        final Field firstPerson = mowziesGeckoFirstPersonField();
        if (firstPerson == null) {
            return;
        }
        try {
            firstPerson.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Mowzie's Gecko first-person player", exception);
        }
    }

    private static Field mowziesGeckoFirstPersonField() {

        if (mowziesGeckoFirstPersonField != null) {
            return mowziesGeckoFirstPersonField;
        }
        if (mowziesGeckoUnavailable) {
            return null;
        }
        try {
            final Class<?> renderer = Class.forName(MOWZIES_GECKO_FIRST_PERSON_RENDERER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field firstPerson = renderer.getDeclaredField(MOWZIES_GECKO_FIRST_PERSON);
            if (!Modifier.isStatic(firstPerson.getModifiers()) || Modifier.isFinal(firstPerson.getModifiers())
                    || !MOWZIES_GECKO_FIRST_PERSON_TYPE.equals(firstPerson.getType().getName())) {
                mowziesGeckoUnavailable = true;
                return null;
            }
            firstPerson.setAccessible(true);
            mowziesGeckoFirstPersonField = firstPerson;
            return firstPerson;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            mowziesGeckoUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Mowzie's Gecko first-person player; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearHudOverlayLevelCaches() {

        final Iterable<NamedGuiOverlay> overlays = GuiOverlayManager.getOverlays();
        if (overlays == null) {
            return;
        }
        for (NamedGuiOverlay named : overlays) {
            final IGuiOverlay overlay = named.overlay();
            if (overlay == null) {
                continue;
            }
            for (Class<?> type = overlay.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    final boolean levelField = Level.class.isAssignableFrom(field.getType());
                    final boolean playerField = Player.class.isAssignableFrom(field.getType());
                    if (!levelField && !playerField) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        final Object value = field.get(overlay);
                        if (levelField && !(value instanceof ClientLevel)) {
                            continue;
                        }
                        if (playerField && !(value instanceof Player)) {
                            continue;
                        }
                        field.set(overlay, null);
                    } catch (Exception | LinkageError exception) {
                        LOGGER.debug("Could not clear HUD overlay field {}.{}", type.getName(), field.getName(), exception);
                    }
                }
            }
        }
    }

    private static void clearForestryBreedingTrackers() {

        final Field trackers = forestryTrackerMapField();
        if (trackers == null || forestryBreedingHandler == null) {
            return;
        }
        try {
            if (trackers.get(forestryBreedingHandler) instanceof Map<?, ?> trackerMap) {
                trackerMap.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Forestry's cached breeding trackers", exception);
        }
    }

    private static Field forestryTrackerMapField() {

        if (forestryTrackerMapField != null) {
            return forestryTrackerMapField;
        }
        if (forestryUnavailable) {
            return null;
        }
        try {
            final Class<?> manager = Class.forName(FORESTRY_BREEDING_TRACKER_MANAGER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field handlerField = manager.getDeclaredField(FORESTRY_BREEDING_HANDLER);
            if (!Modifier.isStatic(handlerField.getModifiers())) {
                forestryUnavailable = true;
                return null;
            }
            handlerField.setAccessible(true);
            final Object handler = handlerField.get(null);
            if (handler == null) {
                forestryUnavailable = true;
                return null;
            }
            final Field trackers = findField(handler.getClass(), FORESTRY_TRACKER_MAP);
            if (trackers == null || Modifier.isStatic(trackers.getModifiers())
                    || !Map.class.isAssignableFrom(trackers.getType())) {
                forestryUnavailable = true;
                return null;
            }
            trackers.setAccessible(true);
            forestryBreedingHandler = handler;
            forestryTrackerMapField = trackers;
            return trackers;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            forestryUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Forestry's cached breeding trackers; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearWootLivingEntities() {
        final Field livingEntities = wootLivingEntitiesField();
        if (livingEntities == null) {
            return;
        }
        try {
            if (livingEntities.get(null) instanceof Map<?, ?> entities) {
                entities.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Woot's cached living entities", exception);
        }
    }

    private static Field wootLivingEntitiesField() {

        if (wootLivingEntitiesField != null) {
            return wootLivingEntitiesField;
        }
        if (wootLivingEntitiesUnavailable) {
            return null;
        }
        try {
            final Class<?> guideCache = Class.forName(WOOT_GUIDE_LIVING_ENTITIES, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field livingEntities = guideCache.getDeclaredField(WOOT_LIVING_ENTITIES);
            if (!Modifier.isStatic(livingEntities.getModifiers()) || !Map.class.isAssignableFrom(livingEntities.getType())) {
                wootLivingEntitiesUnavailable = true;
                return null;
            }
            livingEntities.setAccessible(true);
            wootLivingEntitiesField = livingEntities;
            return livingEntities;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            wootLivingEntitiesUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Woot's cached living entities; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearIronsAdjustmentModifier() {

        final Field anim = ironsAdjustmentModifierAnimField();
        if (anim == null || ironsAdjustmentInstance == null) {
            return;
        }
        try {
            if (anim.get(ironsAdjustmentInstance) != null) {
                anim.set(ironsAdjustmentInstance, null);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Iron's Spellbooks animation modifier", exception);
        }
    }

    private static Field ironsAdjustmentModifierAnimField() {

        if (ironsAdjustmentAnimField != null) {
            return ironsAdjustmentAnimField;
        }
        if (ironsAdjustmentUnavailable) {
            return null;
        }
        try {
            final Class<?> modifier = Class.forName(IRONS_ADJUSTMENT_MODIFIER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field instanceField = modifier.getDeclaredField(IRONS_ADJUSTMENT_INSTANCE);
            if (!Modifier.isStatic(instanceField.getModifiers())) {
                ironsAdjustmentUnavailable = true;
                return null;
            }
            instanceField.setAccessible(true);
            final Object instance = instanceField.get(null);
            final Field anim = findField(modifier, IRONS_ADJUSTMENT_ANIM);
            if (instance == null || anim == null || Modifier.isStatic(anim.getModifiers()) || Modifier.isFinal(anim.getModifiers())
                    || !PLAYER_ANIMATION.equals(anim.getType().getName())) {
                ironsAdjustmentUnavailable = true;
                return null;
            }
            anim.setAccessible(true);
            ironsAdjustmentInstance = instance;
            ironsAdjustmentAnimField = anim;
            return anim;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            ironsAdjustmentUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Iron's Spellbooks animation modifier; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearNuclearCraftTooltipEvent() {        final Field event = nuclearCraftTooltipEventField();
        if (event == null) {
            return;
        }
        try {
            event.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear NuclearCraft's cached tooltip event", exception);
        }
    }

    private static Field nuclearCraftTooltipEventField() {

        if (nuclearCraftTooltipEventField != null) {
            return nuclearCraftTooltipEventField;
        }
        if (nuclearCraftTooltipUnavailable) {
            return null;
        }
        try {
            final Class<?> handler = Class.forName(NUCLEARCRAFT_TOOLTIP_HANDLER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field event = handler.getDeclaredField(NUCLEARCRAFT_PROCESSED_EVENT);
            if (!Modifier.isStatic(event.getModifiers()) || Modifier.isFinal(event.getModifiers())
                    || !ITEM_TOOLTIP_EVENT.equals(event.getType().getName())) {
                nuclearCraftTooltipUnavailable = true;
                return null;
            }
            event.setAccessible(true);
            nuclearCraftTooltipEventField = event;
            return event;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            nuclearCraftTooltipUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve NuclearCraft's cached tooltip event; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearImmersiveHotbarCameraPlayer() {

        final Minecraft minecraft = Minecraft.getInstance();
        final Object gui = minecraft.gui;
        if (gui == null) {
            return;
        }

        final Field cameraPlayer = immersiveHotbarCameraPlayerField(gui.getClass());
        if (cameraPlayer == null) {
            return;
        }
        try {
            if (cameraPlayer.get(gui) != null) {
                cameraPlayer.set(gui, null);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Immersive Hotbar's cached camera player", exception);
        }
    }

    private static Field immersiveHotbarCameraPlayerField(Class<?> guiClass) {
        if (immersiveHotbarCameraPlayerField != null) {
            return immersiveHotbarCameraPlayerField;
        }
        if (immersiveHotbarUnavailable) {
            return null;
        }
        try {
            final Field cameraPlayer = findField(guiClass, IMMERSIVE_HOTBAR_CAMERA_PLAYER);
            if (cameraPlayer == null) {
                immersiveHotbarUnavailable = true;
                return null;
            }
            if (Modifier.isStatic(cameraPlayer.getModifiers()) || Modifier.isFinal(cameraPlayer.getModifiers())
                    || !Player.class.isAssignableFrom(cameraPlayer.getType())) {
                immersiveHotbarUnavailable = true;
                return null;
            }
            cameraPlayer.setAccessible(true);
            immersiveHotbarCameraPlayerField = cameraPlayer;
            return cameraPlayer;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Immersive Hotbar's cached camera player; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearArsCameraStorage() {

        final Field storage = arsCameraStorageField();
        if (storage == null) {
            return;
        }
        try {
            storage.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Ars Nouveau's camera chunk storage", exception);
        }
    }

    private static Field arsCameraStorageField() {

        if (arsCameraStorageField != null) {
            return arsCameraStorageField;
        }
        if (arsCameraStorageUnavailable) {
            return null;
        }
        try {
            final Class<?> controller = Class.forName(ARS_CAMERA_CONTROLLER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field storage = controller.getDeclaredField(ARS_CAMERA_STORAGE);
            if (!Modifier.isStatic(storage.getModifiers()) || Modifier.isFinal(storage.getModifiers())
                    || !"net.minecraft.client.multiplayer.ClientChunkCache$Storage".equals(storage.getType().getName())) {
                arsCameraStorageUnavailable = true;
                return null;
            }
            storage.setAccessible(true);
            arsCameraStorageField = storage;
            return storage;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            arsCameraStorageUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Ars Nouveau's camera chunk storage; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearHexcastingExampleEntities() {

        final Field examples = hexcastingExampleEntitiesField();
        if (examples == null) {
            return;
        }
        try {
            if (!Modifier.isFinal(examples.getModifiers()) && examples.getType().isAssignableFrom(HashMap.class)) {
                examples.set(null, new HashMap<>());
                return;
            }
            if (examples.get(null) instanceof Map<?, ?> cachedEntities) {
                cachedEntities.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Hexcasting's cached example entities", exception);
        }
    }

    private static Field hexcastingExampleEntitiesField() {

        if (hexcastingExampleEntitiesField != null) {
            return hexcastingExampleEntitiesField;
        }
        if (hexcastingUnavailable) {
            return null;
        }
        try {
            final Class<?> ingredient = Class.forName(HEXCASTING_BRAINSWEEPEE, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field examples = ingredient.getDeclaredField(HEXCASTING_EXAMPLE_ENTITIES);
            if (!Modifier.isStatic(examples.getModifiers()) || !Map.class.isAssignableFrom(examples.getType())) {
                hexcastingUnavailable = true;
                return null;
            }
            examples.setAccessible(true);
            hexcastingExampleEntitiesField = examples;
            return examples;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            hexcastingUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Hexcasting's cached example entities; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearEuphoriaShaderLevel() {

        final Field lastLevel = euphoriaLastLevelField();
        if (lastLevel == null) {
            return;
        }
        try {
            lastLevel.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Euphoria Patcher's cached dimension level", exception);
        }
    }

    private static Field euphoriaLastLevelField() {

        if (euphoriaLastLevelField != null) {
            return euphoriaLastLevelField;
        }
        if (euphoriaUnavailable) {
            return null;
        }
        try {
            final Class<?> refresh = Class.forName(EUPHORIA_SHADER_REFRESH, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field lastLevel = refresh.getDeclaredField(EUPHORIA_LAST_LEVEL);
            if (!Modifier.isStatic(lastLevel.getModifiers()) || Modifier.isFinal(lastLevel.getModifiers()) || lastLevel.getType().isPrimitive()) {
                euphoriaUnavailable = true;
                return null;
            }
            lastLevel.setAccessible(true);
            euphoriaLastLevelField = lastLevel;
            return lastLevel;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            euphoriaUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Euphoria Patcher's cached dimension level; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearPneumaticCraftAreaLevel() {

        final Field level = pneumaticAreaLevelField();
        if (level == null || pneumaticAreaRenderManager == null) {
            return;
        }
        try {
            level.set(pneumaticAreaRenderManager, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear PneumaticCraft's cached area render level", exception);
        }
    }

    private static Field pneumaticAreaLevelField() {

        if (pneumaticAreaLevelField != null) {
            return pneumaticAreaLevelField;
        }
        if (pneumaticUnavailable) {
            return null;
        }
        try {
            final Class<?> manager = Class.forName(PNEUMATICCRAFT_AREA_RENDER_MANAGER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field instanceField = manager.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            final Object instance = instanceField.get(null);
            final Field level = manager.getDeclaredField(PNEUMATICCRAFT_LEVEL);
            if (instance == null || Modifier.isStatic(level.getModifiers()) || Modifier.isFinal(level.getModifiers())
                    || !Level.class.isAssignableFrom(level.getType())) {
                pneumaticUnavailable = true;
                return null;
            }
            level.setAccessible(true);
            pneumaticAreaRenderManager = instance;
            pneumaticAreaLevelField = level;
            return level;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            pneumaticUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve PneumaticCraft's cached area render level; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearMekanismPlayerStateWorld() {

        final Field world = mekanismPlayerStateWorldField();
        if (world == null || mekanismPlayerState == null) {
            return;
        }
        try {
            world.set(mekanismPlayerState, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Mekanism's cached player state world", exception);
        }
    }

    private static Field mekanismPlayerStateWorldField() {

        if (mekanismPlayerStateWorldField != null) {
            return mekanismPlayerStateWorldField;
        }
        if (mekanismUnavailable) {
            return null;
        }
        try {
            final Class<?> mekanism = Class.forName(MEKANISM, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field stateField = mekanism.getDeclaredField(MEKANISM_PLAYER_STATE);
            stateField.setAccessible(true);
            final Object playerState = stateField.get(null);
            if (playerState == null) {
                mekanismUnavailable = true;
                return null;
            }
            final Field world = playerState.getClass().getDeclaredField(MEKANISM_PLAYER_STATE_WORLD);
            if (Modifier.isStatic(world.getModifiers()) || Modifier.isFinal(world.getModifiers())
                    || !LevelAccessor.class.isAssignableFrom(world.getType())) {
                mekanismUnavailable = true;
                return null;
            }
            world.setAccessible(true);
            mekanismPlayerState = playerState;
            mekanismPlayerStateWorldField = world;
            return world;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            mekanismUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Mekanism's cached player state world; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearCapsulePreviewDispatcher() {

        final Field dispatcher = capsuleDispatcherField();
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Capsule's cached preview dispatcher", exception);
        }
    }

    private static Field capsuleDispatcherField() {

        if (capsuleDispatcherField != null) {
            return capsuleDispatcherField;
        }
        if (capsuleUnavailable) {
            return null;
        }
        try {
            final Class<?> handler = Class.forName(CAPSULE_PREVIEW_HANDLER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field dispatcher = handler.getDeclaredField(CAPSULE_DISPATCHER);
            if (!Modifier.isStatic(dispatcher.getModifiers()) || Modifier.isFinal(dispatcher.getModifiers())
                    || !CHUNK_RENDER_DISPATCHER.equals(dispatcher.getType().getName())) {
                capsuleUnavailable = true;
                return null;
            }
            dispatcher.setAccessible(true);
            capsuleDispatcherField = dispatcher;
            return dispatcher;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            capsuleUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Capsule's cached preview dispatcher; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearSecurityCraftCameraDispatcher() {

        final Method clear = securityCraftClearMethod();
        if (clear != null) {
            try {
                clear.invoke(null);
            } catch (Exception | LinkageError exception) {
                LOGGER.debug("Could not clear SecurityCraft's cached camera sections", exception);
            }
        }

        final Field dispatcher = securityCraftDispatcherField();
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear SecurityCraft's cached camera dispatcher", exception);
        }
    }

    private static Field securityCraftDispatcherField() {

        if (securityCraftDispatcherField != null) {
            return securityCraftDispatcherField;
        }
        if (securityCraftUnavailable) {
            return null;
        }
        try {
            final Class<?> extension = Class.forName(SECURITYCRAFT_CAMERA_VIEW, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field dispatcher = extension.getDeclaredField(SECURITYCRAFT_DISPATCHER);
            if (!Modifier.isStatic(dispatcher.getModifiers()) || Modifier.isFinal(dispatcher.getModifiers())
                    || !CHUNK_RENDER_DISPATCHER.equals(dispatcher.getType().getName())) {
                securityCraftUnavailable = true;
                return null;
            }
            dispatcher.setAccessible(true);
            securityCraftDispatcherField = dispatcher;
            return dispatcher;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            securityCraftUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve SecurityCraft's cached camera dispatcher; will retry on the next level change", exception);
            return null;
        }
    }

    private static Method securityCraftClearMethod() {

        if (securityCraftClearMethod != null) {
            return securityCraftClearMethod;
        }
        if (securityCraftClearUnavailable) {
            return null;
        }
        try {
            final Class<?> extension = Class.forName(SECURITYCRAFT_CAMERA_VIEW, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Method clear = extension.getDeclaredMethod("clear");
            if (!Modifier.isStatic(clear.getModifiers()) || clear.getParameterCount() != 0) {
                securityCraftClearUnavailable = true;
                return null;
            }
            clear.setAccessible(true);
            securityCraftClearMethod = clear;
            return clear;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            securityCraftClearUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve SecurityCraft's camera section cleanup; will retry on the next level change", exception);
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                // Keep looking in the superclass.
            }
        }
        return null;
    }

    private static Field ryoamicLightsLastWorldField() {

        if (ryoamicLightsLastWorldField != null) {
            return ryoamicLightsLastWorldField;
        }
        if (ryoamicLightsUnavailable) {
            return null;
        }
        try {
            final Field lastWorld = Player.class.getDeclaredField(RYOAMICLIGHTS_PLAYER_LAST_WORLD);
            if (Modifier.isStatic(lastWorld.getModifiers()) || Modifier.isFinal(lastWorld.getModifiers())
                    || !Level.class.isAssignableFrom(lastWorld.getType())) {
                ryoamicLightsUnavailable = true;
                return null;
            }
            lastWorld.setAccessible(true);
            ryoamicLightsLastWorldField = lastWorld;
            return lastWorld;
        } catch (NoSuchFieldException exception) {
            ryoamicLightsUnavailable = true;
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve RyoamicLights' player level; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearStaleStaticLevelCaches(ClientLevel keep) {

        final Field[] fields = staticLevelCacheFields();
        if (fields == null) {
            return;
        }

        for (Field field : fields) {
            try {
                if (field.get(null) instanceof ClientLevel level && level != keep) {
                    field.set(null, null);
                }
            } catch (Exception | LinkageError exception) {
                LOGGER.debug("Could not clear static level cache {}", field.getName(), exception);
            }
        }
    }

    private static Field[] staticLevelCacheFields() {

        if (staticLevelCacheFields != null) {
            return staticLevelCacheFields;
        }
        if (staticLevelCachesResolved) {
            return null;
        }
        try {
            final List<Field> fields = new ArrayList<>();
            for (String[] cache : STATIC_LEVEL_CACHES) {
                try {
                    final Class<?> holder = Class.forName(cache[0], false, MultiplayerSessionLeakCleanup.class.getClassLoader());
                    final Field field;
                    try {
                        field = holder.getDeclaredField(cache[1]);
                    } catch (NoSuchFieldException exception) {
                        LOGGER.debug("Static level cache {}.{} was not found; skipping it", cache[0], cache[1]);
                        continue;
                    }
                    if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                        LOGGER.debug("Static level cache {}.{} is no longer a mutable static field; skipping it", cache[0], cache[1]);
                        continue;
                    }
                    if (!ClientLevel.class.isAssignableFrom(field.getType())) {
                        LOGGER.debug("Static level cache {}.{} is no longer a ClientLevel field; skipping it", cache[0], cache[1]);
                        continue;
                    }
                    field.setAccessible(true);
                    fields.add(field);
                } catch (ClassNotFoundException exception) {
                    // The mod is optional; a missing holder is expected.
                } catch (Exception | LinkageError exception) {
                    LOGGER.debug("Could not resolve static level cache {}.{}; skipping it", cache[0], cache[1], exception);
                }
            }
            staticLevelCacheFields = fields.toArray(new Field[0]);
            staticLevelCachesResolved = true;
            return staticLevelCacheFields;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve static level caches; will retry on the next level change", exception);
            return null;
        }
    }

    private static void clearSmartParticlesFields() {

        try {
            final ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
            if (particleEngine == null) {
                return;
            }

            resolveSmartParticlesFields();
            if (smartParticlesHeapField != null) {
                final Object particles = smartParticlesHeapField.get(particleEngine);
                if (particles instanceof Object[] heap) {
                    Arrays.fill(heap, null);
                }
            }
            if (smartParticlesKeepField != null) {
                final Object keep = smartParticlesKeepField.get(particleEngine);
                if (keep instanceof Set<?> keptParticles) {
                    keptParticles.clear();
                }
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear the particle engine's session particles", exception);
        }
    }

    private static Field worldRandomLockField() {
        resolveWorldRandomHolder();
        return worldRandomLockField;
    }

    private static Field worldRandomSetField() {
        resolveWorldRandomHolder();
        return worldRandomSetField;
    }

    private static void resolveWorldRandomHolder() {

        if (worldRandomLockField != null && worldRandomSetField != null) {
            return;
        }
        if (worldRandomUnavailable) {
            return;
        }
        try {
            final Class<?> holder = Class.forName(RSLS_WORLD_RANDOM_HOLDER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field lock = holder.getDeclaredField("lock");
            final Field randoms = holder.getDeclaredField("randoms");
            lock.setAccessible(true);
            randoms.setAccessible(true);
            worldRandomLockField = lock;
            worldRandomSetField = randoms;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // RSLS is optional or changed its internals; stop looking on later logouts.
            worldRandomUnavailable = true;
            LOGGER.debug("RSLS world random registry not found; skipping its logout cleanup", exception);
        } catch (Exception | LinkageError exception) {
            // Retry on the next logout instead of disabling the cleanup for the rest of the process.
            LOGGER.debug("Could not resolve RSLS's world random registry; will retry on the next logout", exception);
        }
    }

    private static void clearFakeClientLevel() {

        final Field instanceField = fakeClientLevelInstanceField();
        if (instanceField == null) {
            return;
        }

        try {
            instanceField.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear TinyMultiBlockLib's fake client level", exception);
            fakeClientLevelInstanceField = null;
        }
    }

    private static Field fakeClientLevelInstanceField() {

        if (fakeClientLevelInstanceField != null) {
            return fakeClientLevelInstanceField;
        }
        if (fakeClientLevelUnavailable) {
            return null;
        }
        try {
            final Class<?> fakeLevel = Class.forName(TINY_MULTIBLOCK_FAKE_LEVEL, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field instance = fakeLevel.getDeclaredField("INSTANCE");
            if (Modifier.isStatic(instance.getModifiers()) && instance.getType() == fakeLevel) {
                instance.setAccessible(true);
                fakeClientLevelInstanceField = instance;
                return instance;
            }
            fakeClientLevelUnavailable = true;
            return null;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // TinyMultiBlockLib is optional or changed its internals; stop looking on later logouts.
            fakeClientLevelUnavailable = true;
            LOGGER.debug("TinyMultiBlockLib fake client level not found; skipping its logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve TinyMultiBlockLib's fake client level; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearCyclicPackagerCaches() {

        final Field[] cacheFields = cyclicPackagerCacheFields();
        if (cacheFields == null) {
            return;
        }

        for (Field cacheField : cacheFields) {
            try {
                if (cacheField.get(null) instanceof Map<?, ?> cache) {
                    cache.clear();
                }
            } catch (Exception | LinkageError exception) {
                LOGGER.debug("Could not clear Cyclic's packager cache {}", cacheField.getName(), exception);
            }
        }
    }

    private static Field[] cyclicPackagerCacheFields() {

        if (cyclicPackagerCacheFields != null) {
            return cyclicPackagerCacheFields;
        }
        if (cyclicPackagerUnavailable) {
            return null;
        }
        try {
            final Class<?> packager = Class.forName(CYCLIC_UTIL_PACKAGER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field[] caches = new Field[CYCLIC_PACKAGER_CACHES.length];
            for (int i = 0; i < CYCLIC_PACKAGER_CACHES.length; i++) {
                final Field cache = packager.getDeclaredField(CYCLIC_PACKAGER_CACHES[i]);
                if (!Modifier.isStatic(cache.getModifiers()) || !Map.class.isAssignableFrom(cache.getType())) {
                    cyclicPackagerUnavailable = true;
                    return null;
                }
                cache.setAccessible(true);
                caches[i] = cache;
            }
            cyclicPackagerCacheFields = caches;
            return caches;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // Cyclic is optional or changed its internals; stop looking on later logouts.
            cyclicPackagerUnavailable = true;
            LOGGER.debug("Cyclic packager caches not found; skipping their logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Cyclic's packager caches; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearAe2WtlibHandlers() {

        final Field playersField = ae2PlayersField();
        if (playersField == null) {
            return;
        }

        try {
            if (playersField.get(null) instanceof Map<?, ?> players) {
                players.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear AE2WTLib's crafting terminal handlers", exception);
        }
    }

    private static Field ae2PlayersField() {

        if (ae2PlayersField != null) {
            return ae2PlayersField;
        }
        if (ae2Unavailable) {
            return null;
        }
        try {
            final Class<?> handler = Class.forName(AE2WTLIB_CRAFTING_HANDLER, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field players = handler.getDeclaredField("players");
            if (!Modifier.isStatic(players.getModifiers()) || !Map.class.isAssignableFrom(players.getType())) {
                ae2Unavailable = true;
                return null;
            }
            players.setAccessible(true);
            ae2PlayersField = players;
            return players;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // AE2WTLib is optional or changed its internals; stop looking on later logouts.
            ae2Unavailable = true;
            LOGGER.debug("AE2WTLib crafting terminal handler map not found; skipping its logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve AE2WTLib's crafting terminal handler map; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearGeckoLibAnimatableRegistry() {

        final Field syncedField = geckoLibSyncedAnimatableField();
        if (syncedField == null) {
            return;
        }

        try {
            if (syncedField.get(null) instanceof Map<?, ?> syncedAnimatables) {
                syncedAnimatables.entrySet().removeIf(entry ->
                        entry.getValue() instanceof Entity || entry.getValue() instanceof BlockEntity);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear GeckoLib's synced animatable registry", exception);
        }
    }

    private static Field geckoLibSyncedAnimatableField() {

        if (geckoLibSyncedAnimatableField != null) {
            return geckoLibSyncedAnimatableField;
        }
        if (geckoLibUnavailable) {
            return null;
        }
        try {
            final Class<?> network = Class.forName(GECKOLIB_NETWORK, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field synced = network.getDeclaredField(GECKOLIB_SYNCED_ANIMATABLES);
            if (!Modifier.isStatic(synced.getModifiers()) || !Map.class.isAssignableFrom(synced.getType())) {
                geckoLibUnavailable = true;
                return null;
            }
            synced.setAccessible(true);
            geckoLibSyncedAnimatableField = synced;
            return synced;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // GeckoLib is optional or changed its internals; stop looking on later logouts.
            geckoLibUnavailable = true;
            LOGGER.debug("GeckoLib synced animatable registry not found; skipping its logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve GeckoLib's synced animatable registry; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearSmartParticles() {

        try {
            final ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
            if (particleEngine == null) {
                return;
            }

            // Vanilla clears its own collections here; the Smart Particles array is not touched by it.
            particleEngine.setLevel(null);
            clearSmartParticlesFields();
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear the particle engine's session particles", exception);
        }
    }

    private static void resolveSmartParticlesFields() {

        if (smartParticlesHeapField != null && smartParticlesKeepField != null) {
            return;
        }
        if (smartParticlesUnavailable) {
            return;
        }
        try {
            final Field heap = ParticleEngine.class.getDeclaredField(SMART_PARTICLES_HEAP);
            final Field keep = ParticleEngine.class.getDeclaredField(SMART_PARTICLES_KEEP);
            heap.setAccessible(true);
            keep.setAccessible(true);
            smartParticlesHeapField = heap;
            smartParticlesKeepField = keep;
        } catch (NoSuchFieldException exception) {
            // Smart Particles is optional; these fields only exist when its mixin applied.
            smartParticlesUnavailable = true;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Smart Particles' fields; will retry on the next logout", exception);
        }
    }

    private static void clearOpposingForceSoundCache() {

        final Field soundMapField = opposingForceSoundMapField();
        if (soundMapField == null) {
            return;
        }

        try {
            if (soundMapField.get(null) instanceof Map<?, ?> soundInstances) {
                soundInstances.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Opposing Force's entity sound map", exception);
        }
    }

    private static Field opposingForceSoundMapField() {

        if (opposingForceSoundMapField != null) {
            return opposingForceSoundMapField;
        }
        if (opposingForceUnavailable) {
            return null;
        }
        try {
            final Class<?> clientProxy = Class.forName(OPPOSING_FORCE_CLIENT_PROXY, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field soundMap = clientProxy.getDeclaredField(OPPOSING_FORCE_SOUND_MAP);
            if (!Modifier.isStatic(soundMap.getModifiers()) || !Map.class.isAssignableFrom(soundMap.getType())) {
                opposingForceUnavailable = true;
                return null;
            }
            soundMap.setAccessible(true);
            opposingForceSoundMapField = soundMap;
            return soundMap;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // Opposing Force is optional or changed its internals; stop looking on later logouts.
            opposingForceUnavailable = true;
            LOGGER.debug("Opposing Force entity sound map not found; skipping its logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Opposing Force's entity sound map; will retry on the next logout", exception);
            return null;
        }
    }

    private static void clearSophisticatedLinkerMenu() {

        final Field requestedMenuField = sophisticatedRequestedMenuField();
        if (requestedMenuField == null) {
            return;
        }

        try {
            requestedMenuField.set(null, null);
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear Sophisticated Core's requested menu", exception);
        }
    }

    private static Field sophisticatedRequestedMenuField() {

        if (sophisticatedRequestedMenuField != null) {
            return sophisticatedRequestedMenuField;
        }
        if (sophisticatedUnavailable) {
            return null;
        }
        try {
            final Class<?> diagnostics = Class.forName(SOPHISTICATED_LINKER_DIAGNOSTICS, false, MultiplayerSessionLeakCleanup.class.getClassLoader());
            final Field requestedMenu = diagnostics.getDeclaredField(SOPHISTICATED_REQUESTED_MENU);
            if (!Modifier.isStatic(requestedMenu.getModifiers()) || Modifier.isFinal(requestedMenu.getModifiers())
                    || !AbstractContainerMenu.class.isAssignableFrom(requestedMenu.getType())) {
                sophisticatedUnavailable = true;
                return null;
            }
            requestedMenu.setAccessible(true);
            sophisticatedRequestedMenuField = requestedMenu;
            return requestedMenu;
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            // Sophisticated Core is optional or changed its internals; stop looking on later logouts.
            sophisticatedUnavailable = true;
            LOGGER.debug("Sophisticated Core linker diagnostics not found; skipping their logout cleanup", exception);
            return null;
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not resolve Sophisticated Core's requested menu; will retry on the next logout", exception);
            return null;
        }
    }
}
