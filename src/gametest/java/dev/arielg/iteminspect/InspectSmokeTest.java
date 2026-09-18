package dev.arielg.iteminspect;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class InspectSmokeTest implements FabricClientGameTest {
	private static final Map<ItemDisplayContext, Matrix4f> SUBMISSIONS = new EnumMap<>(ItemDisplayContext.class);
	private static final Map<ItemDisplayContext, Matrix4f> INPUTS = new EnumMap<>(ItemDisplayContext.class);

	public static void captureInput(ItemDisplayContext context, PoseStack poses) {
		INPUTS.put(context, new Matrix4f(poses.last().pose()));
	}

	public static void capture(ItemDisplayContext context, PoseStack poses) {
		SUBMISSIONS.put(context, new Matrix4f(poses.last().pose()));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		checkTransformMath();
		try (var world = context.worldBuilder().create()) {
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
			world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:torch");
			context.runOnClient(client -> {
				ItemInspectClient.INSPECT_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7));
				KeyMapping.resetMapping();
				client.player.setXRot(0);
				client.player.setYRot(0);
				client.options.bobView().set(false);
			});
			context.waitTicks(40);
			world.getClientWorld().waitForChunksRender();
			context.takeScreenshot("inspect-idle");
			context.getInput().holdKey(ItemInspectClient.INSPECT_KEY);
			context.waitTicks(20);
			context.runOnClient(client -> require(ItemInspectClient.isInspecting(), "Inspect key did not activate"));
			float cameraYaw = context.computeOnClient(client -> client.player.getYRot());
			float cameraPitch = context.computeOnClient(client -> client.player.getXRot());
			// Vanilla discards the first cursor event after grabbing/resizing.
			context.getInput().moveCursor(0, 0);
			context.getInput().moveCursor(80, 40);
			context.waitTicks(2);
			context.runOnClient(client -> {
				require(Math.abs(client.player.getYRot() - cameraYaw) < 0.001F, "Inspect moved camera yaw");
				require(Math.abs(client.player.getXRot() - cameraPitch) < 0.001F, "Inspect moved camera pitch");
				require(Math.abs(ItemInspectClient.getInspectYaw()) > 0.01F, "Mouse did not tilt the item");
			});
			context.takeScreenshot("inspect-tilted");
			// Compare input and submitted poses within the same draw: Punchy's
			// physics can advance between frames even while game ticks are frozen.
			context.runOnClient(client -> assertHandTransforms(true));
			context.getInput().releaseKey(ItemInspectClient.INSPECT_KEY);
			context.waitTicks(20);
			context.runOnClient(client -> {
				require(!ItemInspectClient.isInspecting(), "Inspect stayed active after release");
				require(ItemInspectClient.getCenterOffset() == 0, "Item did not return after release");
			});
			context.takeScreenshot("inspect-released");
			context.runOnClient(client -> assertHandTransforms(false));
			context.getInput().moveCursor(0, 0);
			context.getInput().moveCursor(80, 0);
			context.waitTicks(2);
			context.runOnClient(client -> require(Math.abs(client.player.getYRot() - cameraYaw) > 0.01F,
					"Camera control did not return after release"));
		}
	}

	private static void assertHandTransforms(boolean inspecting) {
		ItemDisplayContext right = INPUTS.containsKey(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
				? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		ItemDisplayContext left = right.firstPerson() ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				: ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		require(INPUTS.containsKey(right) && SUBMISSIONS.containsKey(right), "No main-hand submission");
		boolean unchanged = INPUTS.get(right).equals(SUBMISSIONS.get(right), 0.00001F);
		require(unchanged != inspecting, inspecting ? "Main-hand transform was never rendered" : "Released item is still transformed");
		require(INPUTS.containsKey(left) && SUBMISSIONS.containsKey(left), "No off-hand submission");
		require(INPUTS.get(left).equals(SUBMISSIONS.get(left), 0.00001F), "Inspect altered the off-hand pose");
	}

	private static void checkTransformMath() {
		InspectConfig config = new InspectConfig();
		PoseStack poses = new PoseStack();
		poses.mulPose(Axis.ZP.rotationDegrees(17));
		PoseStack.Pose root = poses.last().copy();
		poses.translate(0.56F, -0.52F, -0.72F);
		poses.mulPose(Axis.XP.rotationDegrees(90));
		poses.scale(0.5F, 0.7F, 0.9F);
		Matrix4f before = new Matrix4f(poses.last().pose());
		InspectTransform.apply(poses, root, HumanoidArm.RIGHT, config, 0, 60, 45);
		require(before.equals(poses.last().pose(), 0.00001F), "Zero inspect offset changed the pose");
		for (HumanoidArm arm : HumanoidArm.values()) {
			poses.pushPose();
			InspectTransform.apply(poses, root, arm, config, 1, 40, 20);
			Matrix4f relative = new Matrix4f(root.pose()).invert().mul(poses.last().pose());
			float expectedX = 0.56F + (arm == HumanoidArm.RIGHT ? -config.translateX : config.translateX);
			require(Math.abs(relative.m30() - expectedX) < 0.00001F, "Translate X is not in view axes");
			require(Math.abs(relative.m31() - (-0.52F + config.translateY)) < 0.00001F, "Tilt moved the pivot Y");
			require(Math.abs(relative.m32() - (-0.72F + config.translateZ)) < 0.00001F, "Tilt moved the pivot Z");
			poses.popPose();
			require(before.equals(poses.last().pose(), 0.00001F), "Inspect leaked out of its pose scope");
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
