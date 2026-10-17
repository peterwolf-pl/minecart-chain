package com.piotrek.minecartchain.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartControlLayout;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.client.MinecartChainRenderState;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartRenderer.class)
public abstract class AbstractMinecartRendererMixin<T extends AbstractMinecart, S extends MinecartRenderState> {
	private static final double CHAIN_ATTACHMENT_OFFSET = 0.5D;
	private static final double CHAIN_HEIGHT = 0.35D;
	private static final float CHAIN_SEGMENT_SCALE = 0.22F;
	private static final float CONTROL_BASE_Y = 0.62F;
	private static final float CONTROL_LEVER_Y = 0.82F;
	private static final float CHIMNEY_Z = -0.06F;
	private static final float RECEIVER_Z = 0.08F;
	private static final float ANTENNA_Y = 0.94F;
	private static final float RECEIVER_XZ_SCALE = 0.44F;
	private static final float RECEIVER_Y_SCALE = 0.24F;
	private static final float ANTENNA_XZ_SCALE = 0.18F;
	private static final float ANTENNA_Y_SCALE = 0.48F;
	private static final float CONTROL_BASE_XZ_SCALE = 0.16F;
	private static final float CONTROL_BASE_Y_SCALE = 0.32F;
	private static final float CONTROL_LEVER_SCALE = 0.48F;
	private static final float CHIMNEY_Y = 0.95F;
	private static final float CHIMNEY_XZ_SCALE = 0.24F;
	private static final float CHIMNEY_Y_SCALE = 1.0F;
	private static final float LOCOMOTIVE_FLIP_THRESHOLD = 90.0F;
	private static final BlockState CHAIN_X_BLOCK = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
	private static final BlockState CHAIN_Z_BLOCK = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
	private static final BlockState LEVER_OFF = leverBlock(false);
	private static final BlockState LEVER_ON = leverBlock(true);
	private static final BlockState BRAKE_BASE = Blocks.RED_CONCRETE.defaultBlockState();
	private static final BlockState DIRECTION_BASE = Blocks.BLUE_CONCRETE.defaultBlockState();
	private static final BlockState THROTTLE_BASE = Blocks.BLACK_CONCRETE.defaultBlockState();
	private static final BlockState CHIMNEY_BLOCK = Blocks.BLACKSTONE.defaultBlockState();
	private static final BlockState ANTENNA_BLOCK = Blocks.IRON_BARS.defaultBlockState();

	private static BlockState resolveReceiverBlock() {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK
			.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("peterwolfs_txrx_redstone", "receiver"))
			.map(net.minecraft.world.level.block.Block::defaultBlockState)
			.orElse(Blocks.REDSTONE_BLOCK.defaultBlockState());
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V", at = @At("TAIL"))
	private void minecartChain$extractRenderState(final T minecart, final S state, final float tickProgress, final CallbackInfo ci) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		chainState.minecartChain$clearChainOffsets();
		this.minecartChain$appendChainOffset(minecart, chainState, data.minecartChain$getFirstLink().orElse(null), tickProgress);
		this.minecartChain$appendChainOffset(minecart, chainState, data.minecartChain$getSecondLink().orElse(null), tickProgress);

		boolean hasLever = minecart instanceof MinecartFurnace && data.minecartChain$hasEngineLever();
		chainState.minecartChain$setHasLever(hasLever);
		boolean isRadio = minecart instanceof MinecartFurnace && data.minecartChain$isRadioLocomotive();
		chainState.minecartChain$setRadioLocomotive(isRadio);
		if (!hasLever) {
			chainState.minecartChain$setFullThrottle(false);
			chainState.minecartChain$setReversed(false);
			chainState.minecartChain$setHasLocomotiveYaw(false);
			chainState.minecartChain$setLocomotiveYaw(0.0F);
			chainState.minecartChain$setMountedTrack(false);
			chainState.minecartChain$setFlipMountedTrackControls(false);
			chainState.minecartChain$setEngineActive(false);
			return;
		}

		chainState.minecartChain$setEngineActive(data.minecartChain$isEngineActive());
		chainState.minecartChain$setFullThrottle(data.minecartChain$isFullThrottle());
		chainState.minecartChain$setReversed(data.minecartChain$isReversed());
		chainState.minecartChain$setHasLocomotiveYaw(data.minecartChain$hasLocomotiveYaw());
		chainState.minecartChain$setLocomotiveYaw(data.minecartChain$getLocomotiveYaw());
		boolean mountedTrack = MinecartTrainLogic.usesMountedTrack(minecart);
		chainState.minecartChain$setMountedTrack(mountedTrack);
		chainState.minecartChain$setFlipMountedTrackControls(
			mountedTrack && MinecartTrainLogic.shouldFlipMountedTrackLocomotiveControls(minecart)
		);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void minecartChain$submitChains(
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState cameraRenderState,
		final CallbackInfo ci
	) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		if (chainState.minecartChain$chainOffsets().isEmpty()) {
			return;
		}

		poseStack.pushPose();
		if (!state.isNewRender && state.posOnRail != null) {
			double dy = (state.frontPos != null && state.backPos != null)
				? ((state.frontPos.y + state.backPos.y) / 2.0D - state.y)
				: 0.0D;
			poseStack.translate(state.posOnRail.x - state.x, dy, state.posOnRail.z - state.z);
		}

		for (Vec3 offset : chainState.minecartChain$chainOffsets()) {
			this.minecartChain$submitChainSegments(offset, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
		}
		poseStack.popPose();
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;submitMinecartContents(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", shift = At.Shift.AFTER)
	)
	private void minecartChain$submitLocomotiveControls(
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState cameraRenderState,
		final CallbackInfo ci
	) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		if (chainState.minecartChain$isRadioLocomotive()) {
			poseStack.pushPose();
			if (this.minecartChain$shouldFlipLocomotiveControls(state, chainState)) {
				poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
			}
			this.minecartChain$submitChimney(chainState, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
			this.minecartChain$submitReceiverModule(chainState, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
			poseStack.popPose();
			return;
		}

		if (!chainState.minecartChain$hasLever()) {
			return;
		}

		if (this.minecartChain$shouldFlipLocomotiveControls(state, chainState)) {
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		}

		this.minecartChain$submitChimney(chainState, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
		BlockState brakeLever = chainState.minecartChain$isEngineActive() ? LEVER_ON : LEVER_OFF;
		BlockState throttleLever = chainState.minecartChain$isFullThrottle() ? LEVER_ON : LEVER_OFF;
		BlockState directionLever = chainState.minecartChain$isReversed() ? LEVER_ON : LEVER_OFF;

		this.minecartChain$submitControl(
			BRAKE_BASE,
			brakeLever,
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.BRAKE_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
		this.minecartChain$submitControl(
			DIRECTION_BASE,
			directionLever,
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.DIRECTION_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
		this.minecartChain$submitControl(
			THROTTLE_BASE,
			throttleLever,
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.THROTTLE_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.AFTER)
	)
	private void minecartChain$restoreCartControlRotation(
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState cameraRenderState,
		final CallbackInfo ci
	) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		if (this.minecartChain$shouldFlipLocomotiveControls(state, chainState)) {
			poseStack.mulPose(Axis.YP.rotationDegrees(-180.0F));
		}
	}

	private boolean minecartChain$shouldFlipLocomotiveControls(final S state, final MinecartChainRenderState chainState) {
		if (!chainState.minecartChain$hasLever() || !chainState.minecartChain$hasLocomotiveYaw() || state.displayBlockState.isAir()) {
			return false;
		}
		if (chainState.minecartChain$isMountedTrack()) {
			return chainState.minecartChain$shouldFlipMountedTrackControls();
		}

		float visualYaw = minecartChain$visualYaw(state);
		float difference = Mth.degreesDifferenceAbs(chainState.minecartChain$getLocomotiveYaw(), visualYaw);
		return difference > LOCOMOTIVE_FLIP_THRESHOLD;
	}

	private static float minecartChain$visualYaw(final MinecartRenderState state) {
		if (!state.isNewRender) {
			if (state.frontPos != null && state.backPos != null) {
				Vec3 visualDirection = state.backPos.subtract(state.frontPos).horizontal();
				if (visualDirection.lengthSqr() > 1.0E-5D) {
					return minecartChain$yawFromDirection(visualDirection);
				}
			}
			return Mth.wrapDegrees(state.yRot);
		}

		return Mth.wrapDegrees(state.yRot);
	}

	private static float minecartChain$yawFromDirection(final Vec3 direction) {
		return (float) (Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) + 90.0F;
	}

	private void minecartChain$submitControl(
		final BlockState baseState,
		final BlockState leverState,
		final float x,
		final float z,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		poseStack.pushPose();
		poseStack.translate(x, CONTROL_BASE_Y, z);
		poseStack.scale(CONTROL_BASE_XZ_SCALE, CONTROL_BASE_Y_SCALE, CONTROL_BASE_XZ_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitBlock(poseStack, baseState, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(x, CONTROL_LEVER_Y, z);
		poseStack.scale(CONTROL_LEVER_SCALE, CONTROL_LEVER_SCALE, CONTROL_LEVER_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitBlock(poseStack, leverState, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	private void minecartChain$submitChimney(
		final MinecartChainRenderState chainState,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		poseStack.pushPose();
		poseStack.translate(0.0F, CHIMNEY_Y, CHIMNEY_Z);
		poseStack.scale(CHIMNEY_XZ_SCALE, CHIMNEY_Y_SCALE, CHIMNEY_XZ_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitBlock(poseStack, CHIMNEY_BLOCK, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	private void minecartChain$submitReceiverModule(
		final MinecartChainRenderState chainState,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		poseStack.pushPose();
		poseStack.translate(0.0F, CONTROL_BASE_Y, RECEIVER_Z);
		poseStack.scale(RECEIVER_XZ_SCALE, RECEIVER_Y_SCALE, RECEIVER_XZ_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitBlock(poseStack, resolveReceiverBlock(), lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.0F, ANTENNA_Y, RECEIVER_Z);
		poseStack.scale(ANTENNA_XZ_SCALE, ANTENNA_Y_SCALE, ANTENNA_XZ_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitBlock(poseStack, ANTENNA_BLOCK, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	private void minecartChain$appendChainOffset(
		final T minecart,
		final MinecartChainRenderState chainState,
		final UUID linkedId,
		final float tickProgress
	) {
		if (linkedId == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}

		Entity linkedEntity = client.level.getEntity(linkedId);
		if (!(linkedEntity instanceof AbstractMinecart linkedMinecart)) {
			return;
		}

		if (minecart.getId() > linkedMinecart.getId()) {
			return;
		}

		Vec3 selfPos = minecart.getPosition(tickProgress);
		Vec3 targetPos = linkedMinecart.getPosition(tickProgress);
		chainState.minecartChain$addChainOffset(targetPos.subtract(selfPos));
	}

	private void minecartChain$submitChainSegments(
		final Vec3 offset,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		double horizontalDistance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
		if (horizontalDistance < 1.0E-4D) {
			return;
		}

		Vec3 direction = offset.normalize();
		double totalDistance = offset.length();
		double span = Math.max(0.0D, totalDistance - CHAIN_ATTACHMENT_OFFSET * 2.0D);
		int segments = Math.max(1, (int) Math.round(span / 0.18D));
		double step = span / segments;
		float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) + 90.0F;
		float pitch = (float) (Math.asin(direction.y) * (180.0D / Math.PI));

		for (int i = 0; i < segments; i++) {
			double distance = CHAIN_ATTACHMENT_OFFSET + (i + 0.5D) * step;
			Vec3 position = direction.scale(distance).add(0.0D, CHAIN_HEIGHT, 0.0D);
			poseStack.pushPose();
			poseStack.translate(position.x, position.y, position.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
			poseStack.scale(CHAIN_SEGMENT_SCALE, CHAIN_SEGMENT_SCALE, CHAIN_SEGMENT_SCALE);
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			submitNodeCollector.submitBlock(poseStack, CHAIN_Z_BLOCK, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
			poseStack.popPose();
		}
	}

	private static BlockState leverBlock(final boolean powered) {
		return Blocks.LEVER.defaultBlockState()
			.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
			.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
			.setValue(LeverBlock.POWERED, powered);
	}
}
