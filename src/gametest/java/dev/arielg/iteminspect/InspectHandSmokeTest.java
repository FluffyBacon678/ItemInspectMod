package dev.arielg.iteminspect;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Visual-only check (not an assertion suite) for InspectHandRenderer: holds
 * a plain block, inspects it, and screenshots idle/tilted/released so the
 * synthetic hand's position can actually be looked at. checkTransformMath-
 * style matrix assertions don't apply here since there's no "correct"
 * numeric hand pose to check against - this is for eyeballing only.
 */
public class InspectHandSmokeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			context.runOnClient(client -> {
				ItemInspectClient.INSPECT_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7));
				KeyMapping.resetMapping();
				client.player.setXRot(0);
				client.player.setYRot(0);
				client.options.bobView().set(false);
			});
			context.waitTicks(40);
			world.getClientWorld().waitForChunksRender();
			// Sanity check: does vanilla's own empty-hand fist even render in
			// this headless test environment at all? If not, that's an
			// environment limitation, not a bug in our code.
			context.takeScreenshot("empty-hand-baseline");
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:oak_planks");
			world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:torch");
			context.waitTicks(10);
			context.takeScreenshot("hand-idle");
			context.getInput().holdKey(ItemInspectClient.INSPECT_KEY);
			context.waitTicks(20);
			context.getInput().moveCursor(0, 0);
			context.getInput().moveCursor(60, 30);
			context.waitTicks(2);
			context.takeScreenshot("hand-tilted");
			context.getInput().releaseKey(ItemInspectClient.INSPECT_KEY);
			context.waitTicks(20);
			context.takeScreenshot("hand-released");
		}
	}
}
