package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Não desenha as faixas de chuva e neve. O clima em si não muda (mobs, raios, plantações e o som da chuva
 * continuam iguais); só o desenho é pulado.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void ultra$skipWeather(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY,
			double cameraZ, CallbackInfo ci) {
		if (!Live.weatherOff || HookStatus.isFailed(HookStatus.WEATHER)) {
			return;
		}
		HookStatus.hit(HookStatus.WEATHER);
		HookStatus.effect(HookStatus.WEATHER);
		ci.cancel();
	}
}
