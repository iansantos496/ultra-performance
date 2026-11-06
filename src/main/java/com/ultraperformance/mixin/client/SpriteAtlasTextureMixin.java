package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reduz a frequência de atualização das texturas animadas (água, lava, fogo, portais...). Com "Parado",
 * elas ficam no primeiro quadro da animação; as demais texturas não são afetadas.
 */
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
	@Unique
	private int ultra$calls;

	@Inject(method = "tickAnimatedSprites()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void ultra$throttle(CallbackInfo ci) {
		int interval = Live.animInterval;
		if (interval == 1 || HookStatus.isFailed(HookStatus.TEX_ANIM)) {
			return;
		}
		HookStatus.hit(HookStatus.TEX_ANIM);
		if (interval == 0 || ++this.ultra$calls % interval != 0) {
			ci.cancel();
			HookStatus.effect(HookStatus.TEX_ANIM);
		}
	}
}
