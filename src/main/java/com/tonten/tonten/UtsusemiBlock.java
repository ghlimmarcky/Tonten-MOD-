package com.tonten.tonten;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class UtsusemiBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    private static final int FADE_INTERVAL_TICKS = 10 * 20;

    public UtsusemiBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, FADE_INTERVAL_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState currentState = level.getBlockState(pos);
        if (!currentState.is(this)) {
            return;
        }

        int age = currentState.getValue(AGE);
        if (age >= 2) {
            level.removeBlock(pos, false);
            return;
        }

        level.setBlock(pos, currentState.setValue(AGE, age + 1), 3);
        level.scheduleTick(pos, this, FADE_INTERVAL_TICKS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
