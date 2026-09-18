package dev.arielg.iteminspect.testmixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arielg.iteminspect.InspectSmokeTest;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class ItemSubmitProbe {
	@Shadow
	ItemDisplayContext displayContext;

	@Inject(method = "submit", at = @At("HEAD"))
	private void capture(PoseStack poses, SubmitNodeCollector collector, int light, int overlay, int seed, CallbackInfo ci) {
		InspectSmokeTest.capture(displayContext, poses);
	}
}
