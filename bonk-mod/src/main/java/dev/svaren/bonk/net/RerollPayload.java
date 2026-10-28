package dev.svaren.bonk.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by the client when the reroll key is pressed while looking at a villager. */
public record RerollPayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RerollPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("bonk", "reroll"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RerollPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, RerollPayload::entityId, RerollPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
