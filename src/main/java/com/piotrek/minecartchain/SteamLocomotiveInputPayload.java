package com.piotrek.minecartchain;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SteamLocomotiveInputPayload(float throttle, boolean reverser, boolean toggleCoupler) implements CustomPacketPayload {
	public static final Type<SteamLocomotiveInputPayload> TYPE =
		new Type<>(MinecartChainMod.id("steam_locomotive_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SteamLocomotiveInputPayload> CODEC = new StreamCodec<>() {
		@Override
		public SteamLocomotiveInputPayload decode(final RegistryFriendlyByteBuf buffer) {
			return new SteamLocomotiveInputPayload(buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean());
		}

		@Override
		public void encode(final RegistryFriendlyByteBuf buffer, final SteamLocomotiveInputPayload payload) {
			buffer.writeFloat(payload.throttle());
			buffer.writeBoolean(payload.reverser());
			buffer.writeBoolean(payload.toggleCoupler());
		}
	};

	public SteamLocomotiveInputPayload(final float throttle, final boolean reverser) {
		this(throttle, reverser, false);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
