package com.piotrek.minecartchain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.minecartchain.LongMinecartEntity;
import com.piotrek.minecartchain.MinecartChainMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class LongMinecartRenderer extends EntityRenderer<LongMinecartEntity, CoalTenderRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "textures/entity/long_minecart.png");
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
		Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "long_minecart"),
		"main"
	);

	private final LongMinecartModel model;

	public LongMinecartRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
		this.model = new LongMinecartModel(context.bakeLayer(LAYER_LOCATION));
	}

	@Override
	public CoalTenderRenderState createRenderState() {
		return new CoalTenderRenderState();
	}

	@Override
	public void extractRenderState(final LongMinecartEntity entity, final CoalTenderRenderState state, final float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = entity.getViewYRot(partialTick);
		state.pitch = entity.getViewXRot(partialTick);
		state.onSpline = entity.getVehicle() != null && entity.getVehicle().getClass().getName().contains("TrackFollowerEntity");
		state.wheelRotation = entity.getWheelRotation(partialTick);
	}

	@Override
	public void submit(
		final CoalTenderRenderState state,
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final CameraRenderState cameraState
	) {
		super.submit(state, poseStack, collector, cameraState);
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.35F, 0.0F);
		float yawRot = state.onSpline ? 90.0F : (270.0F - state.yaw);
		poseStack.rotateDegrees(Axis.YP, yawRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
