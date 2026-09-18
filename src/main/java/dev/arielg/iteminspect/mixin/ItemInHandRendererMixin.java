package dev.arielg.iteminspect.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arielg.iteminspect.InspectConfig;
import dev.arielg.iteminspect.ItemInspectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	// TEMPORARY debug counter - remove once the render transform is confirmed working.
	private static int iteminspect$frameCounter = 0;
	// applyItemArmTransform() is the one place vanilla positions the held item
	// for its normal resting/idle pose (attack swings and special use-animations
	// like eating/blocking/drawing a bow apply their own transforms instead, and
	// those are exactly the cases ItemInspectClient already forces inspect off
	// for) - injecting at its tail lets us add our translate/tilt on top of the
	// vanilla pose instead of re-deriving it.
	@Inject(
			method = "applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
			at = @At("TAIL")
	)
	private void iteminspect$applyInspectTransform(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
		float centerOffset = ItemInspectClient.getCenterOffset();
		if (centerOffset <= 0.0F) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		boolean mainHandMatch = player != null && humanoidArm == player.getMainArm();
		if (iteminspect$frameCounter++ % 30 == 0) {
			ItemInspectClient.LOGGER.info(
					"[inspect-debug] applyItemArmTransform tail hit, centerOffset={}, humanoidArm={}, mainArm={}, mainHandMatch={}",
					centerOffset, humanoidArm, player == null ? "null" : player.getMainArm(), mainHandMatch
			);
		}
		// Main hand only for v1; the off-hand item keeps rendering normally.
		if (!mainHandMatch) {
			return;
		}

		// Offsets come from InspectConfig (config/iteminspect.json) - not visually
		// tuned yet, this environment can't launch the actual game to eyeball it.
		// Edit that file and rejoin to adjust by feel without a rebuild.
		InspectConfig config = ItemInspectClient.CONFIG;
		int side = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(-side * config.translateX * centerOffset, config.translateY * centerOffset, config.translateZ * centerOffset);
		poseStack.mulPose(Axis.YP.rotationDegrees(ItemInspectClient.getInspectYaw() * centerOffset));
		poseStack.mulPose(Axis.XP.rotationDegrees(-ItemInspectClient.getInspectPitch() * centerOffset));
	}
}
