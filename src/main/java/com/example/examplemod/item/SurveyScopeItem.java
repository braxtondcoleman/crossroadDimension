package com.example.examplemod.item;

import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.examplemod.client.render.WispJarRenderer;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.TagKey;

public class SurveyScopeItem extends Item implements GeoItem {
    private final float red;
    private final float green;
    private final float blue;
    private static final int USE_DURATION = 72000;
    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final int BREADCRUMB_INTERVAL_TICKS = 20;
    private static final int PULSE_INTERVAL_TICKS = 40;
    private static final double SOURCE_SWITCH_MARGIN = 2.0;
    private final TagKey<Block> targetTag;
    private final Map<UUID, Optional<SurveyScan.Source>> trackedSources = new HashMap<>();
    private final Map<UUID, SurveyScan.Breadcrumb> activeBreadcrumbs = new HashMap<>();
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public SurveyScopeItem(Properties properties,
                        TagKey<Block> targetTag,
                        float red,
                        float green,
                        float blue) {
        super(properties);
        this.targetTag = targetTag;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public float getRed() {
    return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private WispJarRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new WispJarRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // The jar is static; its spirit sprites are animated procedurally by the render layer.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack scope, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer player)) {
            return;
        }

        int ticksUsed = USE_DURATION - ticksRemaining;
        UUID playerId = player.getUUID();
        if (ticksUsed % RESCAN_INTERVAL_TICKS == 0 || !trackedSources.containsKey(playerId)) {
            List<SurveyScan.Source> nearestSources = SurveyScan.findSources(serverLevel, player, this.targetTag);
            SurveyScan.Source current = trackedSources.getOrDefault(playerId, Optional.empty()).orElse(null);
            trackedSources.put(playerId, Optional.ofNullable(selectActiveSource(current, nearestSources)));
        }

        SurveyScan.Source source = trackedSources.getOrDefault(playerId, Optional.empty()).orElse(null);
        if (source == null) {
            activeBreadcrumbs.remove(playerId);
            return;
        }

        if (!activeBreadcrumbs.containsKey(playerId) && ticksUsed % BREADCRUMB_INTERVAL_TICKS == 0) {
            SurveyScan.Breadcrumb breadcrumb = SurveyScan.createBreadcrumb(player, source, this);
            if (breadcrumb != null) {
                activeBreadcrumbs.put(playerId, breadcrumb);
            }
        }

        SurveyScan.Breadcrumb breadcrumb = activeBreadcrumbs.get(playerId);
        if (breadcrumb != null && !SurveyScan.tickBreadcrumb(serverLevel, player, breadcrumb)) {
            activeBreadcrumbs.remove(playerId);
        }

        if (ticksUsed > 0 && ticksUsed % PULSE_INTERVAL_TICKS == 0) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.4F, 1.2F);
            scope.hurtAndBreak(1, player, player.getUsedItemHand());
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        trackedSources.remove(entity.getUUID());
        activeBreadcrumbs.remove(entity.getUUID());
    }

    private static SurveyScan.Source selectActiveSource(SurveyScan.Source current,
            List<SurveyScan.Source> nearestSources) {
        if (nearestSources.isEmpty()) {
            return null;
        }

        SurveyScan.Source nearest = nearestSources.getFirst();
        if (current == null) {
            return nearest;
        }

        SurveyScan.Source refreshedCurrent = nearestSources.stream()
                .filter(source -> source.anchor().equals(current.anchor()))
                .findFirst()
                .orElse(null);
        if (refreshedCurrent == null || refreshedCurrent.anchor().equals(nearest.anchor())) {
            return nearest;
        }

        double nearestDistance = Math.sqrt(nearest.distanceSquared());
        double currentDistance = Math.sqrt(refreshedCurrent.distanceSquared());
        return nearestDistance + SOURCE_SWITCH_MARGIN <= currentDistance ? nearest : refreshedCurrent;
    }
}
