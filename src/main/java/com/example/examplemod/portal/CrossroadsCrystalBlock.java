package com.example.examplemod.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class CrossroadsCrystalBlock extends Block implements EntityBlock {
    public static final BooleanProperty ANCHOR = BooleanProperty.create("anchor");
    private static final RealmCrystalManager CRYSTAL_MANAGER = new RealmCrystalManager();

    public CrossroadsCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ANCHOR, true));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockPos anchorPos = state.getValue(ANCHOR) ? pos : pos.below();
            CRYSTAL_MANAGER.openMenu(serverPlayer, GlobalPos.of(level.dimension(), anchorPos));
            return InteractionResult.SUCCESS;
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANCHOR);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int eventId, int eventData) {
        super.triggerEvent(state, level, pos, eventId, eventData);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(eventId, eventData);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(ANCHOR) ? new CrossroadCrystalBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (!state.getValue(ANCHOR)) {
            return null;
        }

        return blockEntityType == com.example.examplemod.CrossroadDimension.CROSSROADS_CRYSTAL_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> CrossroadCrystalBlockEntity.tick(tickerLevel, pos, tickerState, (CrossroadCrystalBlockEntity) blockEntity)
                : null;
    }
}
