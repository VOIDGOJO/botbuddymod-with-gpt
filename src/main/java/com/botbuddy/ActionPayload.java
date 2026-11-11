package com.botbuddy;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server. action: LIST, ADD, RENAME, INV, CMD. target: bot name, or "*" for all bots. */
public record ActionPayload(String action, String target, String arg) implements CustomPayload {
    public static final Id<ActionPayload> ID = new Id<>(Identifier.of("botbuddy", "action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, ActionPayload::action,
        PacketCodecs.STRING, ActionPayload::target,
        PacketCodecs.STRING, ActionPayload::arg,
        ActionPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
