package com.ultraperformance.mixin.client;

import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

/**
 * Limita o tempo por quadro gasto enviando geometria de chunks para a GPU. Os envios que sobram ficam na fila
 * para o quadro seguinte; pelo menos um envio é feito por chamada, então a fila sempre anda.
 *
 * <p>Esta rotina repete o laço simples do jogo original (tirar da fila e rodar até esvaziar), acrescentando o
 * orçamento de tempo. Erros dentro dos envios não são capturados: continuam subindo como no jogo original.
 */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderMixin {
	@Shadow
	@Final
	private Queue<Runnable> uploadQueue;

	@Inject(method = "upload()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void ultra$budgetedUpload(CallbackInfoReturnable<Boolean> cir) {
		long budget = Live.uploadBudgetNs;
		if (budget <= 0L || HookStatus.isFailed(HookStatus.CHUNK_UPLOAD)) {
			return;
		}
		HookStatus.hit(HookStatus.CHUNK_UPLOAD);
		Queue<Runnable> queue = this.uploadQueue;
		long start = System.nanoTime();
		boolean any = false;
		Runnable task;
		while ((task = queue.poll()) != null) {
			task.run();
			any = true;
			if (System.nanoTime() - start >= budget) {
				if (!queue.isEmpty()) {
					HookStatus.effect(HookStatus.CHUNK_UPLOAD);
				}
				break;
			}
		}
		cir.setReturnValue(any);
	}
}
