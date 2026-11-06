package com.ultraperformance.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de todas as opções do mod. Cada otimização pode ser ligada, desligada
 * e ajustada individualmente; os três últimos números de cada opção são os
 * valores dos perfis Ultra Econômico, Desempenho e Equilibrado.
 */
public final class Opt {
	// Geral
	public static final String ENABLED = "enabled";
	public static final String OVERLAY = "overlay";
	public static final String BENCH_SECONDS = "benchSeconds";
	public static final String BENCH_SPIN = "benchSpin";
	// Renderização
	public static final String ENTITY_CULL = "entityCull";
	public static final String ENTITY_DIST = "entityCullDistance";
	public static final String BE_CULL = "blockEntityCull";
	public static final String BE_DIST = "blockEntityCullDistance";
	public static final String WEATHER_OFF = "weatherOff";
	public static final String TEX_ANIM = "textureAnimation";
	// Partículas
	public static final String PARTICLE_CAP = "particleCap";
	public static final String PARTICLE_KEEP = "particleKeepPercent";
	// Mundo e CPU
	public static final String CHUNK_BUDGET = "chunkUploadBudgetMs";
	public static final String UNFOCUSED_FPS = "unfocusedFps";
	public static final String AI_THROTTLE = "aiThrottle";
	public static final String AI_DIST = "aiThrottleDistance";
	public static final String GC_DISCONNECT = "gcOnDisconnect";
	// Adaptativo
	public static final String ADAPTIVE = "adaptive";
	public static final String ADAPTIVE_TARGET = "adaptiveTargetFps";
	public static final String ADAPTIVE_MAXLEVEL = "adaptiveMaxLevel";
	public static final String ADAPTIVE_RD = "adaptiveRenderDistance";
	public static final String ADAPTIVE_MIN_RD = "adaptiveMinRenderDistance";

	private static final List<OptionDef> ALL = new ArrayList<OptionDef>();
	private static final Map<String, OptionDef> BY_ID = new LinkedHashMap<String, OptionDef>();

	private Opt() {
	}

	private static void add(OptionDef d) {
		ALL.add(d);
		BY_ID.put(d.id, d);
	}

	static {
		Category g = Category.GERAL;
		add(OptionDef.bool(ENABLED, g, "Otimizações ativas",
				"Chave geral do mod. Desligue para comparar o desempenho com e sem as otimizações "
						+ "(use junto com o benchmark da aba Sistema).",
				true, true, true).independent());
		add(OptionDef.choice(OVERLAY, g, "Overlay de desempenho",
				"Mostra na tela o FPS, o 1% low, o tempo do servidor, o uso de RAM e dados do mod. "
						+ "A tecla F6 alterna entre os modos.",
				new String[] {"Desligado", "Compacto", "Normal", "Detalhado"}, 0, 0, 0).independent());
		add(OptionDef.range(BENCH_SECONDS, g, "Duração do benchmark",
				"Tempo de coleta do benchmark. Quanto maior, mais confiável o resultado.", 30, 180, 30, " s", 60, 60,
				60).independent());
		add(OptionDef.bool(BENCH_SPIN, g, "Girar câmera no benchmark",
				"Gira a câmera devagar durante o benchmark para que cada execução tenha uma carga de "
						+ "renderização parecida. Desligue se preferir se mover manualmente.",
				true, true, true).independent());

		Category r = Category.RENDER;
		add(OptionDef.bool(ENTITY_CULL, r, "Ocultar entidades distantes",
				"Não desenha entidades (mobs, itens, molduras...) além da distância configurada. Entidades "
						+ "grandes, como o Dragão, têm um limite proporcional ao tamanho e entidades brilhando "
						+ "nunca são ocultadas.",
				true, true, true));
		add(OptionDef.range(ENTITY_DIST, r, "Distância das entidades",
				"Distância máxima de desenho das entidades. O jogo já limita sozinho as entidades pequenas "
						+ "(itens ficam em torno de 16 blocos); este valor limita as médias e grandes.",
				16, 128, 8, " blocos", 32, 48, 80));
		add(OptionDef.bool(BE_CULL, r, "Ocultar blocos animados distantes",
				"Não desenha baús, camas, placas, banners, cabeças e outros blocos com entidade além da "
						+ "distância configurada.",
				true, true, false).warn("camas e baús somem do campo de visão na distância configurada."));
		add(OptionDef.range(BE_DIST, r, "Distância dos blocos animados",
				"Distância máxima de desenho dos blocos com entidade. O padrão do jogo é 64 blocos.", 16, 64, 8,
				" blocos", 32, 48, 64));
		add(OptionDef.bool(WEATHER_OFF, r, "Desligar chuva e neve (visual)",
				"Não desenha as faixas de chuva e neve, que podem pesar em GPUs fracas (meça com o benchmark). O clima continua "
						+ "existindo: mobs, raios e plantações não mudam.",
				true, false, false));
		add(OptionDef.choice(TEX_ANIM, r, "Texturas animadas",
				"Reduz a frequência de atualização de água, lava, fogo, portais e outras texturas animadas. "
						+ "Costuma ajudar em GPUs integradas; o ganho varia, meça com o benchmark.",
				new String[] {"Normal", "Metade", "Um quarto", "Parado"}, 3, 2, 1));

		Category p = Category.PARTICULAS;
		add(OptionDef.range(PARTICLE_CAP, p, "Limite de partículas",
				"Número máximo de partículas vivas ao mesmo tempo. Novas partículas são descartadas quando o "
						+ "limite é atingido. Explosões e fazendas aceleram o jogo sem isso.",
				0, 4000, 250, " partículas", 500, 1000, 2000).zero("Sem limite"));
		add(OptionDef.range(PARTICLE_KEEP, p, "Densidade de partículas",
				"Descarta parte das partículas novas ao acaso. 100% não descarta nenhuma.", 10, 100, 10, "%", 40, 70,
				100));

		Category m = Category.MUNDO;
		add(OptionDef.range(CHUNK_BUDGET, m, "Limite de envio de chunks",
				"Limita o tempo por quadro gasto enviando chunks reconstruídos para a GPU. Reduz travadas ao "
						+ "andar rápido ou carregar o mundo; em troca, os chunks aparecem um pouco mais tarde.",
				0, 8, 1, " ms", 2, 3, 0).zero("Desligado"));
		add(OptionDef.range(UNFOCUSED_FPS, m, "FPS com a janela sem foco",
				"Limita o FPS quando a janela do jogo não está em foco (alt-tab). Economiza CPU e GPU.", 0, 60, 5,
				" FPS", 10, 15, 30).zero("Desligado"));
		add(OptionDef.choice(AI_THROTTLE, m, "IA de mobs distantes",
				"Executa a IA de mobs parados e longe de todos os jogadores com menos frequência. Não afeta "
						+ "mobs perto de você, com alvo, amarrados ou montados, e o tempo de despawn é preservado.",
				new String[] {"Desligado", "Leve (1/2)", "Médio (1/4)", "Forte (1/8)"}, 2, 1, 0)
						.warn("pode mudar o ritmo de fazendas de mobs muito longe de você."));
		add(OptionDef.range(AI_DIST, m, "Distância da IA reduzida",
				"A IA só é reduzida em mobs mais longe que isso de todos os jogadores.", 32, 128, 16, " blocos", 48,
				64, 64));
		add(OptionDef.bool(GC_DISCONNECT, m, "Limpar memória ao sair do mundo",
				"Pede uma coleta de lixo da JVM depois de sair de um mundo, podendo devolver memória ao sistema "
						+ "enquanto você está no menu (depende da JVM).",
				true, true, true));

		Category a = Category.ADAPTATIVO;
		add(OptionDef.bool(ADAPTIVE, a, "Ajuste adaptativo",
				"Monitora o FPS e aumenta ou diminui a agressividade das otimizações sozinho. Avalia a cada "
						+ "5 s, espera bastante entre mudanças e recua aos poucos para não ficar oscilando.",
				true, true, false));
		add(OptionDef.range(ADAPTIVE_TARGET, a, "FPS alvo",
				"O ajuste só reduz a qualidade quando o FPS fica abaixo disso e só volta a aumentar quando "
						+ "há folga.",
				20, 120, 5, " FPS", 30, 45, 60));
		add(OptionDef.range(ADAPTIVE_MAXLEVEL, a, "Nível máximo de redução",
				"Quantos passos de redução o ajuste pode aplicar (1 = leve, 3 = máximo).", 1, 3, 1, "", 3, 3, 2));
		add(OptionDef.bool(ADAPTIVE_RD, a, "Ajustar distância de renderização",
				"Permite que o ajuste também reduza a distância de renderização do jogo. Cada mudança "
						+ "recarrega chunks e causa uma pequena pausa. O valor original volta quando o FPS "
						+ "se recupera.",
				false, false, false));
		add(OptionDef.range(ADAPTIVE_MIN_RD, a, "Distância mínima de renderização",
				"Menor distância de renderização que o ajuste pode usar.", 4, 12, 1, " chunks", 4, 5, 6));
	}

	public static List<OptionDef> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static OptionDef get(String id) {
		return BY_ID.get(id);
	}

	public static List<OptionDef> byCategory(Category c) {
		List<OptionDef> out = new ArrayList<OptionDef>();
		for (OptionDef d : ALL) {
			if (d.category == c) {
				out.add(d);
			}
		}
		return out;
	}
}
