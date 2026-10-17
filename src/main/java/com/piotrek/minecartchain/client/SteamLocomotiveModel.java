package com.piotrek.minecartchain.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class SteamLocomotiveModel extends EntityModel<SteamLocomotiveRenderState> {
	private static final float CRANK_RADIUS = 1.4F;

	private final ModelPart wheelLeftFront;
	private final ModelPart wheelLeftCenter;
	private final ModelPart wheelLeftRear;
	private final ModelPart wheelRightFront;
	private final ModelPart wheelRightCenter;
	private final ModelPart wheelRightRear;
	private final ModelPart rodLeft;
	private final ModelPart rodRight;
	private final ModelPart couplerLever;

	private final float rodLeftBaseX;
	private final float rodLeftBaseY;
	private final float rodRightBaseX;
	private final float rodRightBaseY;

	public SteamLocomotiveModel(final ModelPart root) {
		super(root);
		this.wheelLeftFront = root.getChild("wheel_left_front");
		this.wheelLeftCenter = root.getChild("wheel_left_center");
		this.wheelLeftRear = root.getChild("wheel_left_rear");
		this.wheelRightFront = root.getChild("wheel_right_front");
		this.wheelRightCenter = root.getChild("wheel_right_center");
		this.wheelRightRear = root.getChild("wheel_right_rear");
		this.rodLeft = root.getChild("rod_left");
		this.rodRight = root.getChild("rod_right");
		this.couplerLever = root.getChild("coupler_lever");

		this.rodLeftBaseX = this.rodLeft.x;
		this.rodLeftBaseY = this.rodLeft.y;
		this.rodRightBaseX = this.rodRight.x;
		this.rodRightBaseY = this.rodRight.y;
	}

	public static LayerDefinition createBodyLayer() {
		final MeshDefinition mesh = new MeshDefinition();
		final PartDefinition root = mesh.getRoot();

		// 1. Undercarriage / Chassis
		root.addOrReplaceChild(
			"chassis",
			CubeListBuilder.create()
				// Główna rama podwozia (długość 44, szerokość 14, wysokość 2) -> -22.0F do +22.0F
				.texOffs(0, 0).addBox(-22.0F, 0.0F, -7.0F, 44.0F, 2.0F, 14.0F)
				// Przednia belka zderzakowa z lemieszem / sprzęgiem
				.texOffs(160, 0).addBox(-23.0F, -2.0F, -7.5F, 1.0F, 5.0F, 15.0F)
				.texOffs(160, 28).addBox(-24.5F, -1.0F, -4.5F, 1.5F, 4.0F, 9.0F)
				// Tylna belka zderzakowa
				.texOffs(200, 0).addBox(22.0F, -2.0F, -7.5F, 1.0F, 5.0F, 15.0F),
			PartPose.ZERO
		);

		// 2. Boiler & Smokebox (wydłużony i powiększony do wysokości gracza)
		root.addOrReplaceChild(
			"boiler",
			CubeListBuilder.create()
				// Główny walczak kotła (długość 28: -22 do +6, wysokość 15, szerokość 14)
				.texOffs(0, 48).addBox(-22.0F, -15.0F, -6.0F, 28.0F, 15.0F, 12.0F)
				// Zaokrąglenie wierzchu i boków kotła
				.texOffs(88, 48).addBox(-22.0F, -16.0F, -5.0F, 28.0F, 1.0F, 10.0F)
				// Dymnica z przodu z drzwiami okrągłymi
				.texOffs(0, 80).addBox(-22.5F, -14.0F, -5.0F, 0.5F, 13.0F, 10.0F)
				// Reflektor główny (latarnia parowozu)
				.texOffs(168, 48).addBox(-24.5F, -17.0F, -2.0F, 2.0F, 4.0F, 4.0F)
				// Komin (wysoki, proporcjonalny, z obwódką)
				.texOffs(188, 48).addBox(-18.0F, -25.0F, -2.0F, 4.0F, 9.0F, 4.0F)
				.texOffs(168, 64).addBox(-18.5F, -26.0F, -2.5F, 5.0F, 1.0F, 5.0F)
				// Kołpak pary (Steam dome)
				.texOffs(200, 64).addBox(-10.0F, -20.0F, -2.5F, 5.0F, 4.0F, 5.0F)
				// Piasecznica (Sand dome)
				.texOffs(224, 64).addBox(-1.0F, -19.5F, -2.0F, 4.0F, 3.5F, 4.0F),
			PartPose.ZERO
		);

		// 3. Driver Cab — budka maszynisty sięgająca wysokości stojącego gracza (wysokość 28 jednostek = 1.75 bloku!)
		root.addOrReplaceChild(
			"cab",
			CubeListBuilder.create()
				// Przednia ściana kabiny z oknem na wprost nad kotłem
				.texOffs(0, 108).addBox(6.0F, -26.0F, -7.0F, 1.0F, 3.0F, 14.0F)
				.texOffs(32, 108).addBox(6.0F, -15.0F, -7.0F, 1.0F, 15.0F, 14.0F)
				.texOffs(64, 108).addBox(6.0F, -23.0F, -7.0F, 1.0F, 8.0F, 3.0F)
				.texOffs(76, 108).addBox(6.0F, -23.0F, 4.0F, 1.0F, 8.0F, 3.0F)
				// Lewa ściana kabiny z oknem bocznym
				.texOffs(0, 140).addBox(7.0F, -26.0F, -7.0F, 15.0F, 3.0F, 1.0F)
				.texOffs(40, 140).addBox(7.0F, -11.0F, -7.0F, 15.0F, 11.0F, 1.0F)
				.texOffs(80, 140).addBox(7.0F, -23.0F, -7.0F, 2.0F, 12.0F, 1.0F)
				.texOffs(90, 140).addBox(19.0F, -23.0F, -7.0F, 3.0F, 12.0F, 1.0F)
				// Prawa ściana kabiny z oknem bocznym
				.texOffs(0, 156).addBox(7.0F, -26.0F, 6.0F, 15.0F, 3.0F, 1.0F)
				.texOffs(40, 156).addBox(7.0F, -11.0F, 6.0F, 15.0F, 11.0F, 1.0F)
				.texOffs(80, 156).addBox(7.0F, -23.0F, 6.0F, 2.0F, 12.0F, 1.0F)
				.texOffs(90, 156).addBox(19.0F, -23.0F, 6.0F, 3.0F, 12.0F, 1.0F)
				// Dach kabiny
				.texOffs(0, 172).addBox(5.0F, -27.5F, -8.0F, 18.0F, 1.5F, 16.0F)
				// Tylne wejściowe słupki narożne
				.texOffs(80, 172).addBox(21.0F, -26.0F, -7.0F, 1.0F, 26.0F, 3.0F)
				.texOffs(96, 172).addBox(21.0F, -26.0F, 4.0F, 1.0F, 26.0F, 3.0F),
			PartPose.ZERO
		);

		// 4. Cab Interior
		root.addOrReplaceChild(
			"interior",
			CubeListBuilder.create()
				// Podłoga budki maszynisty
				.texOffs(120, 108).addBox(7.0F, 0.0F, -6.0F, 14.0F, 1.0F, 12.0F)
				// Tył kotła / ściana sitowa z paleniskiem i zegarami
				.texOffs(120, 128).addBox(6.5F, -15.0F, -5.0F, 3.0F, 15.0F, 10.0F)
				// Ławka maszynisty (obniżona, dopasowana do siedzącego gracza)
				.texOffs(160, 128).addBox(12.0F, -2.0F, -5.0F, 8.0F, 2.0F, 10.0F),
			PartPose.ZERO
		);

		// Dźwignia sprzęgu
		root.addOrReplaceChild(
			"coupler_lever",
			CubeListBuilder.create()
				.texOffs(210, 128).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F)
				.texOffs(216, 128).addBox(-0.5F, -5.5F, -0.5F, 1.0F, 5.0F, 1.0F),
			PartPose.offset(10.0F, -7.0F, 0.0F)
		);

		// 5. Wheels (8 dużych kół napędowych, proporcjonalne do dłuższego parowozu)
		final CubeListBuilder wheelBuilder = CubeListBuilder.create()
			.texOffs(0, 208).addBox(-4.0F, -4.0F, -0.4F, 8.0F, 8.0F, 0.8F)
			.texOffs(24, 208).addBox(-4.5F, -2.5F, -0.2F, 9.0F, 5.0F, 0.4F)
			.texOffs(24, 220).addBox(-2.5F, -4.5F, -0.2F, 5.0F, 9.0F, 0.4F)
			.texOffs(168, 80).addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 0.3F);

		root.addOrReplaceChild("wheel_left_front", wheelBuilder, PartPose.offset(-15.0F, 2.0F, -6.0F));
		root.addOrReplaceChild("wheel_left_center", wheelBuilder, PartPose.offset(-5.0F, 2.0F, -6.0F));
		root.addOrReplaceChild("wheel_left_rear", wheelBuilder, PartPose.offset(5.0F, 2.0F, -6.0F));

		root.addOrReplaceChild("wheel_right_front", wheelBuilder, PartPose.offset(-15.0F, 2.0F, 6.0F));
		root.addOrReplaceChild("wheel_right_center", wheelBuilder, PartPose.offset(-5.0F, 2.0F, 6.0F));
		root.addOrReplaceChild("wheel_right_rear", wheelBuilder, PartPose.offset(5.0F, 2.0F, 6.0F));

		// 6. Wiązary / Drągi napędowe łączące koła
		root.addOrReplaceChild(
			"rod_left",
			CubeListBuilder.create().texOffs(48, 208).addBox(-11.0F, -0.6F, -0.4F, 22.0F, 1.2F, 0.8F),
			PartPose.offset(-5.0F, 2.0F, -6.8F)
		);
		root.addOrReplaceChild(
			"rod_right",
			CubeListBuilder.create().texOffs(48, 214).addBox(-11.0F, -0.6F, -0.4F, 22.0F, 1.2F, 0.8F),
			PartPose.offset(-5.0F, 2.0F, 6.8F)
		);

		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(final SteamLocomotiveRenderState state) {
		super.setupAnim(state);
		final float rot = state.wheelRotation;
		this.wheelLeftFront.zRot = rot;
		this.wheelLeftCenter.zRot = rot;
		this.wheelLeftRear.zRot = rot;
		this.wheelRightFront.zRot = rot;
		this.wheelRightCenter.zRot = rot;
		this.wheelRightRear.zRot = rot;

		final float crankX = -Mth.sin(rot) * CRANK_RADIUS;
		final float crankY = Mth.cos(rot) * CRANK_RADIUS;
		this.rodLeft.x = this.rodLeftBaseX + crankX;
		this.rodLeft.y = this.rodLeftBaseY + crankY;
		this.rodRight.x = this.rodRightBaseX + crankX;
		this.rodRight.y = this.rodRightBaseY + crankY;

		// Coupler lever in cab: tilts based on whether a wagon is coupled
		this.couplerLever.xRot = state.couplerEngaged ? 0.45F : -0.45F;
	}
}
