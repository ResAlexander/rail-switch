package com.resalexanderccho.railswitch;

import com.resalexanderccho.railswitch.block.LeftSwitchRailBlock;
import com.resalexanderccho.railswitch.block.RightSwitchRailBlock;
import com.resalexanderccho.railswitch.block.SwitchRailItem;
import com.resalexanderccho.railswitch.config.RailSwitchConfig;
import com.resalexanderccho.railswitch.logic.SwitchDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail Switch：两种 Y 型道岔方块。
 *
 * <p>服务端负责全部逻辑（读骑手按键、拨岔形状）；客户端只为方块资源。
 * 因此模组必须同时装在服务端与客户端，且版本一致。
 */
public final class RailSwitchMod implements ModInitializer {
    public static final String MOD_ID = "rail_switch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block LEFT_SWITCH_RAIL = registerBlock("left_switch_rail",
        new LeftSwitchRailBlock(blockProperties("left_switch_rail")));
    public static final Block RIGHT_SWITCH_RAIL = registerBlock("right_switch_rail",
        new RightSwitchRailBlock(blockProperties("right_switch_rail")));

    public static final Item LEFT_SWITCH_RAIL_ITEM = registerItem("left_switch_rail", LEFT_SWITCH_RAIL);
    public static final Item RIGHT_SWITCH_RAIL_ITEM = registerItem("right_switch_rail", RIGHT_SWITCH_RAIL);

    public static final ResourceKey<CreativeModeTab> TAB_KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("rail_switch"));

    @Override
    public void onInitialize() {
        RailSwitchConfig.load();
        ServerTickEvents.START_LEVEL_TICK.register(SwitchDispatcher::onLevelTick);

        // 自带一个「道岔」创意标签，避免依赖原版标签键（26.2 已不再公开 CreativeModeTabs 常量）
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rail_switch"))
            .icon(() -> new ItemStack(LEFT_SWITCH_RAIL_ITEM))
            .displayItems((parameters, output) -> {
                output.accept(LEFT_SWITCH_RAIL_ITEM);
                output.accept(RIGHT_SWITCH_RAIL_ITEM);
            })
            .build());

        LOGGER.info("[rail_switch] 道岔已加载：仅在开启 minecart_improvements 新物理的维度工作");
    }

    /** 与原版铁轨一致的方块属性（非碰撞、可被水淹没的扁平方块）。 */
    private static BlockBehaviour.Properties blockProperties(String path) {
        return BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, id(path)))
            .noCollision()
            .strength(0.7F)
            .sound(SoundType.METAL);
    }

    private static Block registerBlock(String path, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), block);
    }

    private static Item registerItem(String path, Block block) {
        Item.Properties properties = new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id(path)))
            .useBlockDescriptionPrefix();
        return Registry.register(BuiltInRegistries.ITEM, id(path), new SwitchRailItem(block, properties));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
