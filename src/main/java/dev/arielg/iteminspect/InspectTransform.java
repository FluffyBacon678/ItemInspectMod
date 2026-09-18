package dev.arielg.iteminspect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

public final class InspectTransform {
	private InspectTransform() {
	}

	public static void apply(PoseStack poseStack, PoseStack.Pose handPose, HumanoidArm arm,
			InspectConfig config, float offset, float yaw, float pitch) {
		// Work in the hand pass's view axes, not Punchy's already rotated/scaled
		// item axes. Rotate around the item's origin so it tilts instead of orbiting
		// the camera. Rebuild through PoseStack to preserve correct normal matrices.
		Matrix4f itemPose = new Matrix4f(handPose.pose()).invert().mul(poseStack.last().pose());
		float x = itemPose.m30();
		float y = itemPose.m31();
		float z = itemPose.m32();
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.last().set(handPose);
		poseStack.translate(x - side * config.translateX * offset,
				y + config.translateY * offset, z + config.translateZ * offset);
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw * offset));
		poseStack.mulPose(Axis.XP.rotationDegrees(-pitch * offset));
		poseStack.translate(-x, -y, -z);
		poseStack.mulPose(itemPose);
	}
}
