package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Não desenha entidades além da distância configurada.
 *
 * <p>O limite é proporcional ao tamanho da entidade (média de largura, largura e altura; mínimo 1), então
 * entidades grandes como Ghasts e o Dragão têm um limite maior. Entidades brilhando e jogadores nunca são
 * ocultados. O jogo original já tem um limite próprio por tamanho (64 blocos x tamanho x o ajuste
 * "Distância das entidades" do vídeo); este gancho só pode diminuir esse alcance, nunca aumentar.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void ultra$cull(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
			CallbackInfoReturnable<Boolean> cir) {
		if (!Live.entityCull || HookStatus.isFailed(HookStatus.ENTITY_CULL)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.ENTITY_CULL);
			double dx = entity.getX() - cameraX;
			double dy = entity.getY() - cameraY;
			double dz = entity.getZ() - cameraZ;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < Live.entityNearSq) {
				return; // perto: nunca oculta
			}
			if (entity instanceof PlayerEntity || entity.isGlowing()) {
				return;
			}
			double size = (entity.getWidth() * 2.0D + entity.getHeight()) / 3.0D;
			double limit = Live.entityMaxBlocks * Math.max(1.0D, size);
			if (distSq > limit * limit) {
				cir.setReturnValue(Boolean.FALSE);
				HookStatus.effect(HookStatus.ENTITY_CULL);
			}
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.ENTITY_CULL, t);
		}
	}
}
