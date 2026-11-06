package com.ultraperformance.core;

/**
 * Perfis de desempenho. A ordem dos três primeiros é usada como índice nas
 * tabelas de valores padrão (ordinal 0, 1, 2).
 */
public enum Profile {
	ULTRA_ECONOMICO("Ultra Econômico",
			"Prioridade absoluta para FPS em hardware muito fraco: distâncias curtas, poucas partículas, "
					+ "texturas animadas paradas e ajuste adaptativo ligado."),
	DESEMPENHO("Desempenho",
			"Prioriza FPS e corta os efeitos que mais custam processamento, mantendo o jogo agradável de olhar."),
	EQUILIBRADO("Equilibrado",
			"Combina desempenho e qualidade visual: corta só os desperdícios mais evidentes."),
	PERSONALIZADO("Personalizado",
			"Você controla cada otimização individualmente. Passa a valer sozinho quando você altera "
					+ "qualquer opção de um perfil.");

	public final String label;
	public final String description;

	Profile(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public boolean isPreset() {
		return this != PERSONALIZADO;
	}

	public static Profile parse(String name, Profile fallback) {
		if (name != null) {
			for (Profile p : values()) {
				if (p.name().equalsIgnoreCase(name.trim())) {
					return p;
				}
			}
		}
		return fallback;
	}
}
