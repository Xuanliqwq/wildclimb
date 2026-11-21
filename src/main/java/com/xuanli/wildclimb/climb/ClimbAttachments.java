package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Wildclimb;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ClimbAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Wildclimb.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ClimbData>> CLIMB =
            ATTACHMENT_TYPES.register("climb", () -> AttachmentType.builder(ClimbData::new).build());

    private ClimbAttachments() {}

    public static ClimbData get(net.minecraft.world.entity.player.Player player) {
        return player.getData(CLIMB);
    }
}
