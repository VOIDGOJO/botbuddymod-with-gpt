package com.botbuddy;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client. data = lines of "name\tgamemode\tmode\thealth". */
public record ListPayload(String status, String data) implements CustomPayload {
    public static final Id<ListPayload> ID = new Id<>(Identifier.of("botbuddy", "list"));
    public static final PacketCodec<RegistryByteBuf, ListPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, ListPayload::status,
        PacketCodecs.STRING, ListPayload::data,
        ListPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
