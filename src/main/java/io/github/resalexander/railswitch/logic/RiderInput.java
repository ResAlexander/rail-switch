package io.github.resalexander.railswitch.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * 骑手按键采样：每 tick 记下「向左 / 向右」最后一次按下的时刻，
 * 于是「长按」一直有效、松开后还能保留一小段记忆窗口（高速车来得及）。
 */
public final class RiderInput {
    private static final Map<UUID, Memory> MEMORY = new HashMap<>();

    private RiderInput() {
    }

    private static final class Memory {
        private long leftTick = Long.MIN_VALUE / 2;
        private long rightTick = Long.MIN_VALUE / 2;
    }

    /** 每 tick 对在线玩家调用一次。 */
    public static void sample(ServerPlayer player, long tick) {
        Input input = player.getLastClientInput();
        Memory memory = MEMORY.computeIfAbsent(player.getUUID(), key -> new Memory());
        if (input.left()) {
            memory.leftTick = tick;
        }
        if (input.right()) {
            memory.rightTick = tick;
        }
    }

    /** 长按中，或在记忆窗口内按过。 */
    public static boolean leftHeld(ServerPlayer player, long tick, int memoryTicks) {
        return within(player, tick, memoryTicks, true);
    }

    public static boolean rightHeld(ServerPlayer player, long tick, int memoryTicks) {
        return within(player, tick, memoryTicks, false);
    }

    private static boolean within(ServerPlayer player, long tick, int memoryTicks, boolean left) {
        Memory memory = MEMORY.get(player.getUUID());
        if (memory == null) {
            return false;
        }
        long last = left ? memory.leftTick : memory.rightTick;
        return tick - last <= Math.max(0, memoryTicks);
    }

    /** 玩家离线后清掉记录，避免长期堆积。 */
    public static void prune(Collection<UUID> online) {
        MEMORY.keySet().retainAll(online);
    }
}
