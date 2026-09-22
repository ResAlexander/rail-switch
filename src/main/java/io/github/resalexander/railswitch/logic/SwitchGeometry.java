package io.github.resalexander.railswitch.logic;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

/**
 * 道岔的几何解析：谁是直股、谁是曲股、哪一端是尖轨端（面向端）。
 *
 * <p>约定（与方案 S1 一致）：曲股 B 与直股垂直；直股两端里，从某一端进来时 B 位于
 * 「本类型要求的左/右侧」的那一端是 <b>F（尖轨端 / 面向端）</b>，另一端是 T。
 * 左判定用 {@code left(d) = (d.z, 0, -d.x)}。
 *
 * @param throughA 直股一端（朝外的方向）
 * @param throughB 直股另一端（朝外的方向）
 * @param branch   曲股方向（朝外的方向）
 */
public record SwitchGeometry(Direction throughA, Direction throughB, Direction branch) {

    /** 解析几何：需要恰好 3 个连接（一对反向 + 一个垂直），否则返回 null（当普通铁轨处理）。 */
    public static @Nullable SwitchGeometry of(Level level, BlockPos pos) {
        boolean n = isRailAt(level, pos.north());
        boolean s = isRailAt(level, pos.south());
        boolean w = isRailAt(level, pos.west());
        boolean e = isRailAt(level, pos.east());

        Direction a = null;
        Direction b = null;
        Direction branch = null;
        if (n && s) {
            a = Direction.NORTH;
            b = Direction.SOUTH;
            branch = w == e ? null : (w ? Direction.WEST : Direction.EAST);
        } else if (w && e) {
            a = Direction.WEST;
            b = Direction.EAST;
            branch = n == s ? null : (n ? Direction.NORTH : Direction.SOUTH);
        }
        if (a == null || branch == null) {
            return null;
        }
        return new SwitchGeometry(a, b, branch);
    }

    /** 本类型道岔的尖轨端：从该端进来的车才是「顺向」，可以按键岔向曲股。 */
    public Direction facingFor(boolean divergeLeft) {
        boolean branchIsLeftOfA = left(this.throughA.getOpposite()) == this.branch;
        return branchIsLeftOfA == divergeLeft ? this.throughA : this.throughB;
    }

    /** 直股形状（出口为两个直股端）。 */
    public RailShape straightShape() {
        RailShape shape = shapeFor(this.throughA, this.throughB);
        return shape != null ? shape : RailShape.NORTH_SOUTH;
    }

    /** 岔位形状：把曲股与尖轨端连起来的那条弯道；也是逆向汇合要用的形状。 */
    public @Nullable RailShape divergeShape(boolean divergeLeft) {
        return shapeFor(this.branch, facingFor(divergeLeft));
    }

    /** 行进方向的左侧：d = (x, 0, z) 时 left = (z, 0, -x)。 */
    public static Direction left(Direction d) {
        return switch (d) {
            case EAST -> Direction.NORTH;
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            default -> d;
        };
    }

    /**
     * 反查形状：找出出口方向恰好是 {a, b} 的平铺铁轨形状。
     * 用 {@link AbstractMinecart#exits(RailShape)} 而不是硬编码形状名，版本升级更安全。
     */
    public static @Nullable RailShape shapeFor(Direction a, Direction b) {
        for (RailShape shape : RailShape.values()) {
            Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
            Direction d0 = horizontal(exits.getFirst());
            Direction d1 = horizontal(exits.getSecond());
            if (d0 == null || d1 == null) {
                continue;
            }
            if (exits.getFirst().getY() != 0 || exits.getSecond().getY() != 0) {
                continue; // 跳过坡道形状
            }
            if ((d0 == a && d1 == b) || (d0 == b && d1 == a)) {
                return shape;
            }
        }
        return null;
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

    private static boolean isRailAt(Level level, BlockPos pos) {
        return BaseRailBlock.isRail(level.getBlockState(pos));
    }
}
