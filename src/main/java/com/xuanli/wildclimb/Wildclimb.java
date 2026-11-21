package com.xuanli.wildclimb;

import com.mojang.logging.LogUtils;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.compat.ParagliderStateConfig;
import com.xuanli.wildclimb.net.WildclimbNetwork;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * 旷野攀爬 / Wildclimb
 *
 * <p>A Breath of the Wild style climbing system built on top of Paraglider's stamina system.
 * Players can grab walls that are at least two blocks tall and climb them, spending stamina,
 * until they either reach the top or run out of stamina.</p>
 */
@Mod(Wildclimb.MODID)
public final class Wildclimb {
    /** The mod id, must match the value in META-INF/neoforge.mods.toml. */
    public static final String MODID = "wildclimb";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Wildclimb(IEventBus modEventBus, ModContainer modContainer) {
        // Per-player climbing state. Not persisted: climbing is a transient state and is
        // always ended when a player leaves the world.
        ClimbAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(WildclimbNetwork::registerPayloads);
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 构造阶段所有模组都还没有读取配置文件，此时修改 Paraglider 的状态配置最稳妥，
        // 本次启动就会生效。
        ParagliderStateConfig.apply(FMLPaths.CONFIGDIR.get());

        // 进入世界后再检查一次：配置文件可能是这一局才生成的，改完让 Paraglider 立即重载
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            if (ParagliderStateConfig.apply(FMLPaths.CONFIGDIR.get())) {
                reloadParagliderStates(event.getServer());
            }
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ParagliderStateConfig.apply(FMLPaths.CONFIGDIR.get());
    }

    private static void reloadParagliderStates(MinecraftServer server) {
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "paraglider reloadPlayerStates");
            LOGGER.info("[wildclimb] 已让 Paraglider 重新加载玩家状态配置");
        } catch (Exception e) {
            LOGGER.warn("[wildclimb] 执行 Paraglider 重载命令失败，配置将在下次启动时生效", e);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
