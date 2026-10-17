package com.piotrek.minecartchain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class MinecartChainConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("minecart_chain.json");

	// Default: disabled as requested by user!
	public static boolean requireFuel = false;
	public static boolean requireWater = false;

	// Layout / Model railway mode: enabled by default as requested by user!
	public static boolean layoutMode = true;

	private MinecartChainConfig() {
	}

	public static boolean isRefuelingRequired() {
		return requireFuel || requireWater;
	}

	public static void setRefuelingRequired(final boolean required) {
		requireFuel = required;
		requireWater = required;
		save();
	}

	public static void setRequireFuel(final boolean required) {
		requireFuel = required;
		save();
	}

	public static void setRequireWater(final boolean required) {
		requireWater = required;
		save();
	}

	public static void setLayoutMode(final boolean enabled) {
		layoutMode = enabled;
		save();
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			if (json != null) {
				if (json.has("requireFuel")) {
					requireFuel = json.get("requireFuel").getAsBoolean();
				}
				if (json.has("requireWater")) {
					requireWater = json.get("requireWater").getAsBoolean();
				}
				if (json.has("layoutMode")) {
					layoutMode = json.get("layoutMode").getAsBoolean();
				}
			}
		} catch (Exception e) {
			MinecartChainMod.LOGGER.error("Failed to load minecart_chain config", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				JsonObject json = new JsonObject();
				json.addProperty("requireFuel", requireFuel);
				json.addProperty("requireWater", requireWater);
				json.addProperty("layoutMode", layoutMode);
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			MinecartChainMod.LOGGER.error("Failed to save minecart_chain config", e);
		}
	}
}
