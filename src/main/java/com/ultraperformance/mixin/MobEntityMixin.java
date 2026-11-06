package com.ultraperformance.mixin;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * IA reduzida para mobs parados e longe de todos os jogadores (opção "IA de mobs distantes").
 *
 * <p>O que NÃO é afetado: mobs perto de qualquer jogador (incluindo espectadores), com alvo, amarrados,
 * montados, carregando passageiros, machucados, na água, na lava ou pegando fogo. O contador de despawn
 * continua avançando exatamente como no jogo original (o primeiro comando do método original é
 * {@code ++despawnCounter}, repetido aqui nos ticks pulados).
 *
 * <p>Cada mob roda a IA completa em 1 a cada N ticks, com um deslocamento próprio (idade + id da entidade)
 * para que os mobs não rodem todos no mesmo tick. Só atua no lado do servidor.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntity {
	/** Idade da entidade em que a distância aos jogadores será verificada de novo. */
	@Unique
	private int ultra$nextFarCheck;
	@Unique
	private boolean ultra$far;

	protected MobEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "tickNewAi()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void ultra$throttleAi(CallbackInfo ci) {
		int interval = Live.aiInterval;
		if (interval <= 1 || this.world.isClient || HookStatus.isFailed(HookStatus.AI_THROTTLE)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.AI_THROTTLE);
			if (Math.floorMod(this.age + this.getEntityId(), interval) == 0) {
				return; // neste tick a IA roda normalmente
			}
			MobEntity self = (MobEntity) (Object) this;
			if (self.getTarget() != null || self.isLeashed() || this.hasVehicle() || this.hasPassengers()
					|| this.hurtTime > 0 || this.isTouchingWater() || this.isInLava() || this.isOnFire()) {
				return;
			}
			if (this.age >= this.ultra$nextFarCheck) {
				this.ultra$far = this.ultra$farFromAllPlayers(Live.aiFarSq);
				this.ultra$nextFarCheck = this.age + 10;
			}
			if (!this.ultra$far) {
				return;
			}
			this.despawnCounter++; // o método original começa com isto
			HookStatus.effect(HookStatus.AI_THROTTLE);
			ci.cancel();
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.AI_THROTTLE, t);
		}
	}

	@Unique
	private boolean ultra$farFromAllPlayers(double farSq) {
		List<? extends PlayerEntity> players = this.world.getPlayers();
		if (players.isEmpty()) {
			return false; // sem jogadores neste mundo: por segurança não mexe
		}
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).squaredDistanceTo(this) <= farSq) {
				return false;
			}
		}
		return true;
	}
}
