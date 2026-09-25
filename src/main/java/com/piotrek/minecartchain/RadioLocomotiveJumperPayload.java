package com.piotrek.minecartchain;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RadioLocomotiveJumperPayload(boolean isReverse, int bit, boolean targetState) implements CustomPacketPayload {
	public static final Type<RadioLocomotiveJumperPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "radio_locomotive_jumper"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RadioLocomotiveJumperPayload> CODEC = new StreamCodec<>() {
		@Override
		public RadioLocomotiveJumperPayload decode(final RegistryFriendlyByteBuf buffer) {
			return new RadioLocomotiveJumperPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean());
		}

		@Override
		public void encode(final RegistryFriendlyByteBuf buffer, final RadioLocomotiveJumperPayload payload) {
			buffer.writeBoolean(payload.isReverse());
			buffer.writeVarInt(payload.bit());
			buffer.writeBoolean(payload.targetState());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
