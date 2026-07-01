package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.keyframehandler.AutoPlayingSoundKeyframeHandler;
import com.geckolib.util.GeckoLibUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrossroadCrystalBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int VISUAL_MODE_EVENT = 1;
    public static final String CONTROLLER = "crystal";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation REFORM = RawAnimation.begin().thenPlay("reform");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlayAndHold("slam");
    private static final int HOLD_AFTER_SLAM_TICKS = 6;
    private static final int FADE_OUT_TICKS = 14;
    private static final AnimationTimings ANIMATION_TIMINGS = AnimationTimings.load();
    private static final RealmCrystalManager CRYSTAL_MANAGER = new RealmCrystalManager();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private VisualMode visualMode = VisualMode.IDLE;
    private SequencePhase sequencePhase = SequencePhase.IDLE;
    private long visualStartGameTime = Long.MIN_VALUE;
    private long phaseStartGameTime = Long.MIN_VALUE;
    private boolean slamCompletionNotified = false;

    public CrossroadCrystalBlockEntity(BlockPos pos, BlockState blockState) {
        super(CrossroadDimension.CROSSROADS_CRYSTAL_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void playSlam() {
        setVisualMode(VisualMode.SLAM);
        beginPhase(SequencePhase.SLAMMING);
    }

    public void playReformToIdle() {
        setVisualMode(VisualMode.REFORM);
        beginPhase(SequencePhase.REFORMING);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrossroadCrystalBlockEntity crystal) {
        crystal.tickSequence(level);
    }

    public float renderAlpha(float partialTick) {
        if (visualMode == VisualMode.REFORM) {
            return clamp(visualAge(partialTick) / ANIMATION_TIMINGS.reformTicks());
        }

        if (visualMode == VisualMode.SLAM) {
            float age = visualAge(partialTick);
            int fadeStart = ANIMATION_TIMINGS.slamTicks() + HOLD_AFTER_SLAM_TICKS;
            if (age <= fadeStart) {
                return 1.0F;
            }
            return 1.0F - clamp((age - fadeStart) / FADE_OUT_TICKS);
        }

        return 1.0F;
    }

    private void tickSequence(Level level) {
        switch (sequencePhase) {
            case SLAMMING -> {
                if (!slamCompletionNotified && phaseAge(level) >= ANIMATION_TIMINGS.slamTicks()) {
                    slamCompletionNotified = true;
                    beginPhase(SequencePhase.SLAM_HELD);
                    if (level instanceof ServerLevel serverLevel) {
                        CRYSTAL_MANAGER.handleCrystalSlamComplete(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), worldPosition));
                    }
                }
            }
            case SLAM_HELD -> {
                if (phaseAge(level) >= HOLD_AFTER_SLAM_TICKS + FADE_OUT_TICKS) {
                    setVisualMode(VisualMode.IDLE);
                    beginPhase(SequencePhase.IDLE);
                }
            }
            case REFORMING -> {
                if (phaseAge(level) >= ANIMATION_TIMINGS.reformTicks()) {
                    setVisualMode(VisualMode.IDLE);
                    beginPhase(SequencePhase.IDLE);
                }
            }
            case IDLE -> {
            }
        }
    }

    private float visualAge(float partialTick) {
        if (level == null) {
            return 0.0F;
        }
        if (visualStartGameTime == Long.MIN_VALUE) {
            visualStartGameTime = level.getGameTime();
        }
        return Math.max(0.0F, level.getGameTime() - visualStartGameTime + partialTick);
    }

    private long phaseAge(Level level) {
        if (phaseStartGameTime == Long.MIN_VALUE) {
            phaseStartGameTime = level.getGameTime();
        }
        return Math.max(0L, level.getGameTime() - phaseStartGameTime);
    }

    private void setVisualMode(VisualMode mode) {
        visualMode = mode;
        visualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), VISUAL_MODE_EVENT, mode.ordinal());
        }
    }

    private void beginPhase(SequencePhase phase) {
        sequencePhase = phase;
        phaseStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (phase == SequencePhase.SLAMMING) {
            slamCompletionNotified = false;
        }
    }

    @Override
    public boolean triggerEvent(int eventId, int eventData) {
        if (eventId == VISUAL_MODE_EVENT) {
            VisualMode[] modes = VisualMode.values();
            if (eventData >= 0 && eventData < modes.length) {
                visualMode = modes[eventData];
                beginPhase(syncedPhaseFor(visualMode));
                return true;
            }
        }
        return super.triggerEvent(eventId, eventData);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CrossroadCrystalBlockEntity>(CONTROLLER, 0, state -> switch (sequencePhase) {
            case SLAMMING, SLAM_HELD -> state.setAndContinue(SLAM);
            case REFORMING -> state.setAndContinue(REFORM);
            case IDLE -> state.setAndContinue(IDLE);
        }).setTransitionTicks(0).setSoundKeyframeHandler(new AutoPlayingSoundKeyframeHandler<>()));
    }

    private SequencePhase syncedPhaseFor(VisualMode mode) {
        return switch (mode) {
            case IDLE -> SequencePhase.IDLE;
            case SLAM -> SequencePhase.SLAMMING;
            case REFORM -> SequencePhase.REFORMING;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private enum VisualMode {
        IDLE,
        SLAM,
        REFORM
    }

    private enum SequencePhase {
        IDLE,
        SLAMMING,
        SLAM_HELD,
        REFORMING
    }

    private record AnimationTimings(int reformTicks, int slamTicks) {
        private static AnimationTimings load() {
            try (InputStream stream = openAnimationResource()) {
                if (stream != null) {
                    JsonObject animations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject()
                            .getAsJsonObject("animations");
                    return new AnimationTimings(
                            secondsToTicks(animations.getAsJsonObject("reform").get("animation_length").getAsDouble()),
                            secondsToTicks(animations.getAsJsonObject("slam").get("animation_length").getAsDouble())
                    );
                }
            } catch (RuntimeException | java.io.IOException exception) {
                CrossroadDimension.LOGGER.warn("Unable to load crystal animation timings from exported animation json", exception);
            }

            throw new IllegalStateException("Unable to load Crossroads crystal animation timings from exported animation json");
        }

        private static InputStream openAnimationResource() {
            InputStream stream = CrossroadCrystalBlockEntity.class.getResourceAsStream("/assets/" + CrossroadDimension.MODID + "/animations/crossroad_crystal.animation.json");
            if (stream != null) {
                return stream;
            }
            return CrossroadCrystalBlockEntity.class.getResourceAsStream("/assets/" + CrossroadDimension.MODID + "/geckolib/animations/crossroad_crystal.animation.json");
        }

        private static int secondsToTicks(double seconds) {
            return Math.max(1, (int) Math.round(seconds * 20.0D));
        }
    }
}
