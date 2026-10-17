package com.piotrek.minecartchain.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartControlLayout;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.client.MinecartChainRenderState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartRenderer.class)
public abstract class AbstractMinecartRendererMixin<T extends AbstractMinecart, S extends MinecartRenderState> {
	private static final double CHAIN_ATTACHMENT_OFFSET = 0.62D;
	private static final double CHAIN_HEIGHT = 0.38D;
	private static final double CHAIN_SEGMENT_SPACING = 0.42D;
	private static final float CHAIN_SEGMENT_SCALE = 0.46F;
	private static final float CONTROL_BASE_Y = 0.43F;
	private static final float CONTROL_LEVER_Y = 0.44F;
	private static final float CONTROL_BASE_X_SCALE = 0.08F;
	private static final float CONTROL_BASE_Y_SCALE = 0.32F;
	private static final float CONTROL_BASE_Z_SCALE = 0.28F;
	private static final float CONTROL_LEVER_SCALE = 0.48F;
	private static final float CHIMNEY_Y = 0.95F;
	private static final float CHIMNEY_XZ_SCALE = 0.24F;
	private static final float CHIMNEY_Y_SCALE = 1.0F;
	private static final float LOCOMOTIVE_FLIP_THRESHOLD = 90.0F;
	private static final BlockState CHAIN_X_BLOCK = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
	private static final BlockState CHAIN_Z_BLOCK = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
	private static final BlockState LEVER_OFF = leverBlock(false);
	private static final BlockState LEVER_ON = leverBlock(true);
	private static final BlockState BRAKE_BASE = Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState();
	private static final BlockState DIRECTION_BASE = Blocks.CONCRETE.pick(DyeColor.BLUE).defaultBlockState();
	private static final BlockState THROTTLE_BASE = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
	private static final BlockState CHIMNEY_BLOCK = Blocks.BLACKSTONE.defaultBlockState();
	private static final BlockState ANTENNA_BLOCK = Blocks.IRON_BARS.defaultBlockState();

	private static BlockState resolveReceiverBlock() {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK
			.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("peterwolfs_txrx_redstone", "receiver"))
			.map(net.minecraft.world.level.block.Block::defaultBlockState)
			.orElse(Blocks.REDSTONE_BLOCK.defaultBlockState());
	}

	@Shadow
	@Final
	private BlockModelResolver blockModelResolver;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V", at = @At("TAIL"))
	private void minecartChain$extractRenderState(final T minecart, final S state, final float tickProgress, final CallbackInfo ci) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		MinecartChainAccess data = (MinecartChainAccess) minecart;
		chainState.minecartChain$clearChainOffsets();
		this.minecartChain$appendChainOffset(minecart, chainState, data.minecartChain$getFirstLink(), tickProgress);
		this.minecartChain$appendChainOffset(minecart, chainState, data.minecartChain$getSecondLink(), tickProgress);
		if (!chainState.minecartChain$chainOffsets().isEmpty()) {
			this.blockModelResolver.update(chainState.minecartChain$chainXModel(), CHAIN_X_BLOCK, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
			this.blockModelResolver.update(chainState.minecartChain$chainZModel(), CHAIN_Z_BLOCK, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		}

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
			chainState.minecartChain$leverModel().clear();
			chainState.minecartChain$throttleLeverModel().clear();
			chainState.minecartChain$directionLeverModel().clear();
			chainState.minecartChain$brakeBaseModel().clear();
			chainState.minecartChain$directionBaseModel().clear();
			chainState.minecartChain$throttleBaseModel().clear();
			chainState.minecartChain$chimneyModel().clear();
			chainState.minecartChain$receiverModel().clear();
			chainState.minecartChain$antennaModel().clear();
			return;
		}

		chainState.minecartChain$setFullThrottle(data.minecartChain$isFullThrottle());
		chainState.minecartChain$setReversed(data.minecartChain$isReversed());
		chainState.minecartChain$setHasLocomotiveYaw(data.minecartChain$hasLocomotiveYaw());
		chainState.minecartChain$setLocomotiveYaw(data.minecartChain$getLocomotiveYaw());
		boolean mountedTrack = MinecartTrainLogic.usesMountedTrack(minecart);
		chainState.minecartChain$setMountedTrack(mountedTrack);
		chainState.minecartChain$setFlipMountedTrackControls(
			mountedTrack && MinecartTrainLogic.shouldFlipMountedTrackLocomotiveControls(minecart)
		);

		this.blockModelResolver.update(chainState.minecartChain$chimneyModel(), CHIMNEY_BLOCK, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);

		if (isRadio) {
			chainState.minecartChain$leverModel().clear();
			chainState.minecartChain$throttleLeverModel().clear();
			chainState.minecartChain$directionLeverModel().clear();
			chainState.minecartChain$brakeBaseModel().clear();
			chainState.minecartChain$directionBaseModel().clear();
			chainState.minecartChain$throttleBaseModel().clear();
			this.blockModelResolver.update(chainState.minecartChain$receiverModel(), resolveReceiverBlock(), AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
			this.blockModelResolver.update(chainState.minecartChain$antennaModel(), ANTENNA_BLOCK, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
			return;
		}

		chainState.minecartChain$receiverModel().clear();
		chainState.minecartChain$antennaModel().clear();

		BlockState brakeLeverState = data.minecartChain$isEngineActive() ? LEVER_ON : LEVER_OFF;
		BlockState throttleLeverState = chainState.minecartChain$isFullThrottle() ? LEVER_ON : LEVER_OFF;
		BlockState directionLeverState = chainState.minecartChain$isReversed() ? LEVER_ON : LEVER_OFF;
		this.blockModelResolver.update(chainState.minecartChain$leverModel(), brakeLeverState, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		this.blockModelResolver.update(chainState.minecartChain$throttleLeverModel(), throttleLeverState, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		this.blockModelResolver.update(chainState.minecartChain$directionLeverModel(), directionLeverState, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		this.blockModelResolver.update(chainState.minecartChain$brakeBaseModel(), BRAKE_BASE, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		this.blockModelResolver.update(chainState.minecartChain$directionBaseModel(), DIRECTION_BASE, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
		this.blockModelResolver.update(chainState.minecartChain$throttleBaseModel(), THROTTLE_BASE, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
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
				? (state.frontPos.y + state.backPos.y) * 0.5D - state.y
				: 0.0D;
			poseStack.translate(state.posOnRail.x - state.x, dy, state.posOnRail.z - state.z);
		}

		for (Vec3 offset : chainState.minecartChain$chainOffsets()) {
			this.minecartChain$submitChain(offset, chainState, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
		}
		poseStack.popPose();
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelRenderState;isEmpty()Z")
	)
	private void minecartChain$submitCartControls(
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState cameraRenderState,
		final CallbackInfo ci
	) {
		MinecartChainRenderState chainState = (MinecartChainRenderState) state;
		if (!chainState.minecartChain$hasLever()) {
			return;
		}

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

		if (chainState.minecartChain$leverModel().isEmpty()) {
			return;
		}

		if (this.minecartChain$shouldFlipLocomotiveControls(state, chainState)) {
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		}

		this.minecartChain$submitChimney(chainState, poseStack, submitNodeCollector, state.lightCoords, state.outlineColor);
		this.minecartChain$submitControl(
			chainState.minecartChain$brakeBaseModel(),
			chainState.minecartChain$leverModel(),
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.BRAKE_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
		this.minecartChain$submitControl(
			chainState.minecartChain$directionBaseModel(),
			chainState.minecartChain$directionLeverModel(),
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.DIRECTION_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
		this.minecartChain$submitControl(
			chainState.minecartChain$throttleBaseModel(),
			chainState.minecartChain$throttleLeverModel(),
			(float) MinecartControlLayout.REAR_CONTROL_X,
			(float) MinecartControlLayout.THROTTLE_CONTROL_Z,
			poseStack,
			submitNodeCollector,
			state.lightCoords,
			state.outlineColor
		);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
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
		if (!chainState.minecartChain$hasLever() || !chainState.minecartChain$hasLocomotiveYaw() || state.displayBlockModel.isEmpty()) {
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

		// NewMinecartBehavior stores the yaw applied directly to the cart pose.
		// The locomotive's local front is -X, so its world-space front yaw is
		// 180 - pose yaw. Treating entity yaw like a normal forward-vector yaw
		// introduced a constant 90-degree offset and made the flip test oscillate
		// while traversing a curve.
		return Mth.wrapDegrees(180.0F - state.yRot);
	}

	private static float minecartChain$yawFromDirection(final Vec3 direction) {
		return Mth.wrapDegrees((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG));
	}

	private void minecartChain$submitControl(
		final BlockModelRenderState baseModel,
		final BlockModelRenderState leverModel,
		final float offsetX,
		final float offsetZ,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		poseStack.pushPose();
		poseStack.translate(offsetX, CONTROL_BASE_Y, offsetZ);
		poseStack.scale(CONTROL_BASE_X_SCALE, CONTROL_BASE_Y_SCALE, CONTROL_BASE_Z_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		baseModel.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(offsetX, CONTROL_LEVER_Y, offsetZ);
		poseStack.scale(CONTROL_LEVER_SCALE, CONTROL_LEVER_SCALE, CONTROL_LEVER_SCALE);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		leverModel.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	private void minecartChain$submitChimney(
		final MinecartChainRenderState chainState,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		if (chainState.minecartChain$chimneyModel().isEmpty()) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate((float) MinecartControlLayout.CHIMNEY_RENDER_X, CHIMNEY_Y, (float) MinecartControlLayout.CHIMNEY_RENDER_Z);
		poseStack.scale(CHIMNEY_XZ_SCALE, CHIMNEY_Y_SCALE, CHIMNEY_XZ_SCALE);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		chainState.minecartChain$chimneyModel().submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	private void minecartChain$submitReceiverModule(
		final MinecartChainRenderState chainState,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		if (chainState.minecartChain$receiverModel().isEmpty()) {
			return;
		}

		// Receiver carpet unit on rear deck
		poseStack.pushPose();
		poseStack.translate(0.35F, 0.44F, 0.0F);
		poseStack.scale(0.55F, 0.35F, 0.7F);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		chainState.minecartChain$receiverModel().submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();

		// Antenna rod
		if (!chainState.minecartChain$antennaModel().isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.52F, 0.72F, 0.22F);
			poseStack.scale(0.12F, 0.45F, 0.12F);
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			chainState.minecartChain$antennaModel().submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
			poseStack.popPose();
		}
	}

	private static Vec3 minecartChain$visualPosition(final AbstractMinecart cart, final float tickProgress) {
		if (cart.getBehavior() instanceof NewMinecartBehavior newBehavior && newBehavior.cartHasPosRotLerp()) {
			Vec3 lerpPos = newBehavior.getCartLerpPosition(tickProgress);
			if (lerpPos != null) {
				return lerpPos;
			}
		} else if (cart.getBehavior() instanceof OldMinecartBehavior oldBehavior) {
			double x = Mth.lerp(tickProgress, cart.xo, cart.getX());
			double y = Mth.lerp(tickProgress, cart.yo, cart.getY());
			double z = Mth.lerp(tickProgress, cart.zo, cart.getZ());
			Vec3 railPos = oldBehavior.getPos(x, y, z);
			if (railPos != null) {
				return railPos;
			}
		}
		return cart.getPosition(tickProgress);
	}

	private void minecartChain$appendChainOffset(
		final AbstractMinecart minecart,
		final MinecartChainRenderState chainState,
		final Optional<UUID> linkedId,
		final float tickProgress
	) {
		if (linkedId.isEmpty() || minecart.getUUID().compareTo(linkedId.get()) >= 0) {
			return;
		}

		Entity linkedEntity = minecart.level().getEntity(linkedId.get());
		if (!(linkedEntity instanceof AbstractMinecart linkedMinecart)) {
			return;
		}

		Vec3 myPos = minecartChain$visualPosition(minecart, tickProgress);
		Vec3 linkedPos = minecartChain$visualPosition(linkedMinecart, tickProgress);
		Vec3 offset = linkedPos.subtract(myPos);
		if (offset.horizontalDistanceSqr() <= 0.04D || offset.lengthSqr() > 144.0D) {
			return;
		}

		chainState.minecartChain$chainOffsets().add(offset);
	}

	private void minecartChain$submitChain(
		final Vec3 offset,
		final MinecartChainRenderState chainState,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int outlineColor
	) {
		double length = offset.length();
		double visibleLength = length - CHAIN_ATTACHMENT_OFFSET * 2.0D;
		if (visibleLength <= 0.05D) {
			return;
		}

		Vec3 direction = offset.scale(1.0D / length);
		float yaw = (float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
		double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		float pitch = (float) (Mth.atan2(direction.y, horizontalDist) * Mth.RAD_TO_DEG);

		int segments = Math.max(1, (int) Math.ceil(visibleLength / CHAIN_SEGMENT_SPACING));
		double step = visibleLength / segments;
		BlockModelRenderState chainModel = chainState.minecartChain$chainZModel();

		for (int i = 0; i < segments; i++) {
			double distance = CHAIN_ATTACHMENT_OFFSET + (i + 0.5D) * step;
			Vec3 position = direction.scale(distance).add(0.0D, CHAIN_HEIGHT, 0.0D);
			poseStack.pushPose();
			poseStack.translate(position.x, position.y, position.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
			poseStack.scale(CHAIN_SEGMENT_SCALE, CHAIN_SEGMENT_SCALE, CHAIN_SEGMENT_SCALE);
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			chainModel.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor);
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
