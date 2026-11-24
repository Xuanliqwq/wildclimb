package com.xuanli.wildclimb.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.AnimationSnapshot;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.bones.AdvancedBoneSnapshot;
import com.zigythebird.playeranimcore.easing.EasingType;
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

/** Selects climbing animation clips and blends one-shot jumps with the active pose. */
@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT)
public final class ClimbAnimations {
    public static final ResourceLocation LAYER = Wildclimb.id("climb");
    public static final ResourceLocation CLIMB_IDLE = Wildclimb.id("climb_idle");
    public static final ResourceLocation CLIMB_MOVE = Wildclimb.id("climb_move");
    private static final ResourceLocation CLIMB_DESCEND = Wildclimb.id("climb_descend");
    public static final ResourceLocation CLIMB_JUMP = Wildclimb.id("climb_jump");
    private static final ResourceLocation TRAVERSE_RIGHT = Wildclimb.id("climb_traverse_right");
    private static final ResourceLocation TRAVERSE_LEFT = Wildclimb.id("climb_traverse_left");
    public static final ResourceLocation CLIMB_EDGE = Wildclimb.id("climb_edge");
    public static final ResourceLocation CLIMB_MANTLE = Wildclimb.id("climb_mantle");

    private static final int PRIORITY = 1500;

    private static final Map<ResourceLocation, CachedAnimation> LOOPING = new HashMap<>();
    private static final Map<ResourceLocation, CachedAnimation> ONE_SHOT = new HashMap<>();

    private ClimbAnimations() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER,
                PRIORITY,
                ClimbAnimationController::new));
    }

    private static final class ClimbAnimationController extends PlayerAnimationController {
        private final ClimbData climb;

        private ClimbAnimationController(AbstractClientPlayer player) {
            super(player, (controller, state, animSetter) -> handle(player, animSetter));
            climb = ClimbAttachments.get(player);
        }

        @Override
        public float getAnimationSpeed() {
            return climb.moving && climb.cornerTicks == 0 && climb.mantleTicks == 0
                    && climb.jumpAnimationTicks == 0 ? climb.animationSpeed : 1.0F;
        }

        @Override
        protected void setAnimation(RawAnimation next, float startTick) {
            RawAnimation current = getCurrentRawAnimation();
            RawAnimation jump = rawAnimation(CLIMB_JUMP, false);
            if (current != null && next != null && current != next && (current == jump || next == jump)) {
                // Blend from the current bone pose without waiting for a particular step in the climb cycle.
                Map<String, AdvancedBoneSnapshot> pose = new HashMap<>();
                activeBones.forEach((name, bone) -> pose.put(name, new AdvancedBoneSnapshot(bone)));
                int fadeTicks = next == jump ? 2 : 4;
                AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE);
                fade.setTransitionAnimation(new AnimationSnapshot(pose));
                removeModifierIf(AbstractFadeModifier.class::isInstance);
                addModifierLast(fade);
            }
            super.setAnimation(next, startTick);
        }
    }

    private static PlayState handle(AbstractClientPlayer player, AnimationController.AnimationSetter animSetter) {
        ClimbData data = ClimbAttachments.get(player);
        boolean mantling = data.mantleTicks > 0;
        if (!data.climbing && !mantling) {
            // Clear the current PAL clip explicitly when climbing ends.
            animSetter.setAnimation(null);
            return PlayState.STOP;
        }

        if (mantling) {
            RawAnimation mantle = rawAnimation(CLIMB_MANTLE, false);
            if (mantle == null) return PlayState.STOP;
            return animSetter.setAnimation(mantle);
        }

        if (data.jumpAnimationTicks > 0) {
            RawAnimation jump = rawAnimation(CLIMB_JUMP, false);
            if (jump != null) return animSetter.setAnimation(jump);
        }

        ResourceLocation id;
        if (data.traverseDirection != 0) {
            id = data.traverseDirection > 0 ? TRAVERSE_RIGHT : TRAVERSE_LEFT;
        } else if (data.atTop) {
            id = CLIMB_EDGE;
        } else {
            id = data.moving ? (data.descending ? CLIMB_DESCEND : CLIMB_MOVE) : CLIMB_IDLE;
        }
        RawAnimation raw = rawAnimation(id, true);
        if (raw == null) return PlayState.STOP;
        return animSetter.setAnimation(raw);
    }

    /** Reuses constructed clips while their underlying resource instances remain current. */
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

        RawAnimation raw = RawAnimation.begin();
        if (loop) {
            raw.thenLoop(animation);
        } else if (CLIMB_JUMP.equals(id)) {
            // Keep the regrip pose available until the climbing state requests a blend out.
            raw.thenPlayAndHold(animation);
        } else {
            raw.thenPlay(animation);
        }
        cache.put(id, new CachedAnimation(animation, raw));
        return raw;
    }

    private record CachedAnimation(Animation animation, RawAnimation raw) {
    }
}
