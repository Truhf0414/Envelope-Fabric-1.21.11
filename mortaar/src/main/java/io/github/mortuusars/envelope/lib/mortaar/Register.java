package io.github.mortuusars.envelope.lib.mortaar;

import io.github.mortuusars.envelope.lib.mortaar.network.packet.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class Register {
    /**
     * Gets or creates mod-specific registrar, that allows for easy registering of blocks, items, block-entities, menu-types, etc.,
     * without needing to handle platform specific code,
     * @return First call per modId will create the registrar, subsequent calls will return the same instance.
     */
    public static Registrar registrar(String modId) {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.RegisterImpl.registrar(modId);
    }

    // --

    /**
     * Registers serverbound (play to server) packet.
     */
    public static void serverboundPacket(CustomPacketPayload.Type<? extends Packet> type, StreamCodec<? extends FriendlyByteBuf, ? extends Packet> codec) {
        io.github.mortuusars.envelope.lib.mortaar.fabric.RegisterImpl.serverboundPacket(type, codec);
    }

    /**
     * Registers clientbound (play to client) packet.
     */
    public static void clientboundPacket(CustomPacketPayload.Type<? extends Packet> type, StreamCodec<? extends FriendlyByteBuf, ? extends Packet> codec) {
        io.github.mortuusars.envelope.lib.mortaar.fabric.RegisterImpl.clientboundPacket(type, codec);
    }

    /**
     * Registers bidirectional (play to server and play to client) packet.
     */
    public static void bidirectionalPacket(CustomPacketPayload.Type<? extends Packet> type, StreamCodec<? extends FriendlyByteBuf, ? extends Packet> codec) {
        io.github.mortuusars.envelope.lib.mortaar.fabric.RegisterImpl.bidirectionalPacket(type, codec);
    }
}
