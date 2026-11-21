package com.xuanli.wildclimb;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口。只负责配置文件界面，其余客户端逻辑见 {@code climb.ClimbClientEvents}
 * 与 {@code client.ClimbAnimations}。
 */
@Mod(value = Wildclimb.MODID, dist = Dist.CLIENT)
public final class WildclimbClient {
    public WildclimbClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
