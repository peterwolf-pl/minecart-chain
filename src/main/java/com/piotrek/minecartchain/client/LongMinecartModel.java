package com.piotrek.minecartchain.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class LongMinecartModel extends EntityModel<CoalTenderRenderState> {
	private final ModelPart[] wheels;

	public LongMinecartModel(final ModelPart root) {
		super(root);
		this.wheels = new ModelPart[] {
			root.getChild("w1"), root.getChild("w2"), root.getChild("w3"), root.getChild("w4"),
			root.getChild("w5"), root.getChild("w6"), root.getChild("w7"), root.getChild("w8")
		};
	}

	@Override
	public void setupAnim(final CoalTenderRenderState state) {
		super.setupAnim(state);
		for (ModelPart wheel : this.wheels) {
			wheel.zRot = state.wheelRotation;
		}
	}

	public static LayerDefinition createBodyLayer() {
		final MeshDefinition mesh = new MeshDefinition();
		final PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild(
			"body",
			CubeListBuilder.create()
				.texOffs(0, 0).addBox(-16.0F, 0.0F, -6.0F, 32.0F, 2.0F, 12.0F)
				.texOffs(0, 20).addBox(-16.0F, -6.0F, -7.0F, 32.0F, 6.0F, 1.0F)
				.texOffs(0, 32).addBox(-16.0F, -6.0F, 6.0F, 32.0F, 6.0F, 1.0F)
				.texOffs(0, 44).addBox(-16.0F, -6.0F, -6.0F, 1.0F, 6.0F, 12.0F)
				.texOffs(28, 44).addBox(15.0F, -6.0F, -6.0F, 1.0F, 6.0F, 12.0F)
				.texOffs(56, 44).addBox(-17.0F, -1.0F, -7.0F, 1.0F, 3.0F, 14.0F)
				.texOffs(88, 44).addBox(16.0F, -1.0F, -7.0F, 1.0F, 3.0F, 14.0F),
			PartPose.ZERO
		);

		final CubeListBuilder wheel = CubeListBuilder.create()
			.texOffs(0, 64).addBox(-2.5F, -2.5F, -0.4F, 5.0F, 5.0F, 0.8F);
		root.addOrReplaceChild("w1", wheel, PartPose.offset(-10.0F, 2.0F, -5.5F));
		root.addOrReplaceChild("w2", wheel, PartPose.offset(-3.0F, 2.0F, -5.5F));
		root.addOrReplaceChild("w3", wheel, PartPose.offset(4.0F, 2.0F, -5.5F));
		root.addOrReplaceChild("w4", wheel, PartPose.offset(11.0F, 2.0F, -5.5F));
		root.addOrReplaceChild("w5", wheel, PartPose.offset(-10.0F, 2.0F, 5.5F));
		root.addOrReplaceChild("w6", wheel, PartPose.offset(-3.0F, 2.0F, 5.5F));
		root.addOrReplaceChild("w7", wheel, PartPose.offset(4.0F, 2.0F, 5.5F));
		root.addOrReplaceChild("w8", wheel, PartPose.offset(11.0F, 2.0F, 5.5F));
		return LayerDefinition.create(mesh, 128, 128);
	}
}
