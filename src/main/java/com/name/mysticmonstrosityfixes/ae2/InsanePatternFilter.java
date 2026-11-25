package com.name.mysticmonstrosityfixes.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.me.service.CraftingService;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Insane AE2 Addons' provider-card filter to the pattern collection returned by AE2 Crafting
 * Optimizer's memoizer. The filter mirrors InsaneAE2's MixinCraftingTreeNode handler, but reads the
 * optional classes by name so an updated addon or a missing optional provider cannot stop crafting.
 */
public final class InsanePatternFilter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Class<?>, Method> SIM_REQUESTERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> MISSING_SIM_REQUESTERS = ConcurrentHashMap.newKeySet();
    private static volatile FilterRuntime runtime;
    private static volatile boolean resolved;

    private InsanePatternFilter() {
    }

    public static Collection<IPatternDetails> filter(
            Object job, ICraftingService service, Collection<IPatternDetails> candidates
    ) {
        if (job == null || candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        FilterRuntime filter = runtime();
        if (filter == null || !filter.cardsEnabled()) {
            return candidates;
        }
        ICraftingSimulationRequester requester = filter.simRequester(job);
        if (requester == null) {
            return candidates;
        }
        IActionSource source = requester.getActionSource();
        if (source == null) {
            return candidates;
        }
        IGridNode gridNode = requester.getGridNode();
        if (gridNode == null) {
            return candidates;
        }
        IGrid grid = gridNode.getGrid();
        if (grid == null) {
            return candidates;
        }
        boolean isPlayer = source.player().isPresent();
        boolean isMachine = source.machine().isPresent();
        List<IPatternDetails> filtered = new ArrayList<>(candidates.size());
        for (IPatternDetails pattern : candidates) {
            if (allowed(pattern, service, grid, isPlayer, isMachine, filter)) {
                filtered.add(pattern);
            }
        }
        return filtered;
    }

    private static boolean allowed(
            IPatternDetails pattern, ICraftingService service, IGrid grid,
            boolean isPlayer, boolean isMachine, FilterRuntime filter
    ) {
        if (!(service instanceof CraftingService craftingService)) {
            return true;
        }
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(pattern)) {
            providers.add(provider);
        }
        if (providers.isEmpty()) {
            return false;
        }
        for (IGridNode node : grid.getNodes()) {
            ICraftingProvider provider = node.getService(ICraftingProvider.class);
            if (provider == null || !providers.contains(provider)) {
                continue;
            }
            IUpgradeInventory upgrades = filter.upgrades(node.getOwner());
            if (upgrades == null) {
                return true;
            }
            boolean automation = filter.hasCard(upgrades, filter.automationCard);
            boolean player = filter.hasCard(upgrades, filter.playerCard);
            if (automation && !player) {
                if (isMachine && !isPlayer) {
                    return true;
                }
            } else if (player && !automation) {
                if (isPlayer) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private static FilterRuntime runtime() {
        if (resolved) {
            return runtime;
        }
        synchronized (InsanePatternFilter.class) {
            if (resolved) {
                return runtime;
            }
            try {
                runtime = FilterRuntime.create();
            } catch (Throwable throwable) {
                LOGGER.debug("InsaneAE2 provider-card filter is unavailable; keeping ACO's pattern candidates", throwable);
                runtime = null;
            }
            resolved = true;
            return runtime;
        }
    }

    private static Class<?> classFor(String name) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(name, false, context);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(name, false, InsanePatternFilter.class.getClassLoader());
    }

    private static Method simRequesterAccessor(Class<?> type) {
        if (MISSING_SIM_REQUESTERS.contains(type)) {
            return null;
        }
        Method cached = SIM_REQUESTERS.get(type);
        if (cached != null) {
            return cached;
        }
        Method found = method(type, "getSimRequester", 0);
        if (found == null) {
            MISSING_SIM_REQUESTERS.add(type);
        } else {
            SIM_REQUESTERS.put(type, found);
        }
        return found;
    }

    private static Method method(Class<?> type, String name, int parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return method;
                }
            }
        }
        return null;
    }

    private static final class FilterRuntime {
        private final Object configValue;
        private final Method configGet;
        private final Class<?> blockEntityType;
        private final Method blockEntityUpgrades;
        private final Class<?> partType;
        private final Method partUpgrades;
        private final Object automationCard;
        private final Object playerCard;

        private FilterRuntime(
                Object configValue, Method configGet,
                Class<?> blockEntityType, Method blockEntityUpgrades,
                Class<?> partType, Method partUpgrades,
                Object automationCard, Object playerCard
        ) {
            this.configValue = configValue;
            this.configGet = configGet;
            this.blockEntityType = blockEntityType;
            this.blockEntityUpgrades = blockEntityUpgrades;
            this.partType = partType;
            this.partUpgrades = partUpgrades;
            this.automationCard = automationCard;
            this.playerCard = playerCard;
        }

        private static FilterRuntime create() {
            Object configValue = null;
            Method configGet = null;
            try {
                Class<?> config = classFor("net.oktawia.insaneae2addons.InsaneConfig");
                Object common = config.getField("COMMON").get(null);
                configValue = common.getClass().getField("PROVIDER_CARDS_ENABLED").get(common);
                configGet = method(configValue.getClass(), "get", 0);
            } catch (Throwable ignored) {
            }
            Class<?> blockEntityType = null;
            Method blockEntityUpgrades = null;
            Class<?> partType = null;
            Method partUpgrades = null;
            try {
                blockEntityType = classFor("net.oktawia.crazyae2addons.entities.CrazyPatternProviderBE");
                blockEntityUpgrades = method(blockEntityType, "getUpgrades", 0);
            } catch (Throwable ignored) {
                blockEntityType = null;
            }
            try {
                partType = classFor("net.oktawia.crazyae2addons.parts.CrazyPatternProviderPart");
                partUpgrades = method(partType, "getUpgrades", 0);
            } catch (Throwable ignored) {
                partType = null;
            }
            Object automationCard = card("AUTOMATION_UPGRADE_CARD");
            Object playerCard = card("PLAYER_UPGRADE_CARD");
            return new FilterRuntime(
                    configValue, configGet,
                    blockEntityType, blockEntityUpgrades, partType, partUpgrades,
                    automationCard, playerCard);
        }

        private static Object card(String fieldName) {
            try {
                Class<?> registrar = classFor("net.oktawia.insaneae2addons.defs.regs.InsaneItemRegistrar");
                Object registryObject = registrar.getField(fieldName).get(null);
                Method get = method(registryObject.getClass(), "get", 0);
                return get == null ? null : get.invoke(registryObject);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private boolean cardsEnabled() {
            if (configValue == null || configGet == null) {
                return true;
            }
            try {
                Object value = configGet.invoke(configValue);
                return !(value instanceof Boolean enabled) || enabled;
            } catch (Throwable ignored) {
                return true;
            }
        }

        private ICraftingSimulationRequester simRequester(Object job) {
            if (job == null) {
                return null;
            }
            try {
                Method accessor = simRequesterAccessor(job.getClass());
                if (accessor == null) {
                    return null;
                }
                Object value = accessor.invoke(job);
                return value instanceof ICraftingSimulationRequester requester ? requester : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private IUpgradeInventory upgrades(Object owner) {
            if (owner == null) {
                return null;
            }
            try {
                if (blockEntityType != null && blockEntityType.isInstance(owner) && blockEntityUpgrades != null) {
                    Object value = blockEntityUpgrades.invoke(owner);
                    if (value instanceof IUpgradeInventory upgrades) {
                        return upgrades;
                    }
                }
                if (partType != null && partType.isInstance(owner) && partUpgrades != null) {
                    Object value = partUpgrades.invoke(owner);
                    if (value instanceof IUpgradeInventory upgrades) {
                        return upgrades;
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private boolean hasCard(IUpgradeInventory upgrades, Object card) {
            if (!(card instanceof ItemLike item)) {
                return false;
            }
            try {
                return upgrades.isInstalled(item);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
