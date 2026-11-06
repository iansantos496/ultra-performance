package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import com.ultraperformance.core.PerfMonitor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Três ganchos no cliente: medidor de quadros, limite de FPS com a janela sem foco e coleta de lixo
 * pedida ao sair de um mundo.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	/** Início de cada quadro: alimenta o FPS, o 1% low, o overlay, o benchmark e o ajuste adaptativo. */
	@Inject(method = "render(Z)V", at = @At("HEAD"), require = 0)
	private void ultra$frameStart(boolean tick, CallbackInfo ci) {
		if (HookStatus.isFailed(HookStatus.FRAME_TIMER)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.FRAME_TIMER);
			PerfMonitor.frameStart(System.nanoTime());
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.FRAME_TIMER, t);
		}
	}

	/**
	 * O jogo usa este valor para limitar o FPS. Com a janela sem foco, devolve o menor entre o limite
	 * normal e o configurado.
	 */
	@Inject(method = "getFramerateLimit()I", at = @At("RETURN"), cancellable = true, require = 0)
	private void ultra$unfocusedLimit(CallbackInfoReturnable<Integer> cir) {
		int cap = Live.unfocusedFps;
		if (cap <= 0 || HookStatus.isFailed(HookStatus.UNFOCUSED_FPS)) {
			return;
		}
		try {
			if (!((MinecraftClient) (Object) this).isWindowFocused()) {
				HookStatus.hit(HookStatus.UNFOCUSED_FPS);
				int current = cir.getReturnValue();
				if (current > cap) {
					cir.setReturnValue(cap);
					HookStatus.effect(HookStatus.UNFOCUSED_FPS);
				}
			}
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.UNFOCUSED_FPS, t);
		}
	}

	/** Depois de sair do mundo, pede uma coleta de lixo enquanto o jogador está no menu. */
	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"), require = 0)
	private void ultra$gcAfterDisconnect(Screen screen, CallbackInfo ci) {
		if (!Live.gcOnDisconnect || HookStatus.isFailed(HookStatus.GC_DISCONNECT)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.GC_DISCONNECT);
			System.gc();
			HookStatus.effect(HookStatus.GC_DISCONNECT);
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.GC_DISCONNECT, t);
		}
	}
}
