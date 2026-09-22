package com.resalexanderccho.railswitch.logic;

import com.resalexanderccho.railswitch.RailSwitchMod;
import com.resalexanderccho.railswitch.block.AbstractSwitchRailBlock;
import com.resalexanderccho.railswitch.config.RailSwitchConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * 拨岔调度：每 tick 早于实体 tick 跑一遍。
 *
 * <p>核心安全规则（v1.0.1 修复振荡/倒退）：<b>道岔一旦被某辆车定下进路，就冻结到该车离开道岔格为止</b>。
 * 具体三种情况都不许再改形状：
 * <ol>
 *   <li>车已经在这个道岔格上（无论它在格内是直行还是转弯）；</li>
 *   <li>该车已经进入「提交距离」（本 tick 内必然会进格）；</li>
 *   <li>形状被另一辆车锁住且那辆车已提交/在格上时，其它车不许抢。</li>
 * </ol>
 * 原因：车在格内转弯后速度方向已经变成曲股方向，若此时用速度反推来向会被误判成
 * 「从直股另一端来」（甚至反推出不属于本几何的方向），把形状改回直股；而车带着曲股方向的
 * 速度落在直股形状上时，原版 stepAlongTrack 会把它推向两个出口里排序靠前的那个，
 * 于是车以原速倒退，形成高频振荡。
 */
public final class SwitchDispatcher {
    /** 拨岔后冻结的时长（tick），车在附近走过时会持续刷新。 */
    private static final int LOCK_TICKS = 2;

    private record Key(ServerLevel level, BlockPos pos) {
    }

    /** committed = 该车已提交进路（在格上或本 tick 必进格），此时形状不许再被任何车改写。 */
    private record Lock(int cartId, boolean diverted, boolean committed, long untilTick, double distance) {
        Lock refreshed(long tick, double distance) {
            return new Lock(this.cartId, this.diverted, this.committed, tick + LOCK_TICKS, distance);
        }
    }

    private static final Map<Key, Lock> LOCKS = new HashMap<>();
    private static final Set<ServerLevel> WARNED_NO_PHYSICS = new HashSet<>();

    private SwitchDispatcher() {
    }

    public static void onLevelTick(ServerLevel level) {
        RailSwitchConfig config = RailSwitchConfig.get();
        long tick = level.getGameTime();

        if (config.requireNewPhysics && !AbstractMinecart.useExperimentalMovement(level)) {
            if (WARNED_NO_PHYSICS.add(level)) {
                RailSwitchMod.LOGGER.warn(
                    "[rail_switch] {} 未启用 minecart_improvements 新物理，道岔逻辑停用，方块退化为普通铁轨",
                    level.dimension().toString());
            }
            return;
        }

        for (ServerPlayer player : level.players()) {
            RiderInput.sample(player, tick);
        }
        if (tick % 200L == 0L) {
            List<UUID> online = level.getServer().getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).toList();
            RiderInput.prune(online);
        }

        List<? extends AbstractMinecart> carts = level.getEntities(
            EntityTypeTest.forClass(AbstractMinecart.class), cart -> true);
        for (AbstractMinecart cart : carts) {
            try {
                tickCart(level, cart, tick, config);
            } catch (RuntimeException exception) {
                RailSwitchMod.LOGGER.error("[rail_switch] 处理矿车 {} 出错", cart.getId(), exception);
            }
        }

        LOCKS.entrySet().removeIf(entry -> entry.getValue().untilTick() + 40L < tick);
    }

    /** 道岔方块用它判断「现在有车正在过岔」，避免形状被邻居更新冲掉。 */
    public static boolean isLocked(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Lock lock = LOCKS.get(new Key(serverLevel, pos));
        return lock != null && lock.untilTick() >= serverLevel.getGameTime();
    }

    private static void tickCart(ServerLevel level, AbstractMinecart cart, long tick, RailSwitchConfig config) {
        if (!(cart.getBehavior() instanceof NewMinecartBehavior)) {
            return;
        }
        Vec3 velocity = cart.getDeltaMovement().horizontal();
        double speed = velocity.length();
        if (speed < 1.0E-4) {
            return;
        }
        BlockPos cartBlock = cart.getCurrentBlockPosOrRailBelow();
        if (!BaseRailBlock.isRail(level.getBlockState(cartBlock))) {
            return;
        }
        Direction travel = dominant(velocity);
        if (travel == null) {
            return;
        }

        int lookahead = config.lookaheadBlocks > 0
            ? config.lookaheadBlocks
            : (int) Math.ceil(speed * 2.0) + 2;
        lookahead = Mth.clamp(lookahead, 2, 64);
        double commitDistance = speed + 1.0;

        ServerPlayer rider = cart.getFirstPassenger() instanceof ServerPlayer player ? player : null;
        boolean leftHeld = rider != null && RiderInput.leftHeld(rider, tick, config.inputMemoryTicks);
        boolean rightHeld = rider != null && RiderInput.rightHeld(rider, tick, config.inputMemoryTicks);
        Vec3 cartPosition = cart.position();

        BlockPos pos = cartBlock;
        for (int step = 0; step < lookahead; step++) {
            RailPathWalker.Step current = RailPathWalker.next(level, pos, travel);
            if (current == null) {
                return;
            }
            Direction exit = current.exit();
            BlockState state = level.getBlockState(current.pos());
            if (state.getBlock() instanceof AbstractSwitchRailBlock switchRail) {
                SwitchGeometry geometry = SwitchGeometry.of(level, current.pos());
                if (geometry != null) {
                    Direction decided = decide(level, current.pos(), state, switchRail, geometry,
                        current.entry(), leftHeld, rightHeld, cart, cartBlock, cartPosition,
                        commitDistance, tick, config);
                    if (decided != null) {
                        exit = decided;
                    }
                }
            }
            travel = exit;
            pos = current.pos().relative(exit);
        }
    }

    /** @return 车在本格的出口方向；null = 不干预（按当前形状走）。 */
    private static Direction decide(ServerLevel level, BlockPos pos, BlockState state,
                                    AbstractSwitchRailBlock block, SwitchGeometry geometry, Direction entry,
                                    boolean leftHeld, boolean rightHeld, AbstractMinecart cart,
                                    BlockPos cartBlock, Vec3 cartPosition, double commitDistance,
                                    long tick, RailSwitchConfig config) {
        Key key = new Key(level, pos);
        Lock lock = LOCKS.get(key);
        boolean ownLock = lock != null && lock.cartId() == cart.getId();
        double distance = cartPosition.distanceTo(Vec3.atCenterOf(pos));

        // 规则 1：车已经在这个道岔格上 —— 进路已定，绝不改形状。
        if (cartBlock.equals(pos)) {
            LOCKS.put(key, new Lock(cart.getId(), ownLock && lock.diverted(), true,
                tick + LOCK_TICKS, distance));
            debug(config, "freeze(on-switch) {} cart={} entry={}", pos, cart.getId(), entry);
            return null;
        }
        // 规则 2：这辆车在本 tick 已经提交进路 —— 也不许再改（最后一刻按键已在提交那次生效）。
        if (ownLock && lock.committed()) {
            LOCKS.put(key, lock.refreshed(tick, distance));
            debug(config, "freeze(committed) {} cart={}", pos, cart.getId());
            return null;
        }
        // 规则 3：形状被别的车锁着 —— 提交过的锁不可抢；否则按近者优先、同距岔位优先。
        if (!ownLock && lock != null && lock.untilTick() > tick) {
            if (lock.committed()) {
                return null;
            }
            boolean closer = distance + 1.0 < lock.distance();
            boolean divertPrecedence = !lock.diverted() && distance <= lock.distance() + 1.0;
            if (!closer && !divertPrecedence) {
                return null;
            }
        }

        boolean divergeLeft = block.divergeLeft();
        Direction branch = geometry.branch();
        Direction facing = geometry.facingFor(divergeLeft);
        Direction trailing = facing.getOpposite();
        RailShape straight = geometry.straightShape();
        RailShape diverge = geometry.divergeShape(divergeLeft);
        if (diverge == null) {
            return null;
        }
        // 规则 4：来向必须属于本几何（直股两端或曲股）。车正在格内转弯时速度方向可能是
        // 曲股方向以外的轴，属于异常状态，不动形状。
        if (entry != branch && entry != facing && entry != trailing) {
            debug(config, "skip(entry-not-in-geometry) {} entry={}", pos, entry);
            return null;
        }

        RailShape target;
        Direction exit;
        boolean diverted;
        if (entry == branch) {
            // 逆向过岔：从曲股来，自动汇入尖轨端（可选配置为停车）。
            if ("stop".equalsIgnoreCase(config.trailingBehavior)) {
                cart.setDeltaMovement(Vec3.ZERO);
                return null;
            }
            target = diverge;
            exit = facing;
            diverted = true;
        } else if (entry == facing) {
            // 顺向过岔：按键决定岔位/直行。
            boolean pressed = divergeLeft ? leftHeld : rightHeld;
            target = pressed ? diverge : straight;
            exit = pressed ? branch : trailing;
            diverted = pressed;
        } else {
            // 从直股另一端来：本类型不响应按键，恒直行。
            target = straight;
            exit = facing;
            diverted = false;
        }

        if (state.getValue(AbstractSwitchRailBlock.SHAPE) != target) {
            block.applyShape(level, pos, state, target);
            if (config.switchSound) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            debug(config, "set {} cart={} entry={} target={} diverted={} dist={}",
                pos, cart.getId(), entry, target, diverted, String.format("%.2f", distance));
        }
        boolean committed = distance <= commitDistance;
        LOCKS.put(key, new Lock(cart.getId(), diverted, committed, tick + LOCK_TICKS, distance));
        return exit;
    }

    private static void debug(RailSwitchConfig config, String format, Object... args) {
        if (config.debugLog) {
            RailSwitchMod.LOGGER.info("[rail_switch] " + format, args);
        }
    }

    /** 速度的主轴方向（平手时偏向 X 轴）。 */
    private static Direction dominant(Vec3 velocity) {
        if (Math.abs(velocity.x) >= Math.abs(velocity.z)) {
            return velocity.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return velocity.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }
}
