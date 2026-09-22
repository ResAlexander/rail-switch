package io.github.resalexander.railswitch.block;

import io.github.resalexander.railswitch.logic.SwitchDispatcher;
import io.github.resalexander.railswitch.logic.SwitchGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * 道岔方块公共实现。
 *
 * <p>要点：
 * <ul>
 *   <li>继承 {@link BaseRailBlock} 且在 {@code minecraft:rails} 标签里 —— 这是原版
 *       {@code BaseRailBlock.isRail} 的两个条件，缺一则原版铁轨不认它。</li>
 *   <li>{@code isStraight = false}，允许弯道形状（岔位就是一条弯道）。</li>
 *   <li>覆盖两个 updateState：原版 {@code updateDir → RailState.place} 会在 3 连接时
 *       按固定优先级强制选一条弯道，把我们的岔位形状冲掉，所以形状由本模组自己维护。</li>
 *   <li>形状变化后调 {@link RailState#place} 通知邻居原版铁轨重连，然后写回目标形状。</li>
 * </ul>
 */
public abstract class AbstractSwitchRailBlock extends BaseRailBlock {
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;

    private final boolean divergeLeft;

    protected AbstractSwitchRailBlock(boolean divergeLeft, Properties properties) {
        super(false, properties);
        this.divergeLeft = divergeLeft;
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(SHAPE, RailShape.NORTH_SOUTH)
            .setValue(WATERLOGGED, false));
    }

    /** true = 左开道岔（曲股在行进方向左侧时响应「向左」）。 */
    public final boolean divergeLeft() {
        return this.divergeLeft;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, WATERLOGGED);
    }

    /** 放置时走我们自己的几何初始化（原版 onPlace 会调到）。 */
    @Override
    protected BlockState updateState(BlockState state, Level level, BlockPos pos, boolean movedByPiston) {
        return this.recompute(state, level, pos);
    }

    /** 邻居变化：重算空闲形状（有车正在过岔时不动）。 */
    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        if (SwitchDispatcher.isLocked(level, pos)) {
            return;
        }
        this.recompute(state, level, pos);
    }

    /** 计算并写回「空闲形状」：几何成立时是直股，几何不成立时保持原状。 */
    private BlockState recompute(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return state;
        }
        SwitchGeometry geometry = SwitchGeometry.of(level, pos);
        RailShape current = state.getValue(SHAPE);
        RailShape target = geometry != null ? geometry.straightShape() : current;
        BlockState newState = state;
        if (target != current) {
            newState = state.setValue(SHAPE, target);
            level.setBlock(pos, newState, Block.UPDATE_ALL);
        }
        this.notifyNeighbours(level, pos, newState);
        return newState;
    }

    /** 拨岔：把形状设成目标值，并让相邻的原版铁轨重新对接。 */
    public void applyShape(Level level, BlockPos pos, BlockState state, RailShape shape) {
        if (state.getValue(SHAPE) != shape) {
            state = state.setValue(SHAPE, shape);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        this.notifyNeighbours(level, pos, state);
    }

    /**
     * 触发邻居重连：原版的 {@code RailState.place} 会把我们当铁轨并重算邻居形状，
     * 但它自己也会按优先级改写我们的形状，所以调用后必须把目标形状写回。
     */
    private void notifyNeighbours(Level level, BlockPos pos, BlockState state) {
        RailShape target = state.getValue(SHAPE);
        new RailState(level, pos, state).place(false, true, target);
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() == state.getBlock() && after.getValue(SHAPE) != target) {
            level.setBlock(pos, after.setValue(SHAPE, target), Block.UPDATE_ALL);
        }
    }
}
