package com.name.mysticmonstrosityfixes.placeable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Calls Placeable without a compile dependency. The lookup is deferred until a block actually
 * checks placement, which is after mods have finished constructing.
 */
public final class PlaceableBridge {
    private static final int UNKNOWN = 0;
    private static final int ABSENT = 1;
    private static final int PRESENT = 2;

    private static volatile int state = UNKNOWN;
    private static MethodHandle shouldSkip;
    private static MethodHandle validFloor;
    private static MethodHandle validFloorState;
    private static MethodHandle additionalPlant;

    private PlaceableBridge() {
    }

    public static boolean loaded() {
        resolve();
        return state == PRESENT;
    }

    public static boolean shouldSkip(BlockState state, BlockGetter level) {
        try {
            return (boolean) shouldSkip.invokeExact(state, level);
        } catch (Throwable thrown) {
            throw new IllegalStateException("Placeable.shouldSkipExpandedPlacement failed", thrown);
        }
    }

    public static boolean isValidFloor(LevelReader level, BlockPos pos) {
        try {
            return (boolean) validFloor.invokeExact(level, pos);
        } catch (Throwable thrown) {
            throw new IllegalStateException("Placeable.isValidFloor failed", thrown);
        }
    }

    public static boolean isValidFloor(BlockState state, BlockGetter level, BlockPos pos) {
        try {
            return (boolean) validFloorState.invokeExact(state, level, pos);
        } catch (Throwable thrown) {
            throw new IllegalStateException("Placeable.isValidFloor failed", thrown);
        }
    }

    public static boolean isAdditionalPlant(BlockState state) {
        try {
            return (boolean) additionalPlant.invokeExact(state);
        } catch (Throwable thrown) {
            throw new IllegalStateException("PlaceableConfig.isAdditionalPlaceablePlant failed", thrown);
        }
    }

    private static void resolve() {
        if (state != UNKNOWN) {
            return;
        }
        synchronized (PlaceableBridge.class) {
            if (state != UNKNOWN) {
                return;
            }
            try {
                Class<?> placeable = Class.forName("it.bisumto.placeable.Placeable");
                Class<?> config = Class.forName("it.bisumto.placeable.PlaceableConfig");
                shouldSkip = handle(placeable, "shouldSkipExpandedPlacement", BlockState.class, BlockGetter.class);
                validFloor = handle(placeable, "isValidFloor", LevelReader.class, BlockPos.class);
                validFloorState = handle(placeable, "isValidFloor", BlockState.class, BlockGetter.class, BlockPos.class);
                additionalPlant = handle(config, "isAdditionalPlaceablePlant", BlockState.class);
                state = PRESENT;
            } catch (ReflectiveOperationException exception) {
                state = ABSENT;
            }
        }
    }

    private static MethodHandle handle(Class<?> owner, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameters);
        return MethodHandles.publicLookup().unreflect(method);
    }
}
