package com.piotrek.minecartchain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.minecartchain.LongChestMinecartEntity;
import com.piotrek.minecartchain.MinecartChainMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Renders the two-block cargo wagon using the long minecart chassis and wheels,
 * with the authentic vanilla double chest model (left and right halves) on its deck.
 */
public class LongChestMinecartRenderer extends EntityRenderer<LongChestMinecartEntity, CoalTenderRenderState> {
	public static final Identifier BASE_TEXTURE = Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "textures/entity/long_minecart.png");

	private final LongMinecartModel baseModel;
	private final ChestModel chestLeft;
	private final ChestModel chestRight;
	private final SpriteGetter sprites;

	public LongChestMinecartRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
		this.baseModel = new LongMinecartModel(context.bakeLayer(LongMinecartRenderer.LAYER_LOCATION));
		this.chestLeft = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT));
		this.chestRight = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT));
		this.sprites = context.getSprites();
	}

	@Override
	public CoalTenderRenderState createRenderState() {
		return new CoalTenderRenderState();
	}

	@Override
	public void extractRenderState(final LongChestMinecartEntity entity, final CoalTenderRenderState state, final float partialTick) {
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

		// 1. Long wagon chassis and 8 animated wheels
		poseStack.pushPose();
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.baseModel, state, poseStack, BASE_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();

		// 2. Vanilla Double Chest mounted on the cargo deck
		SpriteId leftSprite = Sheets.CHEST_REGULAR.left();
		SpriteId rightSprite = Sheets.CHEST_REGULAR.right();
		Float lidAngle = 0.0F;
		int chestColor = state.outlineColor != 0 ? state.outlineColor : -1;

		poseStack.pushPose();
		float scale = 0.85F;
		poseStack.translate(0.0F, 0.01F, 0.0F);
		poseStack.scale(scale, scale, scale);

		// Right half of the double chest (X: -15/16 .. 0.0 in consist frame)
		poseStack.pushPose();
		poseStack.translate(-1.0F, 0.0F, -0.5F);
		collector.submitModel(
			this.chestRight, lidAngle, poseStack,
			state.lightCoords, OverlayTexture.NO_OVERLAY, chestColor,
			rightSprite, this.sprites, 0
		);
		poseStack.popPose();

		// Left half of the double chest (X: 0.0 .. +15/16 in consist frame)
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.0F, -0.5F);
		collector.submitModel(
			this.chestLeft, lidAngle, poseStack,
			state.lightCoords, OverlayTexture.NO_OVERLAY, chestColor,
			leftSprite, this.sprites, 0
		);
		poseStack.popPose();

		poseStack.popPose();

		poseStack.popPose();
	}
}
