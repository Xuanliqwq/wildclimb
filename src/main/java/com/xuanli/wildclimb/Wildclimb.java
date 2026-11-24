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

/** Wild Climb common entry point. */
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

        // Patch existing Paraglider state configuration before it is loaded.
        ParagliderStateConfig.apply(FMLPaths.CONFIGDIR.get());

        // Check again when the server starts, since Paraglider may have just generated the file.
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
            LOGGER.info("[wildclimb] Reloaded Paraglider player state configuration");
        } catch (Exception e) {
            LOGGER.warn("[wildclimb] Could not reload Paraglider player states; changes will apply at the next startup", e);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
