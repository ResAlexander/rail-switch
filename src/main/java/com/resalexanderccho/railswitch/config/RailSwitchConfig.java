package com.resalexanderccho.railswitch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.resalexanderccho.railswitch.RailSwitchMod;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** 模组配置；文件不存在时按默认值生成。 */
public final class RailSwitchConfig {
    /** 提前判定距离（格）；0 = 按车速自动（max(2, 速度×2 + 2)）。 */
    public int lookaheadBlocks = 0;
    /** 按键记忆窗口（tick）：松开后仍算「按着」的时长，默认 10 tick = 0.5 秒。 */
    public int inputMemoryTicks = 10;
    /** 拨岔时是否播放音效。 */
    public boolean switchSound = true;
    /** 未开启 minecart_improvements 新物理时是否停用道岔逻辑（退化普通铁轨）。 */
    public boolean requireNewPhysics = true;
    /** 逆向过岔（从曲股来）行为：merge = 自动汇入尖轨端；stop = 直接停车。 */
    public String trailingBehavior = "merge";
    /** 排查问题时打开：把每次拨岔/冻结写进日志（默认关，正常玩请保持关闭）。 */
    public boolean debugLog = false;

    private static RailSwitchConfig instance = new RailSwitchConfig();

    private RailSwitchConfig() {
    }

    public static RailSwitchConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("rail_switch.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                RailSwitchConfig loaded = gson.fromJson(Files.readString(path), RailSwitchConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            }
            Files.writeString(path, gson.toJson(instance));
        } catch (Exception exception) {
            RailSwitchMod.LOGGER.warn("[rail_switch] 配置读取失败，使用默认值", exception);
        }
    }
}
