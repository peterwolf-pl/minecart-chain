package com.piotrek.minecartchain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class MinecartChainCommands {
	private MinecartChainCommands() {
	}

	public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(buildRoot("minecartchain"));
		dispatcher.register(buildRoot("locomotive"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(final String name) {
		return Commands.literal(name)
			.executes(ctx -> showStatus(ctx.getSource()))
			.then(Commands.literal("status")
				.executes(ctx -> showStatus(ctx.getSource())))
			.then(Commands.literal("refueling")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setRefuelingRequired(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Locomotive refueling requirement set to: ")
								.append(Component.literal(enabled ? "ENABLED" : "DISABLED")
									.withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD)),
							true
						);
						return 1;
					})))
			.then(Commands.literal("fuel")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setRequireFuel(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Locomotive fuel requirement set to: ")
								.append(Component.literal(enabled ? "ENABLED" : "DISABLED")
									.withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD)),
							true
						);
						return 1;
					})))
			.then(Commands.literal("water")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setRequireWater(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Locomotive water requirement set to: ")
								.append(Component.literal(enabled ? "ENABLED" : "DISABLED")
									.withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD)),
							true
						);
						return 1;
					})))
			.then(Commands.literal("layoutMode")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setLayoutMode(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Model railway layout mode set to: ")
								.append(Component.literal(enabled ? "ENABLED" : "DISABLED")
									.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
								.append(Component.literal(enabled
									? "\n(Pot. 12=Stop, 10=Forward, 0=Reverse; Ch.2=Couple/Uncouple)"
									: "\n(Classic mode: Pot=Speed, Ch.2=Reverse gear)")
									.withStyle(ChatFormatting.GRAY)),
							true
						);
						return 1;
					})))
			.then(Commands.literal("layout")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setLayoutMode(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Model railway layout mode set to: ")
								.append(Component.literal(enabled ? "ENABLED" : "DISABLED")
									.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)),
							true
						);
						return 1;
					})))
			.then(Commands.literal("makieta")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("enabled", BoolArgumentType.bool())
					.executes(ctx -> {
						boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
						MinecartChainConfig.setLayoutMode(enabled);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Tryb makietowy ustawiony na: ")
								.append(Component.literal(enabled ? "WŁĄCZONY" : "WYŁĄCZONY")
									.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)),
							true
						);
						return 1;
					})));
	}

	private static int showStatus(final CommandSourceStack source) {
		boolean fuel = MinecartChainConfig.requireFuel;
		boolean water = MinecartChainConfig.requireWater;
		boolean layout = MinecartChainConfig.layoutMode;
		source.sendSuccess(
			() -> Component.literal("--- Locomotive Settings ---")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("\nLayout Mode (Makieta): ")
					.withStyle(ChatFormatting.WHITE))
				.append(Component.literal(layout ? "ENABLED (Default: 12=Stop, 10=Fwd, 0=Rev, Ch2=Couple)" : "DISABLED (Classic)")
					.withStyle(layout ? ChatFormatting.GREEN : ChatFormatting.RED))
				.append(Component.literal("\nFuel Required: ")
					.withStyle(ChatFormatting.WHITE))
				.append(Component.literal(fuel ? "ENABLED" : "DISABLED (Default)")
					.withStyle(fuel ? ChatFormatting.RED : ChatFormatting.GREEN))
				.append(Component.literal("\nWater Required: ")
					.withStyle(ChatFormatting.WHITE))
				.append(Component.literal(water ? "ENABLED" : "DISABLED (Default)")
					.withStyle(water ? ChatFormatting.RED : ChatFormatting.GREEN))
				.append(Component.literal("\nToggle commands: /minecartchain layoutMode <bool> | refueling <bool>")
					.withStyle(ChatFormatting.GRAY)),
			false
		);
		return 1;
	}
}
