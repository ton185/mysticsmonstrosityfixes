package com.name.mysticmonstrosityfixes.avaritia;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.List;

/**
 * Drop-in replacement for Forge's {@code RecipeMatcher.findMatches} that cannot blow up on large shapeless
 * recipes.
 * <p>
 * Forge decides whether the inputs fit the ingredients with a recursive backtracking search over every possible
 * input-to-ingredient pairing. That search is exponential: with a 9x9 Extreme Crafting Table grid and ingredients
 * whose item lists overlap (any tag ingredient), the number of pairings it tries grows faster than the server can
 * tick. A shapeless recipe lookup that should take microseconds can spend minutes inside
 * {@code RecipeMatcher.backtrack} and trip the server watchdog.
 * <p>
 * This class answers the same question with a standard augmenting-path maximum bipartite matching. Each ingredient
 * is a test that accepts some of the input stacks, and a recipe matches when every ingredient can be paired with a
 * distinct input. Maximum matching is polynomial, so the lookup always finishes.
 * <p>
 * The returned array keeps Forge's layout: {@code result[inputIndex] = ingredientIndex}. Callers only use it to
 * decide whether a pairing exists, and any perfect matching is a valid answer, so the change is not observable to
 * recipe matching.
 */
public final class FastRecipeMatcher {

    private FastRecipeMatcher() {
    }

    /**
     * @return an input-to-ingredient mapping, or null when the inputs and ingredients cannot be paired one to one
     */
    public static int[] findMatches(List<ItemStack> inputs, List<Ingredient> ingredients) {

        final int size = inputs.size();
        if (size != ingredients.size()) {
            return null;
        }
        if (size == 0) {
            return new int[0];
        }

        final int[][] candidates = new int[size][];
        final boolean[] reachableInput = new boolean[size];

        for (int ingredientIndex = 0; ingredientIndex < size; ingredientIndex++) {
            final Ingredient ingredient = ingredients.get(ingredientIndex);
            final int[] row = new int[size];
            int count = 0;

            for (int inputIndex = 0; inputIndex < size; inputIndex++) {
                if (ingredient.test(inputs.get(inputIndex))) {
                    row[count++] = inputIndex;
                    reachableInput[inputIndex] = true;
                }
            }

            if (count == 0) {
                return null;
            }

            candidates[ingredientIndex] = count == size ? row : Arrays.copyOf(row, count);
        }

        for (boolean reachable : reachableInput) {
            if (!reachable) {
                return null;
            }
        }

        final int[] inputToIngredient = new int[size];
        Arrays.fill(inputToIngredient, -1);

        for (int ingredientIndex = 0; ingredientIndex < size; ingredientIndex++) {
            if (!assign(ingredientIndex, candidates, inputToIngredient, new boolean[size])) {
                return null;
            }
        }

        return inputToIngredient;
    }

    private static boolean assign(
            int ingredientIndex, int[][] candidates, int[] inputToIngredient, boolean[] visited
    ) {

        for (int inputIndex : candidates[ingredientIndex]) {
            if (visited[inputIndex]) {
                continue;
            }
            visited[inputIndex] = true;

            final int previousIngredient = inputToIngredient[inputIndex];
            if (previousIngredient < 0 || assign(previousIngredient, candidates, inputToIngredient, visited)) {
                inputToIngredient[inputIndex] = ingredientIndex;
                return true;
            }
        }

        return false;
    }
}
