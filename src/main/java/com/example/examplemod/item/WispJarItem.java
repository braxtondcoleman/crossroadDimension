package com.example.examplemod.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.client.render.WispJarRenderer;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class WispJarItem extends Item implements GeoItem {
    public static final int MAX_RESONANCE = 100;
    private static final int RESONANCE_USE_INTERVAL_TICKS = 40;
    private static final int USE_DURATION = 72000;
    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final int BREADCRUMB_INTERVAL_TICKS = 20;
    private static final int PULSE_INTERVAL_TICKS = 40;
    private static final double SOURCE_SWITCH_MARGIN = 2.0;

    private final Map<UUID, Optional<SurveyScan.Source>> trackedSources = new HashMap<>();
    private final Map<UUID, SurveyScan.Breadcrumb> activeBreadcrumbs = new HashMap<>();
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public WispJarItem(Properties properties) {
        super(properties);
    }

    public Optional<WispAttunement> getAttunement(ItemStack stack) {
        return WispAttunement.byId(getData(stack).attunement());
    }

    public float getRed(ItemStack stack) {
        return getAttunement(stack).map(WispAttunement::red).orElse(1.0F);
    }

    public float getGreen(ItemStack stack) {
        return getAttunement(stack).map(WispAttunement::green).orElse(1.0F);
    }

    public float getBlue(ItemStack stack) {
        return getAttunement(stack).map(WispAttunement::blue).orElse(1.0F);
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
        ItemStack jar = player.getItemInHand(hand);
        InteractionHand materialHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack material = player.getItemInHand(materialHand);
        Optional<WispAttunement> insertedAttunement = WispAttunement.fromMaterial(material);

        if (insertedAttunement.isPresent()) {
            if (!level.isClientSide()) {
                WispAttunement attunement = insertedAttunement.get();
                WispJarData currentData = getData(jar);
                if (currentData.attunement().equals(attunement.id())
                        && currentData.resonance() >= MAX_RESONANCE) {
                    player.sendOverlayMessage(Component.translatable(
                            "message.crossroaddimension.wisp_jar.resonance_full"));
                    return InteractionResult.SUCCESS;
                }
                jar.set(CrossroadDimension.WISP_JAR_DATA.get(),
                        currentData.withAttunement(attunement, MAX_RESONANCE));
                if (!player.hasInfiniteMaterials()) {
                    material.shrink(1);
                }
                player.sendOverlayMessage(Component.translatable(
                        "message.crossroaddimension.wisp_jar.attuned", attunement.displayName()));
                level.playSound(null, player.blockPosition(), CrossroadDimension.WISP_ATTUNEMENT.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        if (getAttunement(jar).isEmpty()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(
                        "message.crossroaddimension.wisp_jar.unattuned"));
            }
            return InteractionResult.FAIL;
        }

        if (getData(jar).resonance() <= 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(
                        "message.crossroaddimension.wisp_jar.no_resonance"));
            }
            return InteractionResult.FAIL;
        }

        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack jar, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer player)) {
            return;
        }

        Optional<WispAttunement> attunement = getAttunement(jar);
        if (attunement.isEmpty()) {
            player.stopUsingItem();
            return;
        }

        int ticksUsed = USE_DURATION - ticksRemaining;
        UUID playerId = player.getUUID();

        if (ticksUsed > 0 && ticksUsed % RESONANCE_USE_INTERVAL_TICKS == 0) {
            WispJarData data = getData(jar);
            if (data.resonance() <= 0) {
                stopSearching(player, playerId);
                player.sendOverlayMessage(Component.translatable(
                        "message.crossroaddimension.wisp_jar.no_resonance"));
                return;
            }
            jar.set(CrossroadDimension.WISP_JAR_DATA.get(), data.withResonance(data.resonance() - 1));
        }

        if (ticksUsed % RESCAN_INTERVAL_TICKS == 0 || !this.trackedSources.containsKey(playerId)) {
            List<SurveyScan.Source> nearestSources = SurveyScan.findSources(
                    serverLevel, player, attunement.get().targetTag());
            SurveyScan.Source current = this.trackedSources
                    .getOrDefault(playerId, Optional.empty()).orElse(null);
            this.trackedSources.put(playerId, Optional.ofNullable(selectActiveSource(current, nearestSources)));
        }

        SurveyScan.Source source = this.trackedSources.getOrDefault(playerId, Optional.empty()).orElse(null);
        if (source == null) {
            this.activeBreadcrumbs.remove(playerId);
            return;
        }

        if (!this.activeBreadcrumbs.containsKey(playerId) && ticksUsed % BREADCRUMB_INTERVAL_TICKS == 0) {
            SurveyScan.Breadcrumb breadcrumb = SurveyScan.createBreadcrumb(player, source, this, jar);
            if (breadcrumb != null) {
                this.activeBreadcrumbs.put(playerId, breadcrumb);
            }
        }

        SurveyScan.Breadcrumb breadcrumb = this.activeBreadcrumbs.get(playerId);
        if (breadcrumb != null && !SurveyScan.tickBreadcrumb(serverLevel, player, breadcrumb)) {
            this.activeBreadcrumbs.remove(playerId);
        }

        if (ticksUsed > 0 && ticksUsed % PULSE_INTERVAL_TICKS == 0) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.4F, 1.2F);
            jar.hurtAndBreak(1, player, player.getUsedItemHand());
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        this.trackedSources.remove(entity.getUUID());
        this.activeBreadcrumbs.remove(entity.getUUID());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag tooltipFlag) {
        WispJarData data = getData(stack);
        Optional<WispAttunement> attunement = getAttunement(stack);
        Component attunementName = attunement
                .map(WispAttunement::displayName)
                .orElseGet(() -> Component.translatable("attunement.crossroaddimension.unattuned"));

        builder.accept(Component.translatable(
                "tooltip.crossroaddimension.wisp_jar.attunement", attunementName)
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(
                "tooltip.crossroaddimension.wisp_jar.resonance", data.resonance(), MAX_RESONANCE)
                .withStyle(data.resonance() > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        builder.accept(Component.translatable("tooltip.crossroaddimension.wisp_jar.insert")
                .withStyle(ChatFormatting.DARK_GRAY));
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

    private static WispJarData getData(ItemStack stack) {
        return stack.getOrDefault(CrossroadDimension.WISP_JAR_DATA.get(), WispJarData.EMPTY);
    }

    private void stopSearching(ServerPlayer player, UUID playerId) {
        this.trackedSources.remove(playerId);
        this.activeBreadcrumbs.remove(playerId);
        player.stopUsingItem();
    }
}
