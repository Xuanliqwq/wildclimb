package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Wildclimb;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT)
public final class ClimbClientEvents {
    private ClimbClientEvents() {}

    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        ClimbClient.onPlayerTickPre(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        ClimbClient.onPlayerTick(event.getEntity());
    }
}
