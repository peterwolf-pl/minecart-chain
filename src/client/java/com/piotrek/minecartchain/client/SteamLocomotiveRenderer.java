package com.piotrek.minecartchain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.minecartchain.MinecartChainMod;
import com.piotrek.minecartchain.SteamLocomotiveEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class SteamLocomotiveRenderer extends EntityRenderer<SteamLocomotiveEntity, SteamLocomotiveRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "textures/entity/steam_locomotive.png");
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
		Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "steam_locomotive"),
		"main"
	);

	protected final SteamLocomotiveModel model;

	public SteamLocomotiveRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.8F;
		this.model = new SteamLocomotiveModel(context.bakeLayer(LAYER_LOCATION));
	}

	public SteamLocomotiveModel getModel() {
		return this.model;
	}

	@Override
	public SteamLocomotiveRenderState createRenderState() {
		return new SteamLocomotiveRenderState();
	}

	@Override
	public void extractRenderState(final SteamLocomotiveEntity entity, final SteamLocomotiveRenderState state, final float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.throttle = entity.getThrottle();
		state.reverser = entity.isReverser();
		state.couplerEngaged = entity.isCouplerEngaged();
		state.yaw = entity.getFacingYaw();
		state.onSpline = entity.isOnMountedTrack();
		state.pitch = entity.getViewXRot(partialTick);
		state.roll = 0.0F;
		final Vec3 delta = entity.getDeltaMovement();
		state.speed = delta.horizontalDistance();
		state.wheelRotation = entity.getWheelRotation(partialTick);
	}

	@Override
	public void submit(
		final SteamLocomotiveRenderState state,
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final CameraRenderState cameraState
	) {
		super.submit(state, poseStack, collector, cameraState);
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.35F, 0.0F);
		// On Splinecart the dispatcher already applies the track basis + 180° Y and forces
		// passenger yaw to 90, same as a vanilla minecart. Subtracting facingYaw here
		// rotates E-W locomotives 90° off the ties. Match the minecart inner yaw (90).
		float yawRot = state.onSpline ? 90.0F : (270.0F - state.yaw);
		poseStack.rotateDegrees(Axis.YP, yawRot);
		poseStack.rotateDegrees(Axis.XP, -state.pitch);
		poseStack.rotateDegrees(Axis.ZP, state.roll);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
