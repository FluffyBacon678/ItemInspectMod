package dev.arielg.iteminspect.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	// Vanilla's own empty-hand arm renderer (mulPose/translate math, texture,
	// sleeve visibility, AvatarRenderer plumbing all handled correctly by it
	// already). Shadowing and calling it directly - instead of re-deriving
	// its positioning ourselves - avoids depending on undocumented
	// preconditions of AvatarRenderer.renderRightHand/renderLeftHand when
	// called outside their normal full-body-render context.
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm) {
		throw new AssertionError();
	}

	@Unique
	private PoseStack.Pose iteminspect$handPose;

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

		float yaw = ItemInspectClient.getInspectYaw();
		float pitch = ItemInspectClient.getInspectPitch();

		// Punchy already attaches a moving arm for the item kinds it gives
		// custom animation to (swords, tools, bows...) - drawing our own on
		// top would double up. Blocks are the confirmed gap: Punchy falls
		// back to a path that doesn't reach here, so nothing else is going
		// to draw a hand for them. This is our own classification, not
		// Punchy's - it doesn't touch Punchy's code or internals at all.
		if (item.getItem() instanceof BlockItem) {
			poseStack.pushPose();
			try {
				poseStack.last().set(iteminspect$handPose);
				// Pre-rotate the frame renderPlayerArm will position itself
				// within, so its (vanilla-correct) resting arm pose gets
				// carried along with our tilt instead of us re-deriving it.
				poseStack.mulPose(Axis.YP.rotationDegrees(yaw * offset));
				poseStack.mulPose(Axis.XP.rotationDegrees(-pitch * offset));
				this.renderPlayerArm(poseStack, collector, light, 0.0F, 0.0F, player.getMainArm());
			} finally {
				poseStack.popPose();
			}
		}

		poseStack.pushPose();
		try {
			InspectTransform.apply(poseStack, iteminspect$handPose, player.getMainArm(),
					ItemInspectClient.CONFIG, offset, yaw, pitch);
			original.call(state, poseStack, collector, light, overlay, seed);
		} finally {
			poseStack.popPose();
		}
	}
}
