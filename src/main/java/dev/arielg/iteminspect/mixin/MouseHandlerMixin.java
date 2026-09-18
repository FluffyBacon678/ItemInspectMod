package dev.arielg.iteminspect.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.arielg.iteminspect.ItemInspectClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	// turnPlayer() computes the final sensitivity/smoothing-adjusted yaw/pitch
	// delta and hands it straight to LocalPlayer.turn(d, e). Wrapping that call
	// (rather than a plain @Redirect) matters: other mods touch this exact same
	// call site too (e.g. Do a Barrel Roll, for its mouse-curving roll effect),
	// and a plain @Redirect can only ever be claimed by one mod - a second
	// @Redirect on the same instruction is a hard Mixin-apply crash.
	// @WrapOperation (MixinExtras) is composable: every mod wrapping this call
	// nests around the others instead of fighting over it.
	@WrapOperation(
			method = "turnPlayer",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
	)
	private void iteminspect$wrapTurn(LocalPlayer player, double d, double e, Operation<Void> original) {
		if (ItemInspectClient.isInspecting()) {
			// Swallow it entirely - no camera movement of any kind while
			// inspecting, so nothing downstream (e.g. barrel-roll curving)
			// gets a turn to act on either.
			ItemInspectClient.accumulateTilt(d, e);
		} else {
			original.call(player, d, e);
		}
	}
}
