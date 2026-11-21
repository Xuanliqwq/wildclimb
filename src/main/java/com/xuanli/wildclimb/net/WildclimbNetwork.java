package com.xuanli.wildclimb.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WildclimbNetwork {
    private WildclimbNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ClimbActionPayload.TYPE, ClimbActionPayload.STREAM_CODEC, ClimbActionPayload::handle);
        registrar.playToServer(ParagliderGlidePayload.TYPE, ParagliderGlidePayload.STREAM_CODEC,
                ParagliderGlidePayload::handle);
        registrar.playToClient(ClimbStatePayload.TYPE, ClimbStatePayload.STREAM_CODEC, ClimbStatePayload::handle);
    }
}
