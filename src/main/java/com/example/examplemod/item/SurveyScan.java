package com.example.examplemod.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.example.examplemod.CrossroadDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SurveyScan {
    public static final int RANGE = 32;
    public static final int MAX_RAW_RESULTS = 256;
    public static final int MAX_DISPLAYED_SOURCES = 3;
    private static final int RANGE_SQUARED = RANGE * RANGE;
    private static final int FADE_IN_TICKS = 10;
    private static final int HOVER_TICKS = 10;
    private static final int FADE_OUT_TICKS = 10;
    private static final double BREADCRUMB_SPEED = 0.11;
    private static final double ARRIVAL_DISTANCE_SQUARED = 0.3 * 0.3;
    private static final double BOB_HEIGHT = 0.06;
    private static final double BOB_SPEED = 0.22;
    private static final int COLLISION_STEPS = 4;

    private SurveyScan() {
    }

    public static List<Source> findSources(ServerLevel level, ServerPlayer player, TagKey<Block> targetTag) {
        Vec3 playerPosition = player.position();
        Set<BlockPos> ores = findTargetBlocks(level, player.blockPosition(), targetTag);
        if (ores.isEmpty()) {
            return List.of();
        }

        List<Source> sources = groupIntoSources(ores, playerPosition);
        sources.sort(Comparator.comparingDouble(Source::distanceSquared)
                .thenComparingLong(source -> source.target().asLong()));
        return List.copyOf(sources.subList(0, Math.min(MAX_DISPLAYED_SOURCES, sources.size())));
    }

    public static Breadcrumb createBreadcrumb(ServerPlayer player, Source source, SurveyScopeItem scope) {
        Vec3 position = player.getEyePosition();
        Vec3 towardSource = source.target().getCenter().subtract(position);
        if (towardSource.lengthSqr() < 0.0001) {
            return null;
        }

        return new Breadcrumb(
            position,
            towardSource.normalize().scale(BREADCRUMB_SPEED),
            source.target().getCenter(),
            scope.getRed(),
            scope.getGreen(),
            scope.getBlue()
        );
    }

    public static boolean tickBreadcrumb(ServerLevel level, ServerPlayer player, Breadcrumb breadcrumb) {
        return switch (breadcrumb.phase) {
            case TRAVELLING -> tickTravellingWisp(level, player, breadcrumb);
            case HOVERING -> tickHoveringWisp(level, player, breadcrumb);
            case FADING_OUT -> tickFadingWisp(level, player, breadcrumb);
        };
    }

    private static boolean tickTravellingWisp(ServerLevel level, ServerPlayer player, Breadcrumb breadcrumb) {
        Vec3 currentVisualPosition = breadcrumb.visualPosition();
        renderWisp(level, player, breadcrumb, currentVisualPosition);

        Vec3 nextPosition = breadcrumb.position.add(breadcrumb.velocity);
        double nextBob = Math.sin((breadcrumb.travelTicks + 1) * BOB_SPEED) * BOB_HEIGHT;
        Vec3 nextVisualPosition = nextPosition.add(0.0, nextBob, 0.0);
        Vec3 collisionPoint = findCollisionPoint(level, currentVisualPosition,
                nextVisualPosition.subtract(currentVisualPosition));
        if (collisionPoint != null) {
            breadcrumb.beginHover(collisionPoint);
            return true;
        }

        breadcrumb.position = nextPosition;
        breadcrumb.travelTicks++;
        if (nextPosition.distanceToSqr(breadcrumb.target) <= ARRIVAL_DISTANCE_SQUARED) {
            breadcrumb.beginHover(nextVisualPosition);
        }
        return true;
    }

    private static boolean tickHoveringWisp(ServerLevel level, ServerPlayer player, Breadcrumb breadcrumb) {
        renderWisp(level, player, breadcrumb, breadcrumb.position);
        breadcrumb.phaseTicks++;
        if (breadcrumb.phaseTicks >= HOVER_TICKS) {
            breadcrumb.phase = BreadcrumbPhase.FADING_OUT;
            breadcrumb.phaseTicks = 0;
        }
        return true;
    }

    private static boolean tickFadingWisp(ServerLevel level, ServerPlayer player, Breadcrumb breadcrumb) {
        renderWisp(level, player, breadcrumb, breadcrumb.position);
        breadcrumb.phaseTicks++;
        return breadcrumb.phaseTicks < FADE_OUT_TICKS;
    }

    private static void renderWisp(ServerLevel level, ServerPlayer player, Breadcrumb breadcrumb, Vec3 position) {
        float size = 0.18F;
        float opacity = 0.65F;
        if (breadcrumb.phase == BreadcrumbPhase.TRAVELLING && breadcrumb.travelTicks < FADE_IN_TICKS) {
            float progress = (breadcrumb.travelTicks + 1) / (float) FADE_IN_TICKS;
            size = Mth.lerp(progress, 0.03F, 0.18F);
            opacity = Mth.lerp(progress, 0.08F, 0.65F);
        } else if (breadcrumb.phase == BreadcrumbPhase.HOVERING) {
            float progress = breadcrumb.phaseTicks / (float) HOVER_TICKS;
            size = Mth.lerp(progress, 0.18F, 0.22F);
            opacity = Mth.lerp(progress, 0.65F, 0.82F);
        } else if (breadcrumb.phase == BreadcrumbPhase.FADING_OUT) {
            float progress = breadcrumb.phaseTicks / (float) FADE_OUT_TICKS;
            size = Mth.lerp(progress, 0.22F, 0.03F);
            opacity = Mth.lerp(progress, 0.82F, 0.0F);
        }

        level.sendParticles(player, CrossroadDimension.SURVEY_WISP_PARTICLE.get(),
                false, true, position.x, position.y, position.z, 0,
                size, opacity, 0.0, 1.0);
    }

    private static Set<BlockPos> findTargetBlocks(ServerLevel level, BlockPos center, TagKey<Block> targetTag) {
        Comparator<BlockPos> nearestFirst = Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
                .thenComparingLong(BlockPos::asLong);
        PriorityQueue<BlockPos> nearestOres = new PriorityQueue<>(MAX_RAW_RESULTS, nearestFirst.reversed());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int xOffset = -RANGE; xOffset <= RANGE; xOffset++) {
            int xSquared = xOffset * xOffset;
            int blockX = center.getX() + xOffset;

            for (int zOffset = -RANGE; zOffset <= RANGE; zOffset++) {
                int horizontalDistanceSquared = xSquared + zOffset * zOffset;
                if (horizontalDistanceSquared > RANGE_SQUARED) {
                    continue;
                }

                int blockZ = center.getZ() + zOffset;
                if (!level.hasChunk(SectionPos.blockToSectionCoord(blockX),
                        SectionPos.blockToSectionCoord(blockZ))) {
                    continue;
                }

                int verticalRadius = (int) Math.sqrt(RANGE_SQUARED - horizontalDistanceSquared);
                int minY = Math.max(level.getMinY(), center.getY() - verticalRadius);
                int maxY = Math.min(level.getMaxY(), center.getY() + verticalRadius);

                for (int blockY = minY; blockY <= maxY; blockY++) {
                    cursor.set(blockX, blockY, blockZ);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(targetTag)) {
                        continue;
                    }

                    BlockPos ore = cursor.immutable();
                    if (nearestOres.size() < MAX_RAW_RESULTS) {
                        nearestOres.add(ore);
                    } else if (nearestFirst.compare(ore, nearestOres.peek()) < 0) {
                        nearestOres.poll();
                        nearestOres.add(ore);
                    }
                }
            }
        }

        return new HashSet<>(nearestOres);
    }

    private static List<Source> groupIntoSources(Set<BlockPos> ores, Vec3 playerPosition) {
        Set<BlockPos> ungrouped = new HashSet<>(ores);
        List<Source> sources = new ArrayList<>();

        while (!ungrouped.isEmpty()) {
            BlockPos first = ungrouped.iterator().next();
            ungrouped.remove(first);
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            open.add(first);

            BlockPos closest = first;
            BlockPos anchor = first;
            double closestDistance = playerPosition.distanceToSqr(first.getCenter());
            int blockCount = 0;

            while (!open.isEmpty()) {
                BlockPos current = open.removeFirst();
                blockCount++;

                double currentDistance = playerPosition.distanceToSqr(current.getCenter());
                if (currentDistance < closestDistance) {
                    closest = current;
                    closestDistance = currentDistance;
                }
                if (current.asLong() < anchor.asLong()) {
                    anchor = current;
                }

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) {
                                continue;
                            }

                            BlockPos neighbor = current.offset(x, y, z);
                            if (ungrouped.remove(neighbor)) {
                                open.addLast(neighbor);
                            }
                        }
                    }
                }
            }

            sources.add(new Source(closest.immutable(), anchor.immutable(), blockCount, closestDistance));
        }

        return sources;
    }

    private static Vec3 findCollisionPoint(ServerLevel level, Vec3 position, Vec3 velocity) {
        Vec3 lastPassable = position;
        for (int step = 1; step <= COLLISION_STEPS; step++) {
            Vec3 sample = position.add(velocity.scale(step / (double) COLLISION_STEPS));
            BlockPos pointPos = BlockPos.containing(sample);
            BlockState state = level.getBlockState(pointPos);
            if (!state.isAir() && !state.is(Blocks.WATER)
                    && !state.getCollisionShape(level, pointPos).isEmpty()) {
                return lastPassable;
            }
            lastPassable = sample;
        }
        return null;
    }

    public record Source(BlockPos target, BlockPos anchor, int blockCount, double distanceSquared) {
    }

    public static final class Breadcrumb {
        private Vec3 position;
        private final Vec3 velocity;
        private final Vec3 target;
        private final float red;
        private final float green;
        private final float blue;
        private BreadcrumbPhase phase = BreadcrumbPhase.TRAVELLING;
        private int travelTicks;
        private int phaseTicks;

        private Breadcrumb(Vec3 position, Vec3 velocity, Vec3 target,
                   float red, float green, float blue) {
            this.position = position;
            this.velocity = velocity;
            this.target = target;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private Vec3 visualPosition() {
            return this.position.add(0.0, Math.sin(this.travelTicks * BOB_SPEED) * BOB_HEIGHT, 0.0);
        }

        private void beginHover(Vec3 hoverPosition) {
            this.position = hoverPosition;
            this.phase = BreadcrumbPhase.HOVERING;
            this.phaseTicks = 0;
        }
    }

    private enum BreadcrumbPhase {
        TRAVELLING,
        HOVERING,
        FADING_OUT
    }
}
