package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrossroadCrystalBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int VISUAL_MODE_EVENT = 1;
    public static final int PORTAL_VISUAL_EVENT = 2;
    public static final String CONTROLLER = "crystal";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation REFORM = RawAnimation.begin().thenPlay("reform");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlayAndHold("slam");
    private static final int IDLE_OPENING_LOOPS = 1;
    private static final int HOLD_AFTER_SLAM_TICKS = 6;
    private static final int FADE_OUT_TICKS = 14;
    private static final int PORTAL_SCALE_TICKS = 8;
    private static final float PORTAL_SPIN_DEGREES_PER_TICK = 0.01875F;
    private static final float PORTAL_SCROLL_PER_TICK = 0.0002F;
    private static final float PORTAL_SWIRL_RADIANS_PER_TICK = (float) (Math.PI * 2.0D / 500.0D);
    private static final long PORTAL_VISUAL_SYNC_INTERVAL_TICKS = 20L;
    private static final AnimationTimings ANIMATION_TIMINGS = AnimationTimings.load();
    private static final RealmPortalManager PORTAL_MANAGER = new RealmPortalManager();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long visualStartGameTime = Long.MIN_VALUE;
    private long phaseStartGameTime = Long.MIN_VALUE;
    private long portalVisualStartGameTime = Long.MIN_VALUE;
    private String currentAnimation = "none";
    private VisualMode visualMode = VisualMode.HIDDEN;
    private PortalVisualMode portalVisualMode = PortalVisualMode.HIDDEN;
    private SequencePhase sequencePhase = SequencePhase.NONE;
    private long lastPortalVisualSyncGameTime = Long.MIN_VALUE;
    private boolean slamCompletionNotified = false;
    private boolean reformCompletionNotified = false;

    public CrossroadCrystalBlockEntity(BlockPos pos, BlockState blockState) {
        super(CrossroadDimension.CROSSROADS_GATE_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void playSlam() {
        if (visualMode == VisualMode.SEALED_IDLE) {
            setVisualMode(VisualMode.SEALED_OPENING, "slam");
        } else {
            currentAnimation = "slam";
            visualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        }
        beginPhase(SequencePhase.SLAMMING, "slam");
    }

    public void playOpeningSequence() {
        setVisualMode(VisualMode.TEMPORARY_OPENING, "opening");
        beginPhase(SequencePhase.OPENING_REFORM, "reform");
    }

    public void playReformToIdle() {
        setVisualMode(VisualMode.SEALED_REFORM, "reform");
        beginPhase(SequencePhase.REFORMING_TO_IDLE, "reform");
    }

    public void openPortalVisual() {
        setPortalVisualMode(PortalVisualMode.OPENING);
    }

    public void closePortalVisual() {
        setPortalVisualMode(PortalVisualMode.CLOSING);
    }

    public void hidePortalVisual() {
        setPortalVisualMode(PortalVisualMode.HIDDEN);
    }

    public boolean isPortalVisualHidden() {
        return portalVisualMode == PortalVisualMode.HIDDEN;
    }

    public boolean isPortalVisualVisible() {
        return portalVisualMode != PortalVisualMode.HIDDEN;
    }

    public boolean isPortalVisualClosing() {
        return portalVisualMode == PortalVisualMode.CLOSING;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrossroadCrystalBlockEntity crystal) {
        crystal.tickSequence(level);
    }

    public float renderAlpha(float partialTick) {
        if (visualMode == VisualMode.HIDDEN) {
            return 0.0F;
        }

        if (visualMode == VisualMode.SEALED_IDLE) {
            return 1.0F;
        }

        if (visualMode == VisualMode.SEALED_REFORM || isReformVisual()) {
            return clamp(visualAge(partialTick) / ANIMATION_TIMINGS.reformTicks());
        }

        if (!isSlamVisual()) {
            return 1.0F;
        }

        float age = visualAge(partialTick);
        int fadeStart = slamFadeStartTicks();
        if (age <= fadeStart) {
            return 1.0F;
        }

        return 1.0F - clamp((age - fadeStart) / FADE_OUT_TICKS);
    }

    public float portalScale(float partialTick) {
        return switch (portalVisualMode) {
            case HIDDEN -> 0.0F;
            case OPENING -> clamp(portalVisualAge(partialTick) / PORTAL_SCALE_TICKS);
            case OPEN -> 1.0F;
            case CLOSING -> 1.0F - clamp(portalVisualAge(partialTick) / PORTAL_SCALE_TICKS);
        };
    }

    public float portalAlpha(float partialTick) {
        return portalScale(partialTick);
    }

    public float portalScroll(float partialTick) {
        if (level == null) {
            return 0.0F;
        }
        return ((level.getGameTime() + partialTick) * PORTAL_SCROLL_PER_TICK) % 1.0F;
    }

    public float portalSpin(float partialTick) {
        if (level == null) {
            return 0.0F;
        }
        return ((level.getGameTime() + partialTick) * PORTAL_SPIN_DEGREES_PER_TICK) % 360.0F;
    }

    public float portalSwirl(float partialTick) {
        if (level == null) {
            return 0.0F;
        }
        return ((level.getGameTime() + partialTick) * PORTAL_SWIRL_RADIANS_PER_TICK) % ((float) Math.PI * 2.0F);
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

    private boolean isInsidePocketRealm() {
        if (level == null) {
            return false;
        }

        Identifier dimensionId = level.dimension().identifier();
        return dimensionId.getNamespace().equals(CrossroadDimension.MODID)
                && dimensionId.getPath().startsWith("pocket_realm/");
    }

    private boolean isSlamVisual() {
        return currentAnimation.equals("slam") || currentAnimation.equals("slam_hold") || currentAnimation.equals("sealed_slam");
    }

    private boolean isReformVisual() {
        return currentAnimation.equals("reform");
    }

    private void tickSequence(Level level) {
        syncPortalVisualState(level);
        tickPortalVisual(level);
        if (sequencePhase == SequencePhase.NONE) {
            return;
        }

        long phaseAge = phaseAge(level);
        long sequenceAge = sequenceAge(level);
        switch (sequencePhase) {
            case OPENING_REFORM -> {
                if (sequenceAge >= ANIMATION_TIMINGS.reformTicks()) {
                    beginPhase(SequencePhase.OPENING_IDLE, "idle");
                }
            }
            case OPENING_IDLE -> {
                long idleTicks = sequenceAge - ANIMATION_TIMINGS.reformTicks();
                long completedLoops = idleTicks / ANIMATION_TIMINGS.idleTicks();
                if (completedLoops >= IDLE_OPENING_LOOPS) {
                    playSlam();
                }
            }
            case SLAMMING -> {
                if (!slamCompletionNotified && phaseAge >= ANIMATION_TIMINGS.slamTicks()) {
                    slamCompletionNotified = true;
                    beginPhase(SequencePhase.SLAM_HELD, "slam_hold");
                    if (level instanceof ServerLevel serverLevel) {
                        PORTAL_MANAGER.handleCrystalSlamComplete(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), worldPosition));
                    }
                }
            }
            case REFORMING_TO_IDLE -> {
                if (!reformCompletionNotified && phaseAge >= ANIMATION_TIMINGS.reformTicks()) {
                    reformCompletionNotified = true;
                    if (level.isClientSide()) {
                        visualMode = VisualMode.SEALED_IDLE;
                    } else {
                        setVisualMode(VisualMode.SEALED_IDLE, "idle");
                    }
                    beginPhase(SequencePhase.SEALED_IDLE, "idle");
                    if (level instanceof ServerLevel serverLevel) {
                        PORTAL_MANAGER.handleCrystalReformComplete(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), worldPosition));
                    }
                }
            }
            case SLAM_HELD, SEALED_IDLE, NONE -> {
            }
        }
    }

    private long phaseAge(Level level) {
        if (phaseStartGameTime == Long.MIN_VALUE) {
            phaseStartGameTime = level.getGameTime();
        }
        return Math.max(0L, level.getGameTime() - phaseStartGameTime);
    }

    private float phaseAge(float partialTick) {
        if (level == null) {
            return 0.0F;
        }

        if (phaseStartGameTime == Long.MIN_VALUE) {
            phaseStartGameTime = level.getGameTime();
        }

        return Math.max(0.0F, level.getGameTime() - phaseStartGameTime + partialTick);
    }

    private float portalVisualAge(float partialTick) {
        if (level == null) {
            return 0.0F;
        }

        if (portalVisualStartGameTime == Long.MIN_VALUE) {
            portalVisualStartGameTime = level.getGameTime();
        }

        return Math.max(0.0F, level.getGameTime() - portalVisualStartGameTime + partialTick);
    }

    private long sequenceAge(Level level) {
        if (visualStartGameTime == Long.MIN_VALUE) {
            visualStartGameTime = level.getGameTime();
        }
        return Math.max(0L, level.getGameTime() - visualStartGameTime);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private void setVisualMode(VisualMode mode, String animation) {
        visualMode = mode;
        currentAnimation = animation;
        visualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), VISUAL_MODE_EVENT, mode.ordinal());
        }
    }

    private void setPortalVisualMode(PortalVisualMode mode) {
        portalVisualMode = mode;
        portalVisualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), PORTAL_VISUAL_EVENT, mode.ordinal());
        }
    }

    private void tickPortalVisual(Level level) {
        if (portalVisualMode == PortalVisualMode.OPENING && portalVisualAge(0.0F) >= PORTAL_SCALE_TICKS) {
            if (level.isClientSide()) {
                portalVisualMode = PortalVisualMode.OPEN;
            } else {
                setPortalVisualMode(PortalVisualMode.OPEN);
            }
        } else if (portalVisualMode == PortalVisualMode.CLOSING && portalVisualAge(0.0F) >= PORTAL_SCALE_TICKS) {
            if (level.isClientSide()) {
                portalVisualMode = PortalVisualMode.HIDDEN;
            } else {
                setPortalVisualMode(PortalVisualMode.HIDDEN);
            }
        }
    }

    private void syncPortalVisualState(Level level) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (isTransientCrystalAnimationActive()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (lastPortalVisualSyncGameTime != Long.MIN_VALUE && gameTime - lastPortalVisualSyncGameTime < PORTAL_VISUAL_SYNC_INTERVAL_TICKS) {
            return;
        }

        lastPortalVisualSyncGameTime = gameTime;
        PORTAL_MANAGER.syncPortalVisualForAnchor(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), worldPosition), this);
    }

    private boolean isTransientCrystalAnimationActive() {
        return switch (sequencePhase) {
            case OPENING_REFORM, OPENING_IDLE, SLAMMING, REFORMING_TO_IDLE -> true;
            case SLAM_HELD -> isSlamFadeActive();
            case NONE, SEALED_IDLE -> false;
        };
    }

    private boolean isSlamFadeActive() {
        return isSlamVisual() && visualAge(0.0F) <= slamFadeStartTicks() + FADE_OUT_TICKS;
    }

    private void beginPhase(SequencePhase phase, String animation) {
        sequencePhase = phase;
        currentAnimation = animation;
        phaseStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (phase == SequencePhase.SLAMMING) {
            slamCompletionNotified = false;
        }
        if (phase == SequencePhase.REFORMING_TO_IDLE) {
            reformCompletionNotified = false;
        }
    }

    private static int slamFadeStartTicks() {
        return ANIMATION_TIMINGS.slamTicks() + HOLD_AFTER_SLAM_TICKS;
    }

    @Override
    public boolean triggerEvent(int eventId, int eventData) {
        if (eventId == VISUAL_MODE_EVENT) {
            VisualMode[] modes = VisualMode.values();
            if (eventData >= 0 && eventData < modes.length) {
                visualMode = modes[eventData];
                currentAnimation = switch (visualMode) {
                    case HIDDEN -> "hidden";
                    case TEMPORARY_OPENING -> "reform";
                    case SEALED_REFORM -> "reform";
                    case SEALED_OPENING -> "sealed_slam";
                    case SEALED_IDLE -> "idle";
                };
                visualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
                beginPhase(syncedPhaseFor(visualMode), currentAnimation);
                return true;
            }
        }

        if (eventId == PORTAL_VISUAL_EVENT) {
            PortalVisualMode[] modes = PortalVisualMode.values();
            if (eventData >= 0 && eventData < modes.length) {
                portalVisualMode = modes[eventData];
                portalVisualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
                return true;
            }
        }

        return super.triggerEvent(eventId, eventData);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CrossroadCrystalBlockEntity>(CONTROLLER, 0, state -> {
            updateCurrentAnimationFromController(state);

            return switch (sequencePhase) {
                case OPENING_REFORM, REFORMING_TO_IDLE -> {
                    currentAnimation = "reform";
                    yield state.setAndContinue(REFORM);
                }
                case OPENING_IDLE, SEALED_IDLE -> {
                    currentAnimation = "idle";
                    yield state.setAndContinue(IDLE);
                }
                case SLAMMING, SLAM_HELD -> {
                    currentAnimation = sequencePhase == SequencePhase.SLAM_HELD ? "slam_hold" : "slam";
                    yield state.setAndContinue(SLAM);
                }
                case NONE -> {
                    if (isInsidePocketRealm() && visualMode == VisualMode.SEALED_IDLE) {
                        currentAnimation = "idle";
                        yield state.setAndContinue(IDLE);
                    }
                    yield PlayState.STOP;
                }
            };
        }).setTransitionTicks(0));
    }

    private SequencePhase syncedPhaseFor(VisualMode mode) {
        return switch (mode) {
            case HIDDEN -> SequencePhase.NONE;
            case TEMPORARY_OPENING -> SequencePhase.OPENING_REFORM;
            case SEALED_REFORM -> SequencePhase.REFORMING_TO_IDLE;
            case SEALED_OPENING -> SequencePhase.SLAMMING;
            case SEALED_IDLE -> SequencePhase.SEALED_IDLE;
        };
    }

    private void updateCurrentAnimationFromController(com.geckolib.animation.state.AnimationTest<CrossroadCrystalBlockEntity> state) {
        if (state.isCurrentAnimationStage("slam")) {
            currentAnimation = "slam";
        } else if (state.isCurrentAnimationStage("idle")) {
            currentAnimation = "idle";
        } else if (state.isCurrentAnimationStage("reform")) {
            currentAnimation = "reform";
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private enum VisualMode {
        HIDDEN,
        TEMPORARY_OPENING,
        SEALED_REFORM,
        SEALED_OPENING,
        SEALED_IDLE
    }

    private enum PortalVisualMode {
        HIDDEN,
        OPENING,
        OPEN,
        CLOSING
    }

    private enum SequencePhase {
        NONE,
        OPENING_REFORM,
        OPENING_IDLE,
        SLAMMING,
        SLAM_HELD,
        REFORMING_TO_IDLE,
        SEALED_IDLE
    }

    private record AnimationTimings(int reformTicks, int idleTicks, int slamTicks) {
        private static AnimationTimings load() {
            try (InputStream stream = openAnimationResource()) {
                if (stream != null) {
                    JsonObject animations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject()
                            .getAsJsonObject("animations");
                    return new AnimationTimings(
                            secondsToTicks(animations.getAsJsonObject("reform").get("animation_length").getAsDouble()),
                            secondsToTicks(animations.getAsJsonObject("idle").get("animation_length").getAsDouble()),
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
