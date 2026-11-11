package com.botbuddy;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.NetworkState;
import org.jetbrains.annotations.Nullable;

/** A connection that goes nowhere: all packets to the bot are swallowed. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class FakeConnection extends ClientConnection {
    public FakeConnection() { super(NetworkSide.SERVERBOUND); }

    @Override public void send(Packet<?> packet) {}
    @Override public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
    @Override public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}
    @Override public void tryDisableAutoRead() {}
    @Override public void handleDisconnection() {}
    @Override public boolean isOpen() { return true; }
    @Override public void transitionInbound(NetworkState state, PacketListener listener) {}
    @Override public void transitionOutbound(NetworkState state) {}
}
