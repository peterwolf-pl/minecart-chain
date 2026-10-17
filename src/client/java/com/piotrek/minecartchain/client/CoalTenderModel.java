package com.piotrek.minecartchain.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class CoalTenderModel extends EntityModel<CoalTenderRenderState> {
	private final ModelPart wheelLf;
	private final ModelPart wheelLm;
	private final ModelPart wheelLr;
	private final ModelPart wheelLe;
	private final ModelPart wheelRf;
	private final ModelPart wheelRm;
	private final ModelPart wheelRr;
	private final ModelPart wheelRe;

	public CoalTenderModel(final ModelPart root) {
		super(root);
		this.wheelLf = root.getChild("wheel_lf");
		this.wheelLm = root.getChild("wheel_lm");
		this.wheelLr = root.getChild("wheel_lr");
		this.wheelLe = root.getChild("wheel_le");
		this.wheelRf = root.getChild("wheel_rf");
		this.wheelRm = root.getChild("wheel_rm");
		this.wheelRr = root.getChild("wheel_rr");
		this.wheelRe = root.getChild("wheel_re");
	}

	@Override
	public void setupAnim(final CoalTenderRenderState state) {
		super.setupAnim(state);
		final float rot = state.wheelRotation;
		this.wheelLf.zRot = rot;
		this.wheelLm.zRot = rot;
		this.wheelLr.zRot = rot;
		this.wheelLe.zRot = rot;
		this.wheelRf.zRot = rot;
		this.wheelRm.zRot = rot;
		this.wheelRr.zRot = rot;
		this.wheelRe.zRot = rot;
	}

	public static LayerDefinition createBodyLayer() {
		final MeshDefinition mesh = new MeshDefinition();
		final PartDefinition root = mesh.getRoot();

		// 1. Chassis (32 units long, 14 wide, buffer beams)
		root.addOrReplaceChild(
			"chassis",
			CubeListBuilder.create()
				// Main base plate (dx=32: -16..16, dy=2, dz=14: -7..7)
				.texOffs(0, 0).addBox(-16.0F, 0.0F, -7.0F, 32.0F, 2.0F, 14.0F)
				// Front buffer beam with gold fittings
				.texOffs(160, 0).addBox(-17.0F, -1.0F, -7.5F, 1.0F, 4.0F, 15.0F)
				// Rear buffer beam with gold fittings
				.texOffs(200, 0).addBox(16.0F, -1.0F, -7.5F, 1.0F, 4.0F, 15.0F),
			PartPose.ZERO
		);

		// 2. Tender Coal Bunker (black body with gold corner trims)
		root.addOrReplaceChild(
			"bunker",
			CubeListBuilder.create()
				// Front wall (dx=1, dy=12, dz=14)
				.texOffs(0, 24).addBox(-16.0F, -12.0F, -7.0F, 1.0F, 12.0F, 14.0F)
				// Rear wall (dx=1, dy=12, dz=14)
				.texOffs(40, 24).addBox(15.0F, -12.0F, -7.0F, 1.0F, 12.0F, 14.0F)
				// Left wall (dx=30, dy=12, dz=1)
				.texOffs(0, 56).addBox(-15.0F, -12.0F, -7.0F, 30.0F, 12.0F, 1.0F)
				// Right wall (dx=30, dy=12, dz=1)
				.texOffs(0, 72).addBox(-15.0F, -12.0F, 6.0F, 30.0F, 12.0F, 1.0F)
				// Coal mound inside bunker (textured dark coal lumps)
				.texOffs(0, 96).addBox(-15.0F, -14.0F, -6.0F, 30.0F, 6.0F, 12.0F)
				.texOffs(0, 132).addBox(-11.0F, -16.0F, -4.5F, 22.0F, 3.0F, 9.0F),
			PartPose.ZERO
		);

		// 3. Gold Corner Reinforcements & Rim Bracing
		root.addOrReplaceChild(
			"gold_fittings",
			CubeListBuilder.create()
				// Top gold rim trim around bunker
				.texOffs(80, 24).addBox(-16.2F, -12.5F, -7.2F, 32.4F, 1.0F, 1.0F)
				.texOffs(80, 32).addBox(-16.2F, -12.5F, 6.2F, 32.4F, 1.0F, 1.0F)
				.texOffs(160, 24).addBox(-16.2F, -12.5F, -6.2F, 1.0F, 1.0F, 12.4F)
				.texOffs(180, 24).addBox(15.2F, -12.5F, -6.2F, 1.0F, 1.0F, 12.4F)
				// 4 Gold Corner brackets
				.texOffs(160, 48).addBox(-16.2F, -11.5F, -7.2F, 1.5F, 11.5F, 1.5F)
				.texOffs(172, 48).addBox(14.7F, -11.5F, -7.2F, 1.5F, 11.5F, 1.5F)
				.texOffs(184, 48).addBox(-16.2F, -11.5F, 5.7F, 1.5F, 11.5F, 1.5F)
				.texOffs(196, 48).addBox(14.7F, -11.5F, 5.7F, 1.5F, 11.5F, 1.5F),
			PartPose.ZERO
		);

		// 4. Wheels (4 pairs of driving/carrying bogie wheels)
		final CubeListBuilder wheelBuilder = CubeListBuilder.create()
			.texOffs(0, 160).addBox(-3.0F, -3.0F, -0.4F, 6.0F, 6.0F, 0.8F)
			.texOffs(24, 160).addBox(-3.5F, -2.0F, -0.2F, 7.0F, 4.0F, 0.4F)
			.texOffs(24, 172).addBox(-2.0F, -3.5F, -0.2F, 4.0F, 7.0F, 0.4F);

		root.addOrReplaceChild("wheel_lf", wheelBuilder, PartPose.offset(-11.0F, 2.0F, -6.0F));
		root.addOrReplaceChild("wheel_lm", wheelBuilder, PartPose.offset(-3.0F, 2.0F, -6.0F));
		root.addOrReplaceChild("wheel_lr", wheelBuilder, PartPose.offset(5.0F, 2.0F, -6.0F));
		root.addOrReplaceChild("wheel_le", wheelBuilder, PartPose.offset(11.0F, 2.0F, -6.0F));

		root.addOrReplaceChild("wheel_rf", wheelBuilder, PartPose.offset(-11.0F, 2.0F, 6.0F));
		root.addOrReplaceChild("wheel_rm", wheelBuilder, PartPose.offset(-3.0F, 2.0F, 6.0F));
		root.addOrReplaceChild("wheel_rr", wheelBuilder, PartPose.offset(5.0F, 2.0F, 6.0F));
		root.addOrReplaceChild("wheel_re", wheelBuilder, PartPose.offset(11.0F, 2.0F, 6.0F));

		return LayerDefinition.create(mesh, 256, 256);
	}
}
