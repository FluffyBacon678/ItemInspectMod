package dev.arielg.iteminspect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every value here was a placeholder guess written without being able to see
 * the game run. Edit config/iteminspect.json and restart Minecraft to tune
 * the feel without needing a rebuild. The config is loaded once at startup.
 */
public class InspectConfig {
	// Kept modest by default: vanilla (and most hand-rendering mods) don't
	// attach a moving arm to every item type - a large swing or shift reads
	// as the item flying away from a hand that's staying put, rather than
	// being tilted in place. Bigger items (blocks especially) make this more
	// obvious since the same offset covers more of the screen.
	public float maxYawDegrees = 25.0F;
	public float maxPitchDegrees = 20.0F;
	public float tiltSensitivity = 1.0F;
	// Exponential ease-per-tick toward the inspect pose; higher = snappier.
	public float easePerTick = 0.35F;
	// Hand-view translate at full centerOffset; X mirrors for a left-handed player.
	public float translateX = 0.12F;
	public float translateY = 0.06F;
	// Kept at 0: pushing the item toward the camera reads as it popping out
	// of the hand's grip rather than tilting in place.
	public float translateZ = 0.0F;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(ItemInspectClient.MOD_ID + ".json");

	public static InspectConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				InspectConfig loaded = GSON.fromJson(reader, InspectConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				ItemInspectClient.LOGGER.warn("Failed to read {}, using defaults", PATH, e);
			}
		}

		InspectConfig defaults = new InspectConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			ItemInspectClient.LOGGER.warn("Failed to write {}", PATH, e);
		}
	}
}
