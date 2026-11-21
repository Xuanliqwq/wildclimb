package com.xuanli.wildclimb.net;

import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.climb.ClimbClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：同步攀爬状态，让其他玩家也能看到攀爬动画。
 */
public record ClimbStatePayload(int entityId, boolean climbing, byte dir, int wallX, int wallZ,
                                int bottomSolidY, int topSolidY) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClimbStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Wildclimb.id("climb_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClimbStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeBoolean(payload.climbing);
                buf.writeByte(payload.dir);
                buf.writeInt(payload.wallX);
                buf.writeInt(payload.wallZ);
                buf.writeInt(payload.bottomSolidY);
                buf.writeInt(payload.topSolidY);
            },
            buf -> new ClimbStatePayload(buf.readVarInt(), buf.readBoolean(), buf.readByte(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClimbStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClimbClient.handleState(payload));
    }
}
