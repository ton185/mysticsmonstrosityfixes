package com.name.mysticmonstrosityfixes.leaks;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;

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
    }

    /**
     * Runs one optional cleanup and never lets a missing mod, a changed field, or a broken static
     * initializer abort the remaining cleanups. A missing targeted mod is expected to be a no-op.
     */
    private static void runSafely(String name, Runnable cleanup) {

        try {
            cleanup.run();
        } catch (Exception | LinkageError exception) {
            LOGGER.error("Client session cleanup step '{}' failed", name, exception);
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
            if (lockField.get(null) instanceof StampedLock lock) {
                final long stamp = lock.writeLock();
                try {
                    randomSet.clear();
                } finally {
                    lock.unlockWrite(stamp);
                }
            } else {
                randomSet.clear();
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.debug("Could not clear RSLS's world random registry", exception);
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
            if (Modifier.isStatic(instance.getModifiers())) {
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

            resolveSmartParticlesFields();
            if (smartParticlesHeapField == null && smartParticlesKeepField == null) {
                return;
            }

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
            if (!Modifier.isStatic(heap.getModifiers()) || !Modifier.isStatic(keep.getModifiers())) {
                smartParticlesUnavailable = true;
                return;
            }
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
            if (!Modifier.isStatic(requestedMenu.getModifiers())) {
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
