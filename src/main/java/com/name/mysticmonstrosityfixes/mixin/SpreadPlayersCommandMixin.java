package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.name.mysticmonstrosityfixes.util.SpreadPosition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(SpreadPlayersCommand.class)
public class SpreadPlayersCommandMixin {

    @Shadow @Final
    private static Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType((p_201854_, p_201855_) -> Component.translatable("commands.spreadplayers.failed.invalid.height", p_201854_, p_201855_));

    @Inject(method = "spreadPlayers", at = @At("HEAD"), cancellable = true)
    private static void spreadPlayersOptimized(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxY, boolean respectTeams, Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        int minY = level.getMinBuildHeight();
        if (maxY < minY) {
            throw ERROR_INVALID_MAX_HEIGHT.create(maxY, minY);
        }

        int count = respectTeams ? mystics_monstrosity_fixes$getNumberOfTeams(targets) : targets.size();
        SpreadPosition[] positions = mystics_monstrosity_fixes$spreadPositionsGridBased(center, count, spreadDistance, maxRange);

        double avgMinDist = mystics_monstrosity_fixes$setPlayerPositions(targets, level, positions, maxY, respectTeams);

        source.sendSuccess(() -> Component.translatable("commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"),
                positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", avgMinDist)), true);

        cir.setReturnValue(positions.length);
    }

    @Unique
    private static int mystics_monstrosity_fixes$getNumberOfTeams(Collection<? extends Entity> entities) {
        Set<Team> teams = new HashSet<>();
        for (Entity e : entities) {
            if (e instanceof Player) {
                teams.add(e.getTeam());
            } else {
                teams.add(null);
            }
        }
        return teams.size();
    }

    @Unique
    private static SpreadPosition[] mystics_monstrosity_fixes$spreadPositionsGridBased(Vec2 center, int count, float spreadDistance, float maxRange) {
        SpreadPosition[] positions = new SpreadPosition[count];
        int gridWidth = (int) Math.ceil(Math.sqrt(count));
        int gridHeight = (int) Math.ceil((double) count / gridWidth);

        double totalWidth = (gridWidth - 1) * spreadDistance;
        double totalHeight = (gridHeight - 1) * spreadDistance;

        double startX = center.x - totalWidth / 2.0;
        double startZ = center.y - totalHeight / 2.0;

        for (int i = 0; i < count; i++) {
            int row = i / gridWidth;
            int col = i % gridWidth;

            double x = startX + col * spreadDistance;
            double z = startZ + row * spreadDistance;

            double dx = x - center.x;
            double dz = z - center.y;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > maxRange) {
                double scale = maxRange / dist;
                x = center.x + dx * scale;
                z = center.y + dz * scale;
            }

            SpreadPosition pos = new SpreadPosition();
            pos.x = x;
            pos.z = z;
            positions[i] = pos;
        }

        return positions;
    }

    @Unique
    private static double mystics_monstrosity_fixes$setPlayerPositions(Collection<? extends Entity> entities, ServerLevel level, SpreadPosition[] positions, int maxY, boolean respectTeams) {
        double totalMinDist = 0.0;
        AtomicInteger index = new AtomicInteger();
        Map<Team, SpreadPosition> teamMap = new HashMap<>();

        for (Entity entity : entities) {
            SpreadPosition pos;
            if (respectTeams) {
                Team team = (entity instanceof Player) ? entity.getTeam() : null;
                pos = teamMap.computeIfAbsent(team, t -> positions[index.getAndIncrement()]);
            } else {
                pos = positions[index.getAndIncrement()];
            }

            int spawnY = pos.getSpawnY(level, maxY);
            double x = Mth.floor(pos.x) + 0.5;
            double z = Mth.floor(pos.z) + 0.5;

            var event = net.minecraftforge.event.ForgeEventFactory.onEntityTeleportSpreadPlayersCommand(entity, x, spawnY, z);
            if (!event.isCanceled()) {
                entity.teleportToWithTicket(event.getTargetX(), event.getTargetY(), event.getTargetZ());
            }

            double minDist = Double.MAX_VALUE;
            for (SpreadPosition other : positions) {
                if (other != pos) {
                    double dist = pos.dist(other);
                    minDist = Math.min(minDist, dist);
                }
            }

            totalMinDist += minDist;
        }

        return entities.size() < 2 ? 0.0 : totalMinDist / entities.size();
    }
}

