package com.ultraperformance.mixin;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.PerfMonitor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Mede quanto tempo cada tick do servidor leva (MSPT). Só observa: não muda nenhum comportamento. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"), require = 0)
	private void ultra$tickStart(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
		if (HookStatus.isFailed(HookStatus.SERVER_TICK)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.SERVER_TICK);
			PerfMonitor.serverTickStart(System.nanoTime());
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.SERVER_TICK, t);
		}
	}

	@Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"), require = 0)
	private void ultra$tickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
		if (HookStatus.isFailed(HookStatus.SERVER_TICK)) {
			return;
		}
		try {
			PerfMonitor.serverTickEnd(System.nanoTime());
			HookStatus.effect(HookStatus.SERVER_TICK);
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.SERVER_TICK, t);
		}
	}
}
