package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.client.ParagliderVisual;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.net.ParagliderGlidePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import tictim.paraglider.api.ParagliderAPI;

/**
 * 空中按下跳跃键开伞 / 再按一次收伞（不移动物品）。
 *
 * <p>落地、攀爬、坐骑、游泳、鞘翅飞行时都不干预；主手本来就拿着滑翔伞时交给 Paraglider 自己处理。</p>
 */
@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT)
public final class ParagliderAutoDeploy {
    private static boolean wasJumpDown;

    private ParagliderAutoDeploy() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;

        boolean jumpDown = Minecraft.getInstance().options.keyJump.isDown();
        boolean pressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;
        if (!pressed) return;

        if (!canToggle(player)) return;
        if (!ParagliderGlide.hasParaglider(player)) return;

        boolean gliding = ParagliderVisual.isParagliding(player);
        // 开伞受配置开关控制；收伞一直可用
        if (!gliding && !Config.autoDeployParaglider()) return;

        PacketDistributor.sendToServer(new ParagliderGlidePayload(!gliding));
    }

    private static boolean canToggle(LocalPlayer player) {
        if (player.isSpectator() || player.isPassenger()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isFallFlying() || player.getAbilities().flying) return false;
        if (ClimbAttachments.get(player).climbing) return false;
        // 已经拿在手上时由 Paraglider 自己处理（包括它的展开/关闭规则）
        return !player.getMainHandItem().is(ParagliderAPI.PARAGLIDERS);
    }
}
