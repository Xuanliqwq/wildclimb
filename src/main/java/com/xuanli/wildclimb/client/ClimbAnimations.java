package com.xuanli.wildclimb.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;
import com.xuanli.wildclimb.climb.ClimbHelper;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 攀爬动画层（基于 Player Animation Library）。
 *
 * <p>循环动画：{@code climb_idle}（挂在墙上休息）、{@code climb_move}（手脚交替摆动向上爬）、
 * {@code climb_edge}（双手抓住墙顶边缘）；翻上墙顶用一次性的 {@code climb_mantle}
 * （先用手臂撑起上半身，再收腿站上台面）。</p>
 */
@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClimbAnimations {
    public static final ResourceLocation LAYER = Wildclimb.id("climb");
    public static final ResourceLocation CLIMB_IDLE = Wildclimb.id("climb_idle");
    public static final ResourceLocation CLIMB_MOVE = Wildclimb.id("climb_move");
    public static final ResourceLocation CLIMB_EDGE = Wildclimb.id("climb_edge");
    public static final ResourceLocation CLIMB_MANTLE = Wildclimb.id("climb_mantle");

    /** 玩法动画，优先级高于表情等装饰性动画。 */
    private static final int PRIORITY = 1500;

    private static final Map<ResourceLocation, CachedAnimation> LOOPING = new HashMap<>();
    private static final Map<ResourceLocation, CachedAnimation> ONE_SHOT = new HashMap<>();

    private ClimbAnimations() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // NeoForge 上必须在 enqueueWork 里注册
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER,
                PRIORITY,
                player -> new PlayerAnimationController(player,
                        (controller, state, animSetter) -> handle(player, animSetter))));
    }

    private static PlayState handle(AbstractClientPlayer player, AnimationController.AnimationSetter animSetter) {
        ClimbData data = ClimbAttachments.get(player);
        boolean mantling = data.mantleTicks > 0;
        boolean climbing = data.climbing;
        // 已经离开墙面（跳离、掉落）时立刻收掉攀爬姿势，别等状态包同步
        if (climbing && !mantling && data.graceTicks <= 0 && data.cornerTicks <= 0
                && !ClimbHelper.canStillClimb(player, data)) {
            climbing = false;
        }
        if (!climbing && !mantling) {
            // 只返回 STOP 不会清掉已经应用的姿势，必须显式 setAnimation(null) 才会让 PAL 调用 stop()
            animSetter.setAnimation(null);
            return PlayState.STOP;
        }

        // 翻上墙顶：一次性过渡动画（撑起上半身 → 收腿站起），播完就交给原版姿势
        if (mantling) {
            RawAnimation mantle = rawAnimation(CLIMB_MANTLE, false);
            if (mantle == null) return PlayState.STOP;
            return animSetter.setAnimation(mantle);
        }

        ResourceLocation id = data.atTop
                ? CLIMB_EDGE
                : (data.moving || data.jumpVelocity > 0.0D ? CLIMB_MOVE : CLIMB_IDLE);
        RawAnimation raw = rawAnimation(id, true);
        if (raw == null) return PlayState.STOP;
        return animSetter.setAnimation(raw);
    }

    /**
     * 动画资源在资源包重载时会被重新加载，所以这里按实例身份判断缓存是否还有效。
     *
     * @param loop {@code true} 循环播放，{@code false} 只播一次
     */
    private static @Nullable RawAnimation rawAnimation(ResourceLocation id, boolean loop) {
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            LOOPING.remove(id);
            ONE_SHOT.remove(id);
            return null;
        }
        Map<ResourceLocation, CachedAnimation> cache = loop ? LOOPING : ONE_SHOT;
        CachedAnimation cached = cache.get(id);
        if (cached != null && cached.animation == animation) return cached.raw;

        RawAnimation raw = loop
                ? RawAnimation.begin().thenLoop(animation)
                : RawAnimation.begin().thenPlay(animation);
        cache.put(id, new CachedAnimation(animation, raw));
        return raw;
    }

    private record CachedAnimation(Animation animation, RawAnimation raw) {
    }
}
