package io.github.resalexander.railswitch.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 左开道岔：骑手按住「向左」（默认 A）且曲股在行进方向左侧时岔向曲股；无输入时不替该车扳岔。 */
public class LeftSwitchRailBlock extends AbstractSwitchRailBlock {
    public static final MapCodec<LeftSwitchRailBlock> CODEC = simpleCodec(LeftSwitchRailBlock::new);

    public LeftSwitchRailBlock(BlockBehaviour.Properties properties) {
        super(true, properties);
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return CODEC;
    }
}
