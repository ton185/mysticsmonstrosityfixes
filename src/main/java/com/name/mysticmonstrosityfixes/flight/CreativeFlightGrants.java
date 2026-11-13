package com.name.mysticmonstrosityfixes.flight;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps creative flight that FTB Essentials, a flight effect, or Apothic Attributes already granted.
 * Several mods in this pack write {@code mayfly} back to false during the player tick.
 */
public final class CreativeFlightGrants {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float VANILLA_FLYING_SPEED = 0.05F;
    private static final ResourceLocation CREATIVE_FLIGHT = new ResourceLocation("attributeslib", "creative_flight");

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static Attribute creativeFlight;
    private static boolean creativeFlightLookupDone;
    private static Method ftbGetOrCreate;
    private static Method ftbCanFly;
    private static boolean ftbLookupDone;
    private static boolean loggedFtbFailure;

    private CreativeFlightGrants() {
    }

    public static boolean granted(Player player) {
        return hasModGrant(player);
    }

    public static void keep(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean creative = player.isCreative() || player.isSpectator();
        boolean granted = granted(player);
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        boolean changed = false;

        if (granted) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                changed = true;
            }
            if (abilities.getFlyingSpeed() <= 0.0F) {
                abilities.setFlyingSpeed(VANILLA_FLYING_SPEED);
                changed = true;
            }
            if (!state.granted && !state.toggledThisTick) {
                state.flying = true;
            }
            if (state.flying && !abilities.flying) {
                abilities.flying = true;
                changed = true;
            }
        } else if (creative) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                changed = true;
            }
        } else if (state.granted) {
            if (abilities.mayfly || abilities.flying) {
                abilities.mayfly = false;
                abilities.flying = false;
                changed = true;
            }
            state.flying = false;
        }

        state.granted = granted;
        state.toggledThisTick = false;
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /**
     * The move packet is handled before the player tick, which is where {@link #keep} puts flight back.
     * A granted player who is already flying has to count as flying for that check, or a hover is treated
     * as a fall.
     */
    public static void allowMovement(ServerPlayer player) {
        boolean creative = player.isCreative() || player.isSpectator();
        boolean granted = granted(player);
        if (!creative && !granted) {
            return;
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.mayfly) {
            abilities.mayfly = true;
        }
        State state = STATES.get(player.getUUID());
        if (granted && state != null && state.flying && !abilities.flying) {
            abilities.flying = true;
        }
    }

    public static void rememberToggle(ServerPlayer player, boolean flying) {
        if (!granted(player) && !player.isCreative() && !player.isSpectator()) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        state.flying = flying;
        state.clientMayfly = player.getAbilities().mayfly || flying;
        state.clientKnown = true;
        state.toggledThisTick = true;
    }

    /**
     * The client copy of the abilities packet, or the local toggle that sends one.
     * Other mods clear {@code flying} again before movement when jump is not held.
     */
    public static void rememberClient(Player player, boolean mayfly, boolean flying) {
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        state.clientMayfly = mayfly;
        state.flying = flying;
        state.clientKnown = true;
    }

    /**
     * Granted creative flight should hover the way vanilla creative flight does.
     * Returns true when this client player is in that flight and it should be put back before movement.
     */
    public static boolean hover(Player player) {
        if (player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
            return false;
        }
        if (!player.level().isClientSide() || !player.isControlledByLocalInstance()) {
            return false;
        }
        boolean granted = hasModGrant(player);
        if (player.isCreative() && !granted) {
            return false;
        }
        Abilities abilities = player.getAbilities();
        State state = STATES.get(player.getUUID());
        boolean mayfly = abilities.mayfly || granted || (state != null && state.clientMayfly);
        if (!mayfly) {
            return false;
        }
        if (state != null && (state.clientKnown || state.granted || granted)) {
            return state.flying;
        }
        return abilities.flying;
    }

    public static void restoreHover(Player player) {
        if (!hover(player)) {
            return;
        }
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        if (abilities.getFlyingSpeed() <= 0.0F) {
            abilities.setFlyingSpeed(VANILLA_FLYING_SPEED);
        }
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    private static boolean hasModGrant(Player player) {
        return creativeFlight(player) > 0.0D || ftbCanFly(player) || hasFlightEffect(player);
    }

    private static double creativeFlight(Player player) {
        Attribute attribute = creativeFlightAttribute();
        if (attribute == null) {
            return 0.0D;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private static Attribute creativeFlightAttribute() {
        if (creativeFlightLookupDone) {
            return creativeFlight;
        }
        creativeFlight = ForgeRegistries.ATTRIBUTES.getValue(CREATIVE_FLIGHT);
        if (creativeFlight != null) {
            creativeFlightLookupDone = true;
        }
        return creativeFlight;
    }

    private static boolean ftbCanFly(Player player) {
        if (!lookupFtb()) {
            return false;
        }
        try {
            Object data = ftbGetOrCreate.invoke(null, player);
            if (data instanceof Optional<?> optional && optional.isPresent()) {
                return Boolean.TRUE.equals(ftbCanFly.invoke(optional.get()));
            }
        } catch (ReflectiveOperationException exception) {
            if (!loggedFtbFailure) {
                loggedFtbFailure = true;
                LOGGER.warn("Could not read FTB Essentials flight", exception);
            }
        }
        return false;
    }

    private static boolean lookupFtb() {
        if (ftbLookupDone) {
            return ftbGetOrCreate != null;
        }
        ftbLookupDone = true;
        try {
            Class<?> type = Class.forName(
                    "dev.ftb.mods.ftbessentials.util.FTBEPlayerData",
                    false,
                    CreativeFlightGrants.class.getClassLoader());
            ftbGetOrCreate = type.getMethod("getOrCreate", Player.class);
            ftbCanFly = type.getMethod("canFly");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Could not look up FTB Essentials flight", exception);
            return false;
        }
    }

    private static boolean hasFlightEffect(Player player) {
        Attribute creativeFlightAttribute = creativeFlightAttribute();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (creativeFlightAttribute != null && effect.getAttributeModifiers().containsKey(creativeFlightAttribute)) {
                return true;
            }
            if (namedFlight(effect)) {
                return true;
            }
        }
        return false;
    }

    private static boolean namedFlight(MobEffect effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id != null && flightName(id.getPath())) {
            return true;
        }
        return flightName(effect.getClass().getSimpleName());
    }

    private static boolean flightName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("slow_fall") || lower.contains("firefly") || lower.contains("dragonfly") || lower.contains("butterfly")) {
            return false;
        }
        return lower.contains("flight") || lower.contains("flying") || lower.equals("fly");
    }

    private static final class State {
        private boolean granted;
        private boolean flying;
        private boolean toggledThisTick;
        private boolean clientMayfly;
        private boolean clientKnown;
    }
}
