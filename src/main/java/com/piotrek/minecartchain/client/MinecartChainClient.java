package com.piotrek.minecartchain.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartChainMod;
import com.piotrek.minecartchain.MinecartTrainLogic;
import com.piotrek.minecartchain.SteamLocomotiveEntity;
import com.piotrek.minecartchain.SteamLocomotiveInputPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MinecartChainMod.MOD_ID, dist = Dist.CLIENT)
public class MinecartChainClient {
	private static final KeyMapping.Category MINECART_CHAIN_CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath(MinecartChainMod.MOD_ID, "key_category")
	);

	public static final KeyMapping REVERSER_KEY = new KeyMapping(
		"key.minecart_chain.reverser",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_R,
		MINECART_CHAIN_CATEGORY
	);

	public static final KeyMapping COUPLE_KEY = new KeyMapping(
		"key.minecart_chain.couple",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_C,
		MINECART_CHAIN_CATEGORY
	);

	private static final DoubleTapKey EMERGENCY_BRAKE_TAP = new DoubleTapKey(300L);
	private static float lastSentThrottle = 0.0F;
	private static boolean lastSentReverser = false;
	private static SteamLocomotiveEntity inputLocomotive = null;

	public MinecartChainClient(IEventBus modBus) {
		modBus.addListener(MinecartChainClient::registerScreens);
		modBus.addListener(MinecartChainClient::registerRenderers);
		modBus.addListener(MinecartChainClient::registerLayerDefinitions);
		modBus.addListener(MinecartChainClient::registerKeyMappings);
		modBus.addListener(MinecartChainClient::registerGuiLayers);
		NeoForge.EVENT_BUS.addListener(MinecartChainClient::onClientTick);
		MinecartChainMod.LOGGER.info("Minecart chain client renderer hooks loaded");
	}

	private static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(MinecartChainMod.LOCOMOTIVE_MENU.get(), MinecartLocomotiveScreen::new);
		event.register(MinecartChainMod.RADIO_LOCOMOTIVE_MENU.get(), RadioLocomotiveScreen::new);
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY.get(), SteamLocomotiveRenderer::new);
		event.registerEntityRenderer(MinecartChainMod.COAL_TENDER_ENTITY.get(), CoalTenderRenderer::new);
		event.registerEntityRenderer(MinecartChainMod.LONG_CHEST_MINECART_ENTITY.get(), LongChestMinecartRenderer::new);
		event.registerEntityRenderer(MinecartChainMod.LONG_MINECART_ENTITY.get(), LongMinecartRenderer::new);
	}

	private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(SteamLocomotiveRenderer.LAYER_LOCATION, SteamLocomotiveModel::createBodyLayer);
		event.registerLayerDefinition(CoalTenderRenderer.LAYER_LOCATION, CoalTenderModel::createBodyLayer);
		event.registerLayerDefinition(LongMinecartRenderer.LAYER_LOCATION, LongMinecartModel::createBodyLayer);
	}

	private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(REVERSER_KEY);
		event.register(COUPLE_KEY);
	}

	private static void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(
			MinecartChainMod.id("locomotive_hud"),
			MinecartChainClient::renderLocomotiveHud
		);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.screen() != null || !client.isWindowActive()) {
			EMERGENCY_BRAKE_TAP.reset();
			inputLocomotive = null;
			return;
		}
		if (client.player.getVehicle() instanceof SteamLocomotiveEntity locomotive) {
			if (((MinecartChainAccess) locomotive).minecartChain$isRadioLocomotive()) {
				boolean coupleNext = false;
				boolean uncoupleLast = false;
				while (COUPLE_KEY.consumeClick()) {
					if (client.player.isShiftKeyDown()) {
						uncoupleLast = true;
					} else {
						coupleNext = true;
					}
				}
				if (coupleNext || uncoupleLast) {
					ClientPacketDistributor.sendToServer(new SteamLocomotiveInputPayload(locomotive.getThrottle(), locomotive.isReverser(), coupleNext, uncoupleLast));
				}
				return;
			}

			if (inputLocomotive != locomotive) {
				EMERGENCY_BRAKE_TAP.reset();
				inputLocomotive = locomotive;
			}
			boolean keyUp = client.options.keyUp.isDown();
			boolean keyDown = client.options.keyDown.isDown();

			boolean emergencyBrake = EMERGENCY_BRAKE_TAP.update(keyDown, net.minecraft.util.Util.getMillis());
			float t = locomotive.getThrottle();
			if (emergencyBrake) {
				t = -1.0F;
			} else if (keyUp) {
				t = Math.min(1.0F, t + 0.02F);
			} else if (keyDown) {
				t = Math.max(-1.0F, t - 0.02F);
			}

			boolean reverser = locomotive.isReverser();
			while (REVERSER_KEY.consumeClick()) {
				reverser = !reverser;
			}

			boolean coupleNext = false;
			boolean uncoupleLast = false;
			while (COUPLE_KEY.consumeClick()) {
				if (client.player.isShiftKeyDown()) {
					uncoupleLast = true;
				} else {
					coupleNext = true;
				}
			}

			if (emergencyBrake || Math.abs(t - lastSentThrottle) > 0.005F || reverser != lastSentReverser || coupleNext || uncoupleLast) {
				lastSentThrottle = t;
				lastSentReverser = reverser;
				locomotive.setThrottle(t);
				locomotive.setReverser(reverser);
				ClientPacketDistributor.sendToServer(new SteamLocomotiveInputPayload(t, reverser, coupleNext, uncoupleLast));
			}
		} else {
			EMERGENCY_BRAKE_TAP.reset();
			inputLocomotive = null;
			lastSentThrottle = 0.0F;
			lastSentReverser = false;
		}
	}

	private static void renderLocomotiveHud(final GuiGraphicsExtractor graphicsExtractor, final DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(client.player.getVehicle() instanceof SteamLocomotiveEntity locomotive)) {
			return;
		}

		Font font = client.font;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();

		float throttle = locomotive.getThrottle();
		boolean reverser = locomotive.isReverser();

		// Real speed in km/h from all available kinematics (vanilla deltaMovement, frame translation, Splinecart)
		double hSpeed = locomotive.getDeltaMovement().horizontal().length();
		if (hSpeed < 0.001D) {
			double dx = locomotive.getX() - locomotive.xOld;
			double dz = locomotive.getZ() - locomotive.zOld;
			hSpeed = Math.hypot(dx, dz);
		}
		if (hSpeed < 0.001D) {
			hSpeed = MinecartTrainLogic.horizontalMovement(locomotive).length();
		}
		double speedKmh = hSpeed * 20.0D * 3.6D;

		// Telemetry: Engine Power & Engine Load
		float enginePower = Math.max(0.0F, throttle);
		float engineLoad = locomotive.getEngineLoad();
		int wagonsCount = locomotive.getWagonsCount();

		boolean isRadio = ((MinecartChainAccess) locomotive).minecartChain$isRadioLocomotive();

		// Texts & Colors
		String speedText = String.format("Speed: %.1f km/h", speedKmh);
		String reverserText = isRadio
			? (reverser ? "REVERSE [RADIO]" : "FORWARD [RADIO]")
			: (reverser ? "REVERSE [R]" : "FORWARD [R]");
		int reverserColor = reverser ? 0xFFFFCC00 : 0xFF55FF55;

		String throttleText;
		int throttleColor;
		if (isRadio) {
			if (throttle > 0.01F) {
				throttleText = String.format("Throttle: %.0f%% [RADIO]", throttle * 100.0F);
				throttleColor = 0xFF55FFFF;
			} else {
				throttleText = "Brake: 100% [STOP]";
				throttleColor = 0xFFFF4444;
			}
		} else if (throttle > 0.01F) {
			throttleText = String.format("Throttle: %.0f%% (W)", throttle * 100.0F);
			throttleColor = 0xFF55FFFF;
		} else if (throttle < -0.01F) {
			throttleText = throttle <= -0.99F ? "Brake: 100% [EMERGENCY]" : String.format("Brake: %.0f%% (S)", -throttle * 100.0F);
			throttleColor = 0xFFFF4444;
		} else {
			throttleText = "Throttle: Idle";
			throttleColor = 0xFFFFAA00;
		}

		String couplerText = locomotive.isCouplerEngaged()
			? (wagonsCount > 0 ? "Coupled: " + wagonsCount + " car" + (wagonsCount > 1 ? "s" : "") + " [C/Shift+C]" : "Coupled [C]")
			: "Coupler: READY [C]";
		int couplerColor = locomotive.isCouplerEngaged() ? 0xFF55FF55 : 0xFFFFAA00;

		// Panel geometry
		int boxWidth = 230;
		int boxHeight = 74;
		int boxX = (screenWidth - boxWidth) / 2;
		int boxY = screenHeight - boxHeight - 38;

		// Background & outer border
		graphicsExtractor.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x90000000);
		graphicsExtractor.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF4A5664);
		graphicsExtractor.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF4A5664);
		graphicsExtractor.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFF4A5664);
		graphicsExtractor.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF4A5664);

		// 1. Top row: Large speed readout + Reverser tag
		graphicsExtractor.text(font, speedText, boxX + 8, boxY + 6, 0xFFFFFFFF, false);
		int revWidth = font.width(reverserText);
		graphicsExtractor.text(font, reverserText, boxX + boxWidth - revWidth - 8, boxY + 6, reverserColor, false);

		// 2. Horizontal Bar 1: Drive Torque / Power (Moment silnika)
		int barX = boxX + 8;
		int barW = boxWidth - 16;
		boolean isMoving = speedKmh > 0.5D;
		boolean hasDriveSurplus = enginePower > engineLoad;
		int powerBarColor = (isMoving || hasDriveSurplus) ? 0xFF00C8FF : 0xFF88AACC;

		String driveStatus;
		int driveStatusColor;
		if (throttle < -0.01F) {
			driveStatus = "■ HAMOWANIE";
			driveStatusColor = 0xFFFF4444;
		} else if (isMoving) {
			if (throttle <= 0.01F) {
				driveStatus = "≈ TOCZENIE LUZEM";
				driveStatusColor = 0xFF77DDAA;
			} else {
				driveStatus = "● JAZDA";
				driveStatusColor = 0xFF55FF55;
			}
		} else {
			if (hasDriveSurplus) {
				driveStatus = "● RUSZANIE (MOMENT > OPÓR)";
				driveStatusColor = 0xFF55FF55;
			} else if (throttle > 0.01F) {
				driveStatus = "▲ ZA MAŁY GAZ (MOC <= OPÓR)";
				driveStatusColor = 0xFFFF8844;
			} else {
				driveStatus = "○ POSTÓJ";
				driveStatusColor = 0xFFAAAAAA;
			}
		}

		String powerLabel = String.format("Moment (Power): %.0f%%", enginePower * 100.0F);
		graphicsExtractor.text(font, powerLabel, barX, boxY + 18, 0xFF77DDFF, false);
		int statusW = font.width(driveStatus);
		graphicsExtractor.text(font, driveStatus, boxX + boxWidth - statusW - 8, boxY + 18, driveStatusColor, false);

		int bar1Y = boxY + 28;
		int barH = 5;
		// Bar 1 track & fill
		graphicsExtractor.fill(barX, bar1Y, barX + barW, bar1Y + barH, 0xFF141A22);
		int fillPowerW = (int) Math.round(barW * Mth.clamp(enginePower, 0.0F, 1.0F));
		if (fillPowerW > 0) {
			graphicsExtractor.fill(barX, bar1Y, barX + fillPowerW, bar1Y + barH, powerBarColor);
		}
		// Bar 1 frame
		graphicsExtractor.fill(barX - 1, bar1Y - 1, barX + barW + 1, bar1Y, 0xFF354452);
		graphicsExtractor.fill(barX - 1, bar1Y + barH, barX + barW + 1, bar1Y + barH + 1, 0xFF354452);
		graphicsExtractor.fill(barX - 1, bar1Y, barX, bar1Y + barH, 0xFF354452);
		graphicsExtractor.fill(barX + barW, bar1Y, barX + barW + 1, bar1Y + barH, 0xFF354452);

		// 3. Horizontal Bar 2: Engine Load & Resistance threshold (Opór składu)
		float grade = locomotive.getTrackGrade();
		String gradeTag = "";
		if (grade > 0.15F) {
			gradeTag = " ▲ POD GÓRĘ";
		} else if (grade < -0.15F) {
			gradeTag = " ▼ Z GÓRY";
		}
		String wagonTag = wagonsCount > 0 ? " (" + wagonsCount + " wag.)" : "";
		String loadLabel = String.format("Opór składu (Load): %.0f%%%s%s", engineLoad * 100.0F, gradeTag, wagonTag);
		int loadColor = grade < -0.15F ? 0xFF33FFAA : (engineLoad >= 0.65F ? 0xFFFF4444 : (engineLoad >= 0.35F ? 0xFFFFAA00 : 0xFF55DD66));
		graphicsExtractor.text(font, loadLabel, barX, boxY + 37, loadColor, false);

		int bar2Y = boxY + 47;
		// Bar 2 track & fill
		graphicsExtractor.fill(barX, bar2Y, barX + barW, bar2Y + barH, 0xFF141A22);
		int fillLoadW = (int) Math.round(barW * Mth.clamp(engineLoad, 0.0F, 1.0F));
		if (fillLoadW > 0) {
			graphicsExtractor.fill(barX, bar2Y, barX + fillLoadW, bar2Y + barH, loadColor);
		}
		// Bar 2 frame
		graphicsExtractor.fill(barX - 1, bar2Y - 1, barX + barW + 1, bar2Y, 0xFF354452);
		graphicsExtractor.fill(barX - 1, bar2Y + barH, barX + barW + 1, bar2Y + barH + 1, 0xFF354452);
		graphicsExtractor.fill(barX - 1, bar2Y, barX, bar2Y + barH, 0xFF354452);
		graphicsExtractor.fill(barX + barW, bar2Y, barX + barW + 1, bar2Y + barH, 0xFF354452);

		// Threshold marker: white tick mark on both bars showing the exact breakaway threshold
		int thresholdX = barX + fillLoadW;
		if (fillLoadW > 0 && fillLoadW < barW) {
			graphicsExtractor.fill(thresholdX - 1, bar1Y - 1, thresholdX + 1, bar1Y + barH + 1, 0xFFFFFFFF);
			graphicsExtractor.fill(thresholdX - 1, bar2Y - 1, thresholdX + 1, bar2Y + barH + 1, 0xFFFFFFFF);
		}

		// 4. Bottom row: Throttle/Brake + Coupler state
		graphicsExtractor.text(font, throttleText, barX, boxY + 58, throttleColor, false);
		int cWidth = font.width(couplerText);
		graphicsExtractor.text(font, couplerText, boxX + boxWidth - cWidth - 8, boxY + 58, couplerColor, false);
	}
}
