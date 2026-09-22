package io.github.resalexander.railswitch.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 右开道岔：骑手按住「向右」（默认 D）且曲股在行进方向右侧时岔向曲股；无输入时不替该车扳岔。 */
public class RightSwitchRailBlock extends AbstractSwitchRailBlock {
    public static final MapCodec<RightSwitchRailBlock> CODEC = simpleCodec(RightSwitchRailBlock::new);

    public RightSwitchRailBlock(BlockBehaviour.Properties properties) {
        super(false, properties);
    }

    @Override
    protected MapCodec<? extends BaseRailBlock> codec() {
        return CODEC;
    }
}
