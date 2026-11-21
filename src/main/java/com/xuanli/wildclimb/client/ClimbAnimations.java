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
 * <p>三层动画：{@code climb_idle}（挂在墙上休息）、{@code climb_move}（手脚交替摆动向上爬）、
 * {@code climb_edge}（双手抓住墙顶边缘）。</p>
 */
@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClimbAnimations {
    public static final ResourceLocation LAYER = Wildclimb.id("climb");
    public static final ResourceLocation CLIMB_IDLE = Wildclimb.id("climb_idle");
    public static final ResourceLocation CLIMB_MOVE = Wildclimb.id("climb_move");
    public static final ResourceLocation CLIMB_EDGE = Wildclimb.id("climb_edge");

    /** 玩法动画，优先级高于表情等装饰性动画。 */
    private static final int PRIORITY = 1500;

    private static final Map<ResourceLocation, CachedAnimation> CACHE = new HashMap<>();

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
        // 翻上墙顶的动画期间保持“抓住边缘”的姿势
        boolean mantling = data.mantleTicks > 0;
        if (!data.climbing && !mantling) return PlayState.STOP;

        ResourceLocation id;
        if (mantling || data.atTop) {
            id = CLIMB_EDGE;
        } else if (data.moving || data.jumpVelocity > 0.0D) {
            id = CLIMB_MOVE;
        } else {
            id = CLIMB_IDLE;
        }

        RawAnimation raw = rawAnimation(id);
        if (raw == null) return PlayState.STOP;
        return animSetter.setAnimation(raw);
    }

    /**
     * 动画资源在资源包重载时会被重新加载，所以这里按实例身份判断缓存是否还有效。
     */
    private static @Nullable RawAnimation rawAnimation(ResourceLocation id) {
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            CACHE.remove(id);
            return null;
        }
        CachedAnimation cached = CACHE.get(id);
        if (cached != null && cached.animation == animation) return cached.raw;
        RawAnimation raw = RawAnimation.begin().thenLoop(animation);
        CACHE.put(id, new CachedAnimation(animation, raw));
        return raw;
    }

    private record CachedAnimation(Animation animation, RawAnimation raw) {
    }
}
