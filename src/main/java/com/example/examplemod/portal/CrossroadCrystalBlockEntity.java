package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrossroadCrystalBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int VISUAL_MODE_EVENT = 1;
    public static final String CONTROLLER = "crystal";
    public static final String SLAM_TRIGGER = "slam";
    public static final String REFORM_IDLE_TRIGGER = "reform_idle";
    public static final String OPENING_TRIGGER = "opening";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlayAndHold("slam");
    private static final RawAnimation OPENING = RawAnimation.begin().thenPlay("reform").thenWait(5).thenPlayXTimes("idle", 1);
    private static final RawAnimation REFORM_TO_IDLE = RawAnimation.begin().thenPlay("reform").thenWait(5).thenLoop("idle");
    private static final int MATERIALIZE_TICKS = 8;
    private static final int REFORM_TICKS = 20;
    private static final int REFORM_TO_SLAM_PAUSE_TICKS = 5;
    private static final int IDLE_LOOP_TICKS = 160;
    private static final int SLAM_TICKS = 45;
    private static final int HOLD_AFTER_SLAM_TICKS = 24;
    private static final int FADE_OUT_TICKS = 80;
    private static final int TEMPORARY_FADE_START_TICKS = REFORM_TICKS + REFORM_TO_SLAM_PAUSE_TICKS + IDLE_LOOP_TICKS + SLAM_TICKS + HOLD_AFTER_SLAM_TICKS;
    private static final int SEALED_FADE_START_TICKS = SLAM_TICKS + HOLD_AFTER_SLAM_TICKS;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long visualStartGameTime = Long.MIN_VALUE;
    private long lastRenderDebugGameTime = Long.MIN_VALUE;
    private String currentAnimation = "none";
    private VisualMode visualMode = VisualMode.HIDDEN;

    public CrossroadCrystalBlockEntity(BlockPos pos, BlockState blockState) {
        super(CrossroadDimension.CROSSROADS_GATE_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void playSlam() {
        if (visualMode == VisualMode.SEALED_IDLE) {
            setVisualMode(VisualMode.SEALED_OPENING, "slam");
        } else {
            currentAnimation = "slam";
        }
        logVisualTransition("playSlam", SLAM_TRIGGER);
        triggerAnim(CONTROLLER, SLAM_TRIGGER);
    }

    public void playOpeningSequence() {
        setVisualMode(VisualMode.TEMPORARY_OPENING, "opening");
        logVisualTransition("playOpeningSequence", OPENING_TRIGGER);
        triggerAnim(CONTROLLER, OPENING_TRIGGER);
    }

    public void playReformToIdle() {
        setVisualMode(VisualMode.SEALED_IDLE, "reform_idle");
        logVisualTransition("playReformToIdle", REFORM_IDLE_TRIGGER);
        triggerAnim(CONTROLLER, REFORM_IDLE_TRIGGER);
    }

    public float renderAlpha(float partialTick) {
        if (visualMode == VisualMode.HIDDEN) {
            logRenderState(0.0F, partialTick);
            return 0.0F;
        }

        if (visualMode == VisualMode.SEALED_IDLE) {
            float alpha = 1.0F;
            logRenderState(alpha, partialTick);
            return alpha;
        }

        float age = visualAge(partialTick);
        int fadeStart = visualMode == VisualMode.SEALED_OPENING ? SEALED_FADE_START_TICKS : TEMPORARY_FADE_START_TICKS;
        if (age <= fadeStart) {
            logRenderState(1.0F, partialTick);
            return 1.0F;
        }

        float alpha = 1.0F - clamp((age - fadeStart) / FADE_OUT_TICKS);
        logRenderState(alpha, partialTick);
        return alpha;
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

    @Override
    public boolean triggerEvent(int eventId, int eventData) {
        if (eventId == VISUAL_MODE_EVENT) {
            VisualMode[] modes = VisualMode.values();
            if (eventData >= 0 && eventData < modes.length) {
                visualMode = modes[eventData];
                currentAnimation = switch (visualMode) {
                    case HIDDEN -> "hidden";
                    case TEMPORARY_OPENING -> "opening/slam";
                    case SEALED_OPENING -> "sealed_slam";
                    case SEALED_IDLE -> "reform_idle";
                };
                visualStartGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
                logVisualTransition("syncVisualMode", currentAnimation);
                return true;
            }
        }

        return super.triggerEvent(eventId, eventData);
    }

    private void logVisualTransition(String source, String animation) {
        CrossroadDimension.LOGGER.info(
                "Crossroads crystal visual transition: source={} side={} pos={} mode={} animation={}",
                source,
                level != null && level.isClientSide() ? "client" : "server",
                worldPosition,
                visualMode,
                animation
        );
    }

    private void logRenderState(float alpha, float partialTick) {
        if (level == null) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime == lastRenderDebugGameTime || gameTime % 20L != 0L) {
            return;
        }

        lastRenderDebugGameTime = gameTime;
        CrossroadDimension.LOGGER.info(
                "Crossroads crystal render state: side={} pos={} mode={} animation={} alpha={} age={} partial={}",
                level.isClientSide() ? "client" : "server",
                worldPosition,
                visualMode,
                currentAnimation,
                String.format(java.util.Locale.ROOT, "%.2f", alpha),
                String.format(java.util.Locale.ROOT, "%.2f", visualAge(partialTick)),
                String.format(java.util.Locale.ROOT, "%.2f", partialTick)
        );
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CrossroadCrystalBlockEntity>(CONTROLLER, 0, state -> {
            if (isInsidePocketRealm()) {
                if (visualMode == VisualMode.SEALED_IDLE) {
                    return state.setAndContinue(IDLE);
                }

                return PlayState.STOP;
            }

            return PlayState.STOP;
        })
                .triggerableAnim(SLAM_TRIGGER, SLAM)
                .triggerableAnim(OPENING_TRIGGER, OPENING)
                .triggerableAnim(REFORM_IDLE_TRIGGER, REFORM_TO_IDLE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private enum VisualMode {
        HIDDEN,
        TEMPORARY_OPENING,
        SEALED_OPENING,
        SEALED_IDLE
    }
}
