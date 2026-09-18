package dev.arielg.iteminspect.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.arielg.iteminspect.InspectTransform;
import dev.arielg.iteminspect.ItemInspectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	@Unique
	private PoseStack.Pose iteminspect$handPose;

	// TEMPORARY diagnostic counter, retained until the modpack playtest passes.
	@Unique
	private int iteminspect$frameCounter;

	// Punchy draws its hands from a HEAD injection here and cancels vanilla's
	// renderArmWithItem, so applyItemArmTransform is never reached. Wrap the
	// whole pass, including other mods' injections, to identify first-person
	// draws even when Punchy uses THIRD_PERSON_* item model transforms.
	@WrapMethod(method = "renderHandsWithItems")
	private void iteminspect$withHandRenderContext(float partialTick, PoseStack poseStack,
			SubmitNodeCollector collector, LocalPlayer player, int light, Operation<Void> original) {
		PoseStack.Pose previous = iteminspect$handPose;
		iteminspect$handPose = poseStack.last().copy();
		try {
			original.call(partialTick, poseStack, collector, player, light);
		} finally {
			iteminspect$handPose = previous;
		}
	}

	// Vanilla and Punchy's ordinary held items both submit here. Scope the
	// change to the submission so it cannot leak into the off-hand, arm mesh,
	// or later draws sharing this stack. Keep the mouse WrapOperation intact.
	@WrapOperation(
			method = "renderItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void iteminspect$submitInspectedItem(ItemStackRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, int light, int overlay, int seed, Operation<Void> original,
			LivingEntity entity, ItemStack item, ItemDisplayContext displayContext,
			PoseStack renderPose, SubmitNodeCollector renderCollector, int renderLight) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		float offset = ItemInspectClient.getCenterOffset();
		boolean handContext = displayContext.firstPerson()
				|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		if (iteminspect$handPose == null || offset <= 0.0F || player == null || entity != player
				|| !client.options.getCameraType().isFirstPerson() || !handContext
				|| displayContext.leftHand() != (player.getMainArm() == HumanoidArm.LEFT)) {
			original.call(state, poseStack, collector, light, overlay, seed);
			return;
		}

		poseStack.pushPose();
		try {
			InspectTransform.apply(poseStack, iteminspect$handPose, player.getMainArm(),
					ItemInspectClient.CONFIG, offset, ItemInspectClient.getInspectYaw(), ItemInspectClient.getInspectPitch());
			if (iteminspect$frameCounter++ % 30 == 0) {
				ItemInspectClient.LOGGER.info("[inspect-debug] item submit, centerOffset={}, displayContext={}, mainArm={}",
						offset, displayContext, player.getMainArm());
			}
			original.call(state, poseStack, collector, light, overlay, seed);
		} finally {
			poseStack.popPose();
		}
	}
}
