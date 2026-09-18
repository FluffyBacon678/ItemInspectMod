package dev.arielg.iteminspect;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemInspectClient implements ClientModInitializer {
	public static final String MOD_ID = "iteminspect";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final InspectConfig CONFIG = InspectConfig.load();

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "main")
	);

	// Unbound by default (InputConstants.UNKNOWN) so it can't collide with an existing bind;
	// the player assigns it themselves in Options > Controls > Key Binds.
	public static final KeyMapping INSPECT_KEY = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
					"key.iteminspect.inspect",
					InputConstants.Type.KEYSYM,
					InputConstants.UNKNOWN.getValue(),
					CATEGORY
			)
	);

	// Target state: true while the key is held and no forced-exit condition applies.
	private static boolean active = false;
	private static boolean wasActive = false;

	// Eased 0..1 toward `active`; drives both the render-side lerp and how much
	// of inspectYaw/inspectPitch is actually applied.
	private static float centerOffset = 0.0F;

	// Accumulated mouse-driven tilt, in degrees, clamped to +/-MAX_*.
	private static float inspectYaw = 0.0F;
	private static float inspectPitch = 0.0F;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			active = wantsInspect(client);
			if (active && !wasActive) {
				// Fresh entry: don't carry over tilt from a previous inspect session.
				inspectYaw = 0.0F;
				inspectPitch = 0.0F;
			}
			wasActive = active;

			float target = active ? 1.0F : 0.0F;
			centerOffset += (target - centerOffset) * CONFIG.easePerTick;
			if (Math.abs(target - centerOffset) < 0.001F) {
				centerOffset = target;
			}

			// TEMPORARY debug readout - remove once the render transform is confirmed working.
			if (active && client.player != null) {
				client.player.displayClientMessage(
						Component.literal(String.format(
								"[inspect] offset=%.2f yaw=%.1f pitch=%.1f mainArm=%s",
								centerOffset, inspectYaw, inspectPitch, client.player.getMainArm()
						)),
						true
				);
			}
		});
	}

	private static boolean wantsInspect(Minecraft client) {
		if (!INSPECT_KEY.isDown()) {
			return false;
		}
		LocalPlayer player = client.player;
		if (player == null || !player.isAlive()) {
			return false;
		}
		if (client.screen != null) {
			return false;
		}
		if (!client.isWindowActive()) {
			return false;
		}
		if (!client.options.getCameraType().isFirstPerson() || player.isSpectator()) {
			return false;
		}
		// Attacking/using the held item drives its own first-person transforms
		// (swing, eat, block, draw...) that would fight ours - bail out and let
		// those play normally.
		if (client.options.keyAttack.isDown() || client.options.keyUse.isDown()) {
			return false;
		}
		return true;
	}

	public static boolean isInspecting() {
		return active;
	}

	public static float getCenterOffset() {
		return centerOffset;
	}

	public static float getInspectYaw() {
		return inspectYaw;
	}

	public static float getInspectPitch() {
		return inspectPitch;
	}

	/**
	 * Called from the mouse-redirect mixin with the same deltas that would
	 * otherwise have gone to {@code Entity.turn(d, e)}.
	 */
	public static void accumulateTilt(double d, double e) {
		float scale = 0.15F * CONFIG.tiltSensitivity;
		inspectYaw = Mth.clamp(inspectYaw + (float) d * scale, -CONFIG.maxYawDegrees, CONFIG.maxYawDegrees);
		inspectPitch = Mth.clamp(inspectPitch + (float) e * scale, -CONFIG.maxPitchDegrees, CONFIG.maxPitchDegrees);
	}
}
