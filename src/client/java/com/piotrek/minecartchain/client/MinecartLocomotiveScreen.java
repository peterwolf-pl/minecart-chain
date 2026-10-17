package com.piotrek.minecartchain.client;

import com.piotrek.minecartchain.MinecartLocomotiveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class MinecartLocomotiveScreen extends AbstractContainerScreen<MinecartLocomotiveMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier LIT_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final int WATER_BAR_COLOR = 0xFF3F76E4;
	private static final int WATER_BAR_BACKGROUND = 0xFF1F2A44;

	public MinecartLocomotiveScreen(final MinecartLocomotiveMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float tickProgress) {
		super.render(graphics, mouseX, mouseY, tickProgress);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(final GuiGraphics graphics, final float tickProgress, final int mouseX, final int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		this.extractFuelBar(graphics, x, y);
		this.extractWaterBar(graphics, x, y);
	}

	@Override
	protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		graphics.drawString(this.font, Component.translatable("container.minecart_chain.fuel"), 80, 19, -12566464, false);
		graphics.drawString(this.font, Component.literal(formatTicks(this.menu.fuelTicks(), this.menu.maxFuelTicks())), 80, 30, -12566464, false);
		graphics.drawString(this.font, Component.translatable("container.minecart_chain.water"), 80, 55, -12566464, false);
		graphics.drawString(this.font, Component.literal(formatTicks(this.menu.waterTicks(), this.menu.maxWaterTicks())), 80, 66, -12566464, false);
	}

	private void extractFuelBar(final GuiGraphics graphics, final int x, final int y) {
		if (this.menu.fuelTicks() <= 0) {
			return;
		}

		int height = Mth.ceil(this.menu.fuelProgress() * 13.0F) + 1;
		graphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			LIT_PROGRESS_SPRITE,
			14,
			14,
			0,
			14 - height,
			x + 56,
			y + 36 + 14 - height,
			14,
			height
		);
	}

	private void extractWaterBar(final GuiGraphics graphics, final int x, final int y) {
		int barX = x + 56;
		int barY = y + 18;
		int width = 14;
		int height = 14;
		int filledHeight = Mth.ceil(this.menu.waterProgress() * height);
		graphics.fill(barX, barY, barX + width, barY + height, WATER_BAR_BACKGROUND);
		if (filledHeight > 0) {
			graphics.fill(barX, barY + height - filledHeight, barX + width, barY + height, WATER_BAR_COLOR);
		}
	}

	private static String formatTicks(final int ticks, final int maxTicks) {
		return seconds(ticks) + "s / " + seconds(maxTicks) + "s";
	}

	private static int seconds(final int ticks) {
		return Math.max(0, ticks) / 20;
	}
}
