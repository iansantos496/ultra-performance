package com.ultraperformance.mixin.client;

import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.HookStatus;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conta os pacotes de chunk recebidos (para o benchmark). Fica no fim do método: na thread de rede o jogo
 * interrompe o método antes de chegar lá e o refaz na thread principal, então cada pacote é contado uma vez.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onChunkData(Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;)V", at = @At("RETURN"),
			require = 0)
	private void ultra$countChunk(ChunkDataS2CPacket packet, CallbackInfo ci) {
		if (HookStatus.isFailed(HookStatus.CHUNK_PACKET)) {
			return;
		}
		HookStatus.hit(HookStatus.CHUNK_PACKET);
		HookStatus.effect(HookStatus.CHUNK_PACKET);
		Benchmark.onChunkPacket();
	}
}
