package com.piotrek.minecartchain.client;

import com.piotrek.minecartchain.RadioLocomotiveJumperPayload;
import com.piotrek.minecartchain.RadioLocomotiveMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class RadioLocomotiveScreen extends AbstractContainerScreen<RadioLocomotiveMenu> {
	private static final Identifier FURNACE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier LIT_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");

	// Screen dimensions
	public static final int SCREEN_WIDTH = 216;
	public static final int SCREEN_HEIGHT = 220;

	// Module positioning
	private static final int MODULE_WIDTH = 96;
	private static final int MODULE_HEIGHT = 74;
	private static final int THROTTLE_MOD_X = 10;
	private static final int REVERSE_MOD_X = 110;
	private static final int MODULE_Y = 16;

	// Jumper grid inside module (2 rows of 4, exactly like the TX/RX block):
	// Row 1 (back / top): bits 4, 5, 6, 7
	// Row 0 (front / bottom): bits 0, 1, 2, 3
	private static final int JUMPER_COL_START = 8;
	private static final int JUMPER_COL_SPACING = 21;
	private static final int JUMPER_ROW_TOP_Y = 28;    // bits 4, 5, 6, 7
	private static final int JUMPER_ROW_BOTTOM_Y = 48; // bits 0, 1, 2, 3
	private static final int JUMPER_W = 16;
	private static final int JUMPER_H = 17;

	// TX/RX Palette matching update_switches_texture.py and generate_textures.py:
	// Wool carpet border:
	private static final int COLOR_WOOL_BASE = 0xFFE6E6E6;
	private static final int COLOR_WOOL_BORDER = 0xFFCCCCCC;
	// PCB Surface:
	private static final int COLOR_PCB_BG = 0xFF243026;
	private static final int COLOR_PCB_BORDER = 0xFF323D34;
	private static final int COLOR_COPPER_TRACE = 0xFF8C5F2D;
	private static final int COLOR_GOLD_PAD = 0xFFB48C32;
	// Socket:
	private static final int COLOR_SOCKET_OUTER = 0xFF191919; // (25, 25, 25)
	private static final int COLOR_SOCKET_MID = 0xFF262626;   // (38, 38, 38)
	private static final int COLOR_SOCKET_TRACK = 0xFF141414; // (20, 20, 20)
	// Jumper OFF (Dark graphite/unlit):
	private static final int COLOR_OFF_OUTER = 0xFF37373A;    // (55, 55, 58)
	private static final int COLOR_OFF_BODY = 0xFF4B4B50;     // (75, 75, 80)
	private static final int COLOR_OFF_CORE = 0xFF5F5F64;     // (95, 95, 100)
	private static final int COLOR_OFF_LIGHT = 0xFF737378;    // (115, 115, 120)
	// Jumper ON (Glowing radiant red with hot filament core):
	private static final int COLOR_ON_OUTER = 0xFFC8140F;     // (200, 20, 15)
	private static final int COLOR_ON_BODY = 0xFFF52819;      // (245, 40, 25)
	private static final int COLOR_ON_CORE = 0xFFFF5532;      // (255, 85, 50)
	private static final int COLOR_ON_FILAMENT = 0xFFFFBE8C;  // (255, 190, 140)

	private static final int WATER_BAR_COLOR = 0xFF3F76E4;
	private static final int WATER_BAR_BG = 0xFF1F2A44;

	public RadioLocomotiveScreen(final RadioLocomotiveMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		this.titleLabelY = 4;

		// Add 16 interactive Jumper widgets (8 for Throttle, 8 for Reverse)
		// 2 rows of 4 jumpers per module:
		for (int r = 0; r < 2; r++) {
			int relY = (r == 0) ? JUMPER_ROW_BOTTOM_Y : JUMPER_ROW_TOP_Y;
			for (int c = 0; c < 4; c++) {
				int bit = r * 4 + c;
				int relX = JUMPER_COL_START + c * JUMPER_COL_SPACING;

				// Throttle module switch
				this.addRenderableWidget(new JumperWidget(
					this.leftPos + THROTTLE_MOD_X + relX,
					this.topPos + MODULE_Y + relY,
					JUMPER_W,
					JUMPER_H,
					false,
					bit
				));

				// Reverse module switch
				this.addRenderableWidget(new JumperWidget(
					this.leftPos + REVERSE_MOD_X + relX,
					this.topPos + MODULE_Y + relY,
					JUMPER_W,
					JUMPER_H,
					true,
					bit
				));
			}
		}
	}

	void onJumperPressed(final boolean isReverse, final int bit) {
		int ch = isReverse ? this.menu.reverseChannel() : this.menu.throttleChannel();
		boolean current = (ch & (1 << bit)) != 0;
		boolean targetState = !current;

		// 1. Optimistic instant local update
		this.menu.setBitLocally(isReverse, bit, targetState);

		// 2. Reliable Fabric network packet with idempotent target state
		ClientPlayNetworking.send(new RadioLocomotiveJumperPayload(isReverse, bit, targetState));

		if (this.minecraft != null) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float tickProgress) {
		super.extractBackground(graphics, mouseX, mouseY, tickProgress);
		int x = this.leftPos;
		int y = this.topPos;

		// 1. Outer dark steel panel
		graphics.fill(x, y, x + SCREEN_WIDTH, y + 128, 0xFF1C1F22);
		graphics.outline(x, y, SCREEN_WIDTH, 128, 0xFF0D0F11);
		graphics.horizontalLine(x, x + SCREEN_WIDTH - 1, y, 0xFF363B40);
		graphics.verticalLine(x, y, y + 127, 0xFF363B40);

		// 2. Draw Left TX/RX Module (Throttle / Potentiometer)
		this.drawTxRxBlockPad(graphics, x + THROTTLE_MOD_X, y + MODULE_Y, false);

		// 3. Draw Right TX/RX Module (Reverse)
		this.drawTxRxBlockPad(graphics, x + REVERSE_MOD_X, y + MODULE_Y, true);

		// 4. Center Refuel / Status Bar (y: 94..124)
		this.drawCenterConsole(graphics, x, y);

		// 5. Draw lower player inventory background (centered 176px at x+20, y+128)
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			FURNACE_TEXTURE,
			x + 20,
			y + 128,
			0.0F,
			83.0F,
			176,
			84,
			256,
			256
		);

		// 6. Draw input slot frames for fuel and water (slots 0 & 1 at x+67, y+99 and x+131, y+99)
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			FURNACE_TEXTURE,
			x + 67,
			y + 99,
			55.0F,
			52.0F,
			18,
			18,
			256,
			256
		);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			FURNACE_TEXTURE,
			x + 131,
			y + 99,
			55.0F,
			52.0F,
			18,
			18,
			256,
			256
		);

		// 7. Extract fuel and water indicator bars
		this.extractFuelBar(graphics, x + 48, y + 101);
		this.extractWaterBar(graphics, x + 154, y + 101);
	}

	private void drawTxRxBlockPad(
		final GuiGraphicsExtractor graphics,
		final int bx,
		final int by,
		final boolean isReverse
	) {
		// A. White wool carpet base (outer border)
		graphics.fill(bx, by, bx + MODULE_WIDTH, by + MODULE_HEIGHT, COLOR_WOOL_BASE);
		graphics.outline(bx, by, MODULE_WIDTH, MODULE_HEIGHT, COLOR_WOOL_BORDER);

		// Subtle noise / wool border shadow
		graphics.fill(bx + 1, by + 1, bx + MODULE_WIDTH - 1, by + MODULE_HEIGHT - 1, 0xFFEDEDED);

		// B. Inset dark green PCB board surface
		int px = bx + 3;
		int py = by + 3;
		int pw = MODULE_WIDTH - 6;
		int ph = MODULE_HEIGHT - 6;
		graphics.fill(px, py, px + pw, py + ph, COLOR_PCB_BG);
		graphics.outline(px, py, pw, ph, COLOR_PCB_BORDER);

		// C. Copper traces on PCB
		graphics.horizontalLine(px + 4, px + pw - 5, py + 22, COLOR_COPPER_TRACE);
		graphics.horizontalLine(px + 4, px + pw - 5, py + 43, COLOR_COPPER_TRACE);
		graphics.verticalLine(px + 4, py + 6, py + ph - 6, COLOR_COPPER_TRACE);
		graphics.verticalLine(px + pw - 5, py + 6, py + ph - 6, COLOR_COPPER_TRACE);

		// Gold contact pads near corners
		graphics.fill(px + 2, py + 2, px + 5, py + 5, COLOR_GOLD_PAD);
		graphics.fill(px + pw - 6, py + 2, px + pw - 3, py + 5, COLOR_GOLD_PAD);

		// D. Port terminal header at bottom (front of block facing player)
		int portX = px + 18;
		int portW = pw - 36;
		int portY = py + ph - 4;
		graphics.fill(portX, portY, portX + portW, portY + 3, 0xFF1E1E1E);
		for (int p = 0; p < 6; p++) {
			int pCol = (p % 2 == 0) ? (isReverse ? 0xFF3DA5E0 : 0xFFE04030) : COLOR_GOLD_PAD;
			graphics.fill(portX + 2 + p * 6, portY + 1, portX + 5 + p * 6, portY + 3, pCol);
		}
	}

	private void drawCenterConsole(final GuiGraphicsExtractor graphics, final int x, final int y) {
		int cy = y + 92;
		// Center recessed tray
		graphics.fill(x + 10, cy, x + SCREEN_WIDTH - 10, cy + 32, 0xFF141619);
		graphics.outline(x + 10, cy, SCREEN_WIDTH - 20, 32, 0xFF0B0C0E);

		boolean isLayout = this.menu.isLayoutMode();
		int pwr = this.menu.throttlePower();

		if (isLayout) {
			boolean armed = this.menu.isArmed();
			if (!armed) {
				graphics.text(this.font, Component.literal("LOCKED: SET 50% TO ARM").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), x + 12, cy + 3, -1, false);
				graphics.text(this.font, Component.literal("Knob at 12:00 unlocks drive").withStyle(ChatFormatting.GRAY), x + 12, cy + 14, -1, false);
			} else {
				String modeDesc;
				ChatFormatting modeColor;
				if (pwr == 7 || pwr == 8) {
					modeDesc = "50%: STOP [12:00]";
					modeColor = ChatFormatting.YELLOW;
				} else if (pwr < 7) {
					int revPct = (int) Math.round(((7.0D - pwr) / 7.0D) * 100.0D);
					modeDesc = "REV " + revPct + "% [7:30]";
					modeColor = ChatFormatting.AQUA;
				} else {
					int fwdPct = (int) Math.round(((pwr - 8.0D) / 7.0D) * 100.0D);
					modeDesc = "FWD " + fwdPct + "% [5:30]";
					modeColor = ChatFormatting.GREEN;
				}
				graphics.text(this.font, Component.literal(modeDesc).withStyle(modeColor, ChatFormatting.BOLD), x + 12, cy + 3, -1, false);

				// Mini 10-bar LED under throttle
				int ledStartX = x + 12;
				int ledY = cy + 14;
				for (int i = 0; i < 10; i++) {
					boolean on;
					int col;
					if (pwr == 7 || pwr == 8) {
						on = (i == 4 || i == 5);
						col = 0xFFE5A823; // Yellow center stop
					} else if (pwr < 7) {
						on = (i < 5);
						col = 0xFF3DA5E0; // Blue reverse
					} else {
						on = (i >= 5);
						col = 0xFF4AE04A; // Green forward
					}
					graphics.fill(ledStartX + i * 3, ledY, ledStartX + i * 3 + 2, ledY + 4, on ? col : 0xFF202A20);
				}
			}

			// Coupler status under module 2 (Ch.2: HIGH=Couple, LOW=Uncouple)
			int links = this.menu.linkCount();
			int couplerSig = this.menu.reversePower();
			Component couplerText;
			if (couplerSig > 0) {
				couplerText = Component.literal(links > 0 ? "● COUPLED (" + links + " wagons)" : "READY TO COUPLE (<2b)")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
			} else {
				couplerText = Component.literal(links > 0 ? "UNCOUPLING..." : "○ UNCOUPLED (Signal LOW)")
					.withStyle(ChatFormatting.GRAY);
			}
			graphics.text(this.font, couplerText, x + 110, cy + 3, -1, false);

			String couplerHint = (couplerSig > 0)
				? "Signal HIGH (Coupler ON)"
				: "Signal LOW (Coupler OFF)";
			graphics.text(this.font, Component.literal(couplerHint).withStyle(ChatFormatting.DARK_GRAY), x + 110, cy + 14, -1, false);
		} else {
			// Classic mode readout
			int pct = (int) Math.round((pwr / 15.0D) * 100.0D);
			String pwrStr = "Pwr: " + pwr + "/15 (" + pct + "%)";
			graphics.text(this.font, Component.literal(pwrStr).withStyle(ChatFormatting.GOLD), x + 12, cy + 3, -1, false);

			// Mini 10-bar LED under throttle
			int ledStartX = x + 12;
			int ledY = cy + 14;
			for (int i = 0; i < 10; i++) {
				boolean on = (i * 1.5) < pwr;
				int col = on ? (i < 4 ? 0xFF4AE04A : (i < 7 ? 0xFFE0DA4A : 0xFFE04A4A)) : 0xFF202A20;
				graphics.fill(ledStartX + i * 3, ledY, ledStartX + i * 3 + 2, ledY + 4, col);
			}

			// Reverse Status under Reverse module
			boolean rev = this.menu.isReversed();
			boolean pending = this.menu.isReversePending();
			Component status;
			if (pending) {
				status = Component.literal("STOP TRAIN TO REVERSE!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
			} else if (rev) {
				status = Component.literal("◄ REVERSE ACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
			} else {
				status = Component.literal("► FORWARD ACTIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
			}
			graphics.text(this.font, status, x + 110, cy + 3, -1, false);

			if (pending) {
				graphics.text(
					this.font,
					Component.literal("Signal ON, waiting for stop").withStyle(ChatFormatting.GRAY),
					x + 110,
					cy + 14,
					-1,
					false
				);
			}
		}
	}

	private void extractFuelBar(final GuiGraphicsExtractor graphics, final int x, final int y) {
		int width = 14;
		int height = 14;
		graphics.fill(x, y, x + width, y + height, 0xFF2B1C10);
		if (this.menu.fuelTicks() > 0) {
			int flameH = Mth.ceil(this.menu.fuelProgress() * 13.0F) + 1;
			graphics.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				LIT_PROGRESS_SPRITE,
				14,
				14,
				0,
				14 - flameH,
				x,
				y + 14 - flameH,
				14,
				flameH
			);
		}
	}

	private void extractWaterBar(final GuiGraphicsExtractor graphics, final int x, final int y) {
		int width = 14;
		int height = 14;
		int filledHeight = Mth.ceil(this.menu.waterProgress() * height);
		graphics.fill(x, y, x + width, y + height, WATER_BAR_BG);
		if (filledHeight > 0) {
			graphics.fill(x, y + height - filledHeight, x + width, y + height, WATER_BAR_COLOR);
		}
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);

		boolean isLayout = this.menu.isLayoutMode();

		// Module Header Titles:
		// Left: [RX] POTENTIOMETER / SPEED (CH: X)
		int thCh = this.menu.throttleChannel();
		String leftTitle = isLayout ? "[RX] SPEED (12/10/0)" : "[RX] POTENTIOMETER";
		graphics.text(
			this.font,
			Component.literal(leftTitle).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
			THROTTLE_MOD_X + 6,
			MODULE_Y + 5,
			-1,
			false
		);
		graphics.text(
			this.font,
			Component.literal("CH: " + thCh + " ").withStyle(ChatFormatting.GOLD)
				.append(getChannelDots(thCh)),
			THROTTLE_MOD_X + 6,
			MODULE_Y + 15,
			-1,
			false
		);

		// Right: [RX] COUPLER / REVERSE (CH: X)
		int revCh = this.menu.reverseChannel();
		String rightTitle = isLayout ? "[RX] COUPLER (<2b)" : "[RX] REVERSE";
		graphics.text(
			this.font,
			Component.literal(rightTitle).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
			REVERSE_MOD_X + 6,
			MODULE_Y + 5,
			-1,
			false
		);
		graphics.text(
			this.font,
			Component.literal("CH: " + revCh + " ").withStyle(ChatFormatting.AQUA)
				.append(getChannelDots(revCh)),
			REVERSE_MOD_X + 6,
			MODULE_Y + 15,
			-1,
			false
		);

		// Refuel Labels
		graphics.text(this.font, Component.literal("Fuel").withStyle(ChatFormatting.GRAY), 68, 90, -1, false);
		graphics.text(this.font, Component.literal("Water").withStyle(ChatFormatting.GRAY), 132, 90, -1, false);

		// Tooltips for Fuel and Water
		if (mouseX >= this.leftPos + 48 && mouseX <= this.leftPos + 86 && mouseY >= this.topPos + 98 && mouseY <= this.topPos + 118) {
			graphics.setTooltipForNextFrame(
				this.font,
				Component.literal("Fuel Tank: " + (this.menu.fuelTicks() / 20) + "s / " + (this.menu.maxFuelTicks() / 20) + "s")
					.withStyle(ChatFormatting.GOLD),
				mouseX,
				mouseY
			);
		}
		if (mouseX >= this.leftPos + 130 && mouseX <= this.leftPos + 168 && mouseY >= this.topPos + 98 && mouseY <= this.topPos + 118) {
			graphics.setTooltipForNextFrame(
				this.font,
				Component.literal("Water Tank: " + (this.menu.waterTicks() / 20) + "s / " + (this.menu.maxWaterTicks() / 20) + "s")
					.withStyle(ChatFormatting.AQUA),
				mouseX,
				mouseY
			);
		}
	}

	private static Component getChannelDots(final int channel) {
		MutableComponent comp = Component.empty();
		for (int i = 0; i < 8; i++) {
			boolean on = (channel & (1 << i)) != 0;
			comp.append(Component.literal(on ? "●" : "○")
				.withStyle(on ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
		}
		return comp;
	}

	// Interactive Jumper Widget designed to look identical to the TX/RX 3D block
	private final class JumperWidget extends AbstractWidget {
		private final boolean isReverse;
		private final int bit;

		JumperWidget(final int x, final int y, final int w, final int h, final boolean isReverse, final int bit) {
			super(x, y, w, h, Component.empty());
			this.isReverse = isReverse;
			this.bit = bit;
		}

		private boolean isOn() {
			int ch = this.isReverse ? RadioLocomotiveScreen.this.menu.reverseChannel() : RadioLocomotiveScreen.this.menu.throttleChannel();
			return (ch & (1 << this.bit)) != 0;
		}

		@Override
		public void onClick(final net.minecraft.client.input.MouseButtonEvent event, final boolean doubleClick) {
			RadioLocomotiveScreen.this.onJumperPressed(this.isReverse, this.bit);
		}

		@Override
		protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float tickProgress) {
			int x0 = this.getX();
			int y0 = this.getY();
			int w = this.width;
			int h = this.height;
			boolean on = isOn();

			// 1. Socket base frame flush on carpet surface (like TxRxBlockEntityRenderer)
			graphics.fill(x0, y0, x0 + w, y0 + h, COLOR_SOCKET_OUTER);
			graphics.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, COLOR_SOCKET_MID);
			graphics.fill(x0 + 3, y0 + 2, x0 + w - 3, y0 + h - 2, COLOR_SOCKET_TRACK);

			// Two gold contact pins (at top and bottom of socket)
			graphics.fill(x0 + 5, y0 + 3, x0 + 7, y0 + 5, COLOR_GOLD_PAD);
			graphics.fill(x0 + w - 7, y0 + 3, x0 + w - 5, y0 + 5, COLOR_GOLD_PAD);
			graphics.fill(x0 + 5, y0 + h - 5, x0 + 7, y0 + h - 3, COLOR_GOLD_PAD);
			graphics.fill(x0 + w - 7, y0 + h - 5, x0 + w - 5, y0 + h - 3, COLOR_GOLD_PAD);

			// 2. The Jumper Cap itself!
			// Shift position along Z/Y based on state:
			// ON / front / down = +3 px shift (covers bottom pins)
			// OFF / back / up = -3 px shift (covers top pins)
			int capW = w - 4; // 12 px
			int capH = 8;     // 8 px
			int capX = x0 + 2;
			int capY = on ? (y0 + h - capH - 2) : (y0 + 2);

			if (on) {
				// JUMPER LIGHTS UP GLOWING RED! (identical to TX/RX block)
				// Soft glow aura around cap:
				graphics.fill(capX - 1, capY - 1, capX + capW + 1, capY + capH + 1, 0x55FF2819);

				// Outer body:
				graphics.fill(capX, capY, capX + capW, capY + capH, COLOR_ON_OUTER);
				graphics.fill(capX + 1, capY + 1, capX + capW - 1, capY + capH - 1, COLOR_ON_BODY);
				// Center glow:
				graphics.fill(capX + 2, capY + 2, capX + capW - 2, capY + capH - 2, COLOR_ON_CORE);
				// Hot filament core:
				graphics.fill(capX + 4, capY + 3, capX + capW - 4, capY + capH - 3, COLOR_ON_FILAMENT);
			} else {
				// JUMPER IS UNLIT (DARK GRAPHITE/GRAY)
				graphics.fill(capX, capY, capX + capW, capY + capH, COLOR_OFF_OUTER);
				graphics.fill(capX + 1, capY + 1, capX + capW - 1, capY + capH - 1, COLOR_OFF_BODY);
				graphics.fill(capX + 2, capY + 2, capX + capW - 2, capY + capH - 2, COLOR_OFF_CORE);
				graphics.horizontalLine(capX + 2, capX + capW - 3, capY + 2, COLOR_OFF_LIGHT);
			}

			// Hover highlight
			if (this.isHovered()) {
				graphics.outline(x0 - 1, y0 - 1, w + 2, h + 2, 0xAAFFFFFF);
				String typeName = this.isReverse ? "Reverse" : "Throttle";
				int val = 1 << this.bit;
				graphics.setTooltipForNextFrame(
					RadioLocomotiveScreen.this.font,
					Component.literal(typeName + " Switch #" + (this.bit + 1) + " (Val: " + val + "): ")
						.append(Component.literal(on ? "ON (Glow)" : "OFF (Unlit)")
							.withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY, ChatFormatting.BOLD)),
					mouseX,
					mouseY
				);
			}
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
		}
	}
}
