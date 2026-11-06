package com.ultraperformance.client;

import com.ultraperformance.core.Profile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AoMode;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajusta as opções de vídeo do próprio jogo para os valores de um perfil. Só acontece quando o jogador
 * clica no botão, e só REDUZ a qualidade: nunca aumenta nada que o jogador já tenha deixado mais baixo.
 */
public final class VideoSettings {
	private static final int[] MAX_VIEW = {6, 8, 10};
	private static final int[] MAX_GRAPHICS = {0, 0, 1}; // 0 Rápido, 1 Detalhado
	private static final int[] MAX_CLOUDS = {0, 0, 1}; // 0 Desligadas, 1 Rápidas
	private static final int[] MIN_PARTICLES = {2, 1, 1}; // 1 Reduzidas, 2 Mínimas
	private static final int[] MAX_AO = {0, 0, 1}; // 0 Desligada, 1 Mínima
	private static final int[] MAX_BLEND = {0, 1, 2};
	private static final boolean[] SHADOWS = {false, false, true};
	private static final float[] MAX_ENTITY_SCALE = {0.5F, 0.75F, 1.0F};

	private VideoSettings() {
	}

	/** Devolve a lista do que mudou (vazia se já estava tudo igual ou melhor). */
	public static List<String> apply(MinecraftClient client, Profile profile) {
		int i = profile.isPreset() ? profile.ordinal() : Profile.EQUILIBRADO.ordinal();
		GameOptions o = client.options;
		List<String> changes = new ArrayList<String>();

		if (o.viewDistance > MAX_VIEW[i]) {
			changes.add("distância de renderização " + o.viewDistance + " -> " + MAX_VIEW[i]);
			o.viewDistance = MAX_VIEW[i];
		}
		if (o.graphicsMode.getId() > MAX_GRAPHICS[i]) {
			o.graphicsMode = GraphicsMode.byId(MAX_GRAPHICS[i]);
			changes.add("gráficos para Rápido");
		}
		if (o.cloudRenderMode.getId() > MAX_CLOUDS[i]) {
			o.cloudRenderMode = CloudRenderMode.byId(MAX_CLOUDS[i]);
			changes.add("nuvens reduzidas");
		}
		if (o.particles.getId() < MIN_PARTICLES[i]) {
			o.particles = ParticlesMode.byId(MIN_PARTICLES[i]);
			changes.add("partículas reduzidas");
		}
		if (o.ao.getId() > MAX_AO[i]) {
			o.ao = AoMode.byId(MAX_AO[i]);
			changes.add("iluminação suave reduzida");
		}
		if (o.biomeBlendRadius > MAX_BLEND[i]) {
			o.biomeBlendRadius = MAX_BLEND[i];
			changes.add("mistura de biomas " + MAX_BLEND[i]);
		}
		if (o.entityShadows && !SHADOWS[i]) {
			o.entityShadows = false;
			changes.add("sombras de entidades desligadas");
		}
		if (o.entityDistanceScaling > MAX_ENTITY_SCALE[i]) {
			o.entityDistanceScaling = MAX_ENTITY_SCALE[i];
			changes.add("distância das entidades " + (int) (MAX_ENTITY_SCALE[i] * 100) + "%");
		}
		if (!changes.isEmpty()) {
			o.write();
			client.worldRenderer.reload();
		}
		return changes;
	}
}
