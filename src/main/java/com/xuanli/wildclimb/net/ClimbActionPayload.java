package com.xuanli.wildclimb.net;

import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.climb.ClimbServer;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：攀爬动作。
 *
 * <p>抓墙时会把客户端算出来的那一段墙面一起发过去，服务端用它来做校验。
 * 服务端的玩家坐标比客户端慢一刻，如果服务端自己重新探测墙面，起跳中途抓墙很容易被误判成无效。</p>
 */
public record ClimbActionPayload(byte action, byte dir, boolean flag,
                                 int wallX, int wallZ, int bottomSolidY, int topSolidY) implements CustomPacketPayload {
    public static final byte START = 0;
    public static final byte JUMP = 1;
    public static final byte STOP = 2;
    public static final byte MANTLE = 3;
    /** 横向攀爬绕过墙角，切换到相邻的那一面墙。 */
    public static final byte TURN = 4;

    public static final CustomPacketPayload.Type<ClimbActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Wildclimb.id("climb_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClimbActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.action);
                buf.writeByte(payload.dir);
                buf.writeBoolean(payload.flag);
                buf.writeInt(payload.wallX);
                buf.writeInt(payload.wallZ);
                buf.writeInt(payload.bottomSolidY);
                buf.writeInt(payload.topSolidY);
            },
            buf -> new ClimbActionPayload(buf.readByte(), buf.readByte(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));

    public static ClimbActionPayload start(Direction dir, int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        return new ClimbActionPayload(START, (byte) dir.ordinal(), false, wallX, wallZ, bottomSolidY, topSolidY);
    }

    public static ClimbActionPayload jump() {
        return new ClimbActionPayload(JUMP, (byte) 0, false, 0, 0, 0, 0);
    }

    public static ClimbActionPayload stop() {
        return new ClimbActionPayload(STOP, (byte) 0, false, 0, 0, 0, 0);
    }

    public static ClimbActionPayload mantle() {
        return new ClimbActionPayload(MANTLE, (byte) 0, false, 0, 0, 0, 0);
    }

    public static ClimbActionPayload turn(Direction dir, int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        return new ClimbActionPayload(TURN, (byte) dir.ordinal(), false, wallX, wallZ, bottomSolidY, topSolidY);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClimbActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Direction direction = Direction.values()[Math.floorMod((int) payload.dir, Direction.values().length)];
            switch (payload.action) {
                case START -> ClimbServer.handleStart(player, direction,
                        payload.wallX, payload.wallZ, payload.bottomSolidY, payload.topSolidY);
                case JUMP -> ClimbServer.handleJump(player);
                case STOP, MANTLE -> ClimbServer.handleStop(player);
                case TURN -> ClimbServer.handleTurn(player, direction,
                        payload.wallX, payload.wallZ, payload.bottomSolidY, payload.topSolidY);
                default -> {
                }
            }
        });
    }
}
