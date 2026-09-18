package dev.arielg.iteminspect.testmixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arielg.iteminspect.InspectSmokeTest;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemRenderProbe {
	@Inject(method = "renderItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void captureInput(LivingEntity entity, ItemStack item, ItemDisplayContext context, PoseStack poses,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		InspectSmokeTest.captureInput(context, poses);
	}
}
