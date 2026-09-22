package io.github.resalexander.railswitch.logic;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

/**
 * 沿轨前走一步：复刻原版 {@code NewMinecartBehavior.stepAlongTrack} 的出口选择逻辑
 * （出口方向与行进方向点积更大者胜），所以弯道也能跟着走。
 */
public final class RailPathWalker {
    private RailPathWalker() {
    }

    /**
     * @param pos   当前轨道格
     * @param entry 来向（车从哪一端进来的，= 行进方向的反向）
     * @param exit  出口方向
     */
    public record Step(BlockPos pos, Direction entry, Direction exit) {
    }

    /** 不是轨道或无法判定时返回 null。 */
    public static @Nullable Step next(Level level, BlockPos pos, Direction travelDir) {
        BlockState state = level.getBlockState(pos);
        if (!BaseRailBlock.isRail(state)) {
            return null;
        }
        Direction exit = exitFor(state, travelDir);
        if (exit == null) {
            return null;
        }
        return new Step(pos, travelDir.getOpposite(), exit);
    }

    /** 与速度点积更大的出口（平手时取第一个出口，与原版一致）。 */
    public static @Nullable Direction exitFor(BlockState state, Direction travelDir) {
        RailShape shape = state.getValue(shapeProperty(state));
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Direction d0 = horizontal(exits.getFirst());
        Direction d1 = horizontal(exits.getSecond());
        if (d0 == null || d1 == null) {
            return null;
        }
        return dot(d0, travelDir) >= dot(d1, travelDir) ? d0 : d1;
    }

    private static Property<RailShape> shapeProperty(BlockState state) {
        return ((BaseRailBlock) state.getBlock()).getShapeProperty();
    }

    /** 把 {x, y, z} 的方向向量取水平分量转成 Direction（坡道形状的 y 分量被忽略）。 */
    private static @Nullable Direction horizontal(Vec3i vector) {
        if (vector.getX() > 0) {
            return Direction.EAST;
        }
        if (vector.getX() < 0) {
            return Direction.WEST;
        }
        if (vector.getZ() > 0) {
            return Direction.SOUTH;
        }
        if (vector.getZ() < 0) {
            return Direction.NORTH;
        }
        return null;
    }

    private static int dot(Direction a, Direction b) {
        return a.getStepX() * b.getStepX() + a.getStepZ() * b.getStepZ();
    }
}
