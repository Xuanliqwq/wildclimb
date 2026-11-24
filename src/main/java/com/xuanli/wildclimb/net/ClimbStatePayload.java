package com.xuanli.wildclimb.net;

import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.climb.ClimbClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes climbing attachment or one-shot animation state. */
public record ClimbStatePayload(int entityId, boolean climbing, byte dir, int wallX, int wallZ,
                                int bottomSolidY, int topSolidY, byte animation) implements CustomPacketPayload {
    public static final byte NONE = 0;
    public static final byte JUMP = 1;
    public static final byte MANTLE = 2;

    public ClimbStatePayload(int entityId, boolean climbing, byte dir, int wallX, int wallZ,
                             int bottomSolidY, int topSolidY) {
        this(entityId, climbing, dir, wallX, wallZ, bottomSolidY, topSolidY, NONE);
    }

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
                buf.writeByte(payload.animation);
            },
            buf -> new ClimbStatePayload(buf.readVarInt(), buf.readBoolean(), buf.readByte(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readByte()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClimbStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClimbClient.handleState(payload));
    }
}
