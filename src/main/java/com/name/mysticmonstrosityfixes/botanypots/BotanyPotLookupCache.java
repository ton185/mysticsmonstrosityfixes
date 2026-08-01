package com.name.mysticmonstrosityfixes.botanypots;

import com.name.mysticmonstrosityfixes.mixin.IngredientAccessor;
import com.name.mysticmonstrosityfixes.mixin.RecipeManagerAccessor;
import net.darkhax.botanypots.BotanyPotHelper;
import net.darkhax.botanypots.block.BlockEntityBotanyPot;
import net.darkhax.botanypots.data.recipes.crop.BasicCrop;
import net.darkhax.botanypots.data.recipes.crop.Crop;
import net.darkhax.botanypots.data.recipes.soil.BasicSoil;
import net.darkhax.botanypots.data.recipes.soil.Soil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoizes the soil and crop recipe lookups performed by BotanyPotHelper.
 * <p>
 * Every lookup copies the entire soil/crop recipe list ({@link RecipeManager#getAllRecipesFor} is a {@code List.copyOf})
 * and then scans it linearly. Pots hit that path constantly: the container revalidates its contents on every inventory
 * change, {@code BotanyPotContainer#update} retries the lookup every single tick for as long as a slot holds an item
 * that does not currently resolve (a seed that does not match the soil, for example), and hoppers pointed at a pot run
 * it through {@code canPlaceItem} on every insert attempt.
 * <p>
 * The scan is a pure function of the item stack, the recipe list and - for crops - the soil currently in the pot, so the
 * result is cached per side and thrown away whenever recipes or item tags change. The lookup event is still fired for
 * every call so addons that override lookups keep working.
 */
public final class BotanyPotLookupCache {

    /**
     * Cached entries are keyed by item and NBT, so a pot fed junk NBT could otherwise grow the map forever.
     */
    private static final int MAX_ENTRIES = 2048;

    private static final Object NO_MATCH = new Object();

    private static final BotanyPotLookupCache CLIENT = new BotanyPotLookupCache();
    private static final BotanyPotLookupCache SERVER = new BotanyPotLookupCache();

    private final Map<Key, Object> soils = new ConcurrentHashMap<>();
    private final Map<Key, Object> crops = new ConcurrentHashMap<>();

    /**
     * The recipe map instance the cached entries were built from. Both {@code apply} and {@code replaceRecipes} swap
     * this map out, so an identity change means the recipes were reloaded.
     */
    @Nullable
    private Object knownRecipes = null;

    /**
     * Ingredients cache their matching stacks and Forge bumps this counter whenever item tags change.
     */
    private int knownIngredientState = -1;

    private boolean cachesSoil = false;
    private boolean cachesCrop = false;

    private BotanyPotLookupCache() {

    }

    /**
     * @param level The level the lookup is being performed for.
     * @return The cache for the level's side, refreshed if recipes or tags have changed. Null if the level is unknown,
     *         in which case the caller must fall back to the original lookup.
     */
    @Nullable
    public static BotanyPotLookupCache get(@Nullable Level level) {
        if (level == null) {
            return null;
        }
        final BotanyPotLookupCache cache = level.isClientSide ? CLIENT : SERVER;
        cache.refresh(level.getRecipeManager());
        return cache;
    }

    /**
     * @return true if every loaded soil recipe is a plain BasicSoil, whose lookup only depends on the stack. Custom soil
     *         types may inspect the world, so they are never cached.
     */
    public boolean cachesSoil() {
        return this.cachesSoil;
    }

    /**
     * @return true if every loaded crop recipe is a plain BasicCrop, whose lookup only depends on the stack and the
     *         soil in the pot.
     */
    public boolean cachesCrop() {
        return this.cachesCrop;
    }

    private void refresh(RecipeManager manager) {
        final Object recipes = ((RecipeManagerAccessor) manager).mmf$getRecipes();
        final int ingredientState = IngredientAccessor.mmf$getInvalidationCounter().get();
        if (recipes != this.knownRecipes || ingredientState != this.knownIngredientState) {
            this.knownRecipes = recipes;
            this.knownIngredientState = ingredientState;
            this.soils.clear();
            this.crops.clear();
            this.cachesSoil = allExactly(allSoils(manager), BasicSoil.class);
            this.cachesCrop = allExactly(allCrops(manager), BasicCrop.class);
        }
    }

    /**
     * Performs the same scan as BotanyPotHelper#findSoil, without the lookup event, and remembers the result.
     */
    @Nullable
    public Soil findSoil(Level level, BlockPos pos, @Nullable BlockEntityBotanyPot pot, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        final Key key = new Key(stack.getItem(), stack.getTag(), null);
        final Object cached = this.soils.get(key);
        if (cached != null) {
            return cached == NO_MATCH ? null : (Soil) cached;
        }
        Soil found = null;
        for (Soil soil : allSoils(level.getRecipeManager())) {
            if (soil.matchesLookup(level, pos, pot, stack)) {
                found = soil;
                break;
            }
        }
        store(this.soils, key.detached(), found);
        return found;
    }

    /**
     * Performs the same scan as BotanyPotHelper#findCrop, without the lookup event, and remembers the result. A crop
     * only matches when it can grow in the soil that is currently in the pot, so the soil is part of the key.
     */
    @Nullable
    public Crop findCrop(Level level, BlockPos pos, @Nullable BlockEntityBotanyPot pot, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        final Key key = new Key(stack.getItem(), stack.getTag(), pot != null ? pot.getSoil() : null);
        final Object cached = this.crops.get(key);
        if (cached != null) {
            return cached == NO_MATCH ? null : (Crop) cached;
        }
        Crop found = null;
        for (Crop crop : allCrops(level.getRecipeManager())) {
            if (crop.matchesLookup(level, pos, pot, stack)) {
                found = crop;
                break;
            }
        }
        store(this.crops, key.detached(), found);
        return found;
    }

    private static void store(Map<Key, Object> cache, Key key, @Nullable Object value) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, value == null ? NO_MATCH : value);
    }

    private static boolean allExactly(List<?> recipes, Class<?> expected) {
        for (Object recipe : recipes) {
            if (recipe.getClass() != expected) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Soil> allSoils(RecipeManager manager) {
        return (List<Soil>) BotanyPotHelper.getAllRecipes(manager, (RecipeType) BotanyPotHelper.SOIL_TYPE.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Crop> allCrops(RecipeManager manager) {
        return (List<Crop>) BotanyPotHelper.getAllRecipes(manager, (RecipeType) BotanyPotHelper.CROP_TYPE.get());
    }

    private static final class Key {

        private final Item item;
        @Nullable
        private final CompoundTag tag;
        /**
         * Extra input the lookup depends on, compared by identity. The pot's soil for crop lookups, null otherwise.
         */
        @Nullable
        private final Object context;
        private final int hash;

        private Key(Item item, @Nullable CompoundTag tag, @Nullable Object context) {
            this.item = item;
            this.tag = tag;
            this.context = context;
            this.hash = 31 * (31 * item.hashCode() + Objects.hashCode(tag)) + System.identityHashCode(context);
        }

        /**
         * @return A key that is safe to retain, copying the tag so later mutation of the stack can not corrupt the map.
         */
        private Key detached() {
            return this.tag == null ? this : new Key(this.item, this.tag.copy(), this.context);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return this.item == key.item && this.context == key.context && Objects.equals(this.tag, key.tag);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}
