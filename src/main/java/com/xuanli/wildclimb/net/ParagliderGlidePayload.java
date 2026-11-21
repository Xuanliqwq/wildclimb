package com.xuanli.wildclimb.net;

import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.compat.ParagliderGlide;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：请求展开或收起背包里的滑翔伞（不移动任何物品）。
 */
public record ParagliderGlidePayload(boolean glide) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ParagliderGlidePayload> TYPE =
            new CustomPacketPayload.Type<>(Wildclimb.id("paraglider_glide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParagliderGlidePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.glide()),
            buf -> new ParagliderGlidePayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ParagliderGlidePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ParagliderGlide.request(player, payload.glide());
            }
        });
    }
}
