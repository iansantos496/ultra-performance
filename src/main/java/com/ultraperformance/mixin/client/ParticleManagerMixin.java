package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;

/**
 * Limite e densidade de partículas.
 *
 * <p>As partículas são descartadas quando entram na fila ({@code addParticle(Particle)}), e não mais cedo,
 * porque o jogo usa o resultado de {@code addParticle(ParticleEffect, ...)} (fogos de artifício, por exemplo):
 * a partícula descartada continua sendo criada e entregue a quem pediu, só não é simulada nem desenhada.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Shadow
	@Final
	private Map<?, ?> particles;

	/** Partículas vivas contadas no fim do último tick. */
	@Unique
	private int ultra$live;
	/** Partículas aceitas desde essa contagem. */
	@Unique
	private int ultra$added;

	@Inject(method = "tick()V", at = @At("RETURN"), require = 0)
	private void ultra$recount(CallbackInfo ci) {
		if (Live.particleCap <= 0 || HookStatus.isFailed(HookStatus.PARTICLES)) {
			return;
		}
		try {
			int n = 0;
			for (Object queue : this.particles.values()) {
				if (queue instanceof Collection) {
					n += ((Collection<?>) queue).size();
				}
			}
			this.ultra$live = n;
			this.ultra$added = 0;
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.PARTICLES, t);
		}
	}

	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true,
			require = 0)
	private void ultra$limit(Particle particle, CallbackInfo ci) {
		int cap = Live.particleCap;
		int keep = Live.particleKeep;
		if ((cap <= 0 && keep >= 100) || HookStatus.isFailed(HookStatus.PARTICLES)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.PARTICLES);
			if (keep < 100 && Live.nextPercent() >= keep) {
				ci.cancel();
				HookStatus.effect(HookStatus.PARTICLES);
				return;
			}
			if (cap > 0) {
				if (this.ultra$live + this.ultra$added >= cap) {
					ci.cancel();
					HookStatus.effect(HookStatus.PARTICLES);
					return;
				}
				this.ultra$added++;
			}
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.PARTICLES, t);
		}
	}
}
