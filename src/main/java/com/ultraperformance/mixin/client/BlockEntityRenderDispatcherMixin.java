package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Não desenha blocos com entidade (baús, camas, placas, banners, cabeças...) além da distância configurada.
 * Blocos que já têm um alcance de desenho maior que o padrão do jogo (como o feixe do sinalizador)
 * são deixados em paz.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Shadow
	public Camera camera;

	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void ultra$cull(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (!Live.blockEntityCull || HookStatus.isFailed(HookStatus.BLOCK_ENTITY_CULL)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.BLOCK_ENTITY_CULL);
			Camera cam = this.camera;
			if (cam == null || blockEntity.getRenderDistance() > 4096.0D) {
				return;
			}
			BlockPos pos = blockEntity.getPos();
			Vec3d c = cam.getPos();
			double dx = pos.getX() + 0.5D - c.x;
			double dy = pos.getY() + 0.5D - c.y;
			double dz = pos.getZ() + 0.5D - c.z;
			if (dx * dx + dy * dy + dz * dz > Live.blockEntityMaxSq) {
				ci.cancel();
				HookStatus.effect(HookStatus.BLOCK_ENTITY_CULL);
			}
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.BLOCK_ENTITY_CULL, t);
		}
	}
}
