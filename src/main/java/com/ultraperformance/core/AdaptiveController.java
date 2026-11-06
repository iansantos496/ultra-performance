package com.ultraperformance.core;

import java.util.Locale;

/**
 * Decide, a cada avaliação, se o nível de redução do ajuste adaptativo sobe, desce
 * ou fica igual. Não depende do Minecraft: recebe estatísticas de FPS e um relógio
 * monotônico em milissegundos, o que permite testar o comportamento sem abrir o jogo.
 *
 * <p>Regras para não ficar oscilando:
 * <ol>
 * <li>Faixas separadas (histerese): reduz quando o FPS médio cai abaixo de 90% do alvo
 * (ou o 1% low fica muito baixo) e só recua quando há folga de 10% ou mais acima do alvo.
 * Entre as duas faixas, não faz nada.</li>
 * <li>No máximo um nível por vez: reduz no máximo a cada 15 s e recua no máximo a cada 30 s.</li>
 * <li>Para recuar é preciso uma sequência de avaliações boas (6 por padrão = 30 s). Se logo
 * depois de um recuo (menos de 2 min) for preciso reduzir de novo, a espera para o próximo
 * recuo triplica, até 9 min. Depois de 10 min sem mudança ela volta ao valor inicial.</li>
 * <li>Nenhuma decisão nos 20 s depois de entrar num mundo, nem com a janela sem foco
 * (o chamador usa {@link #holdOff}), nem com poucos quadros medidos.</li>
 * </ol>
 */
public final class AdaptiveController {
	/** Intervalo entre avaliações. */
	public static final long EVAL_MS = 5000L;
	/** Janela de quadros que o chamador deve usar nas estatísticas. */
	public static final long WINDOW_MS = 10000L;
	public static final long WARMUP_MS = 20000L;
	public static final long TIGHTEN_COOLDOWN_MS = 15000L;
	public static final long RELAX_COOLDOWN_MS = 30000L;
	public static final long PREMATURE_RELAX_MS = 120000L;
	public static final long STABLE_RESET_MS = 600000L;

	public static final int BASE_UP_WAIT = 6;
	public static final int MAX_UP_WAIT = 108;
	public static final int MIN_FRAMES = 30;
	public static final int MAX_LEVEL = 3;

	/** Abaixo desta fração do alvo o FPS médio é considerado ruim. */
	public static final double BAD_RATIO = 0.90D;
	/** Abaixo do alvo, o 1% low abaixo desta fração dele também é considerado ruim. */
	public static final double BAD_LOW1_RATIO = 0.40D;
	/** Para recuar, o FPS médio precisa estar acima desta fração do alvo. */
	public static final double GOOD_RATIO = 1.10D;
	public static final double GOOD_LOW1_RATIO = 0.60D;

	private int level;
	private long lastChangeMs;
	private long holdUntilMs;
	private long lastEvalMs;
	private long lastRelaxMs;
	private boolean everRelaxed;
	private int goodStreak;
	private int upWait = BASE_UP_WAIT;
	private int changes;
	private boolean saturated;
	private String reason = "";

	public AdaptiveController() {
		reset(0L);
	}

	/** Volta ao nível 0 e inicia o período de aquecimento (entrada num mundo). */
	public void reset(long nowMs) {
		level = 0;
		lastChangeMs = nowMs;
		holdUntilMs = nowMs + WARMUP_MS;
		lastEvalMs = nowMs;
		lastRelaxMs = nowMs;
		everRelaxed = false;
		goodStreak = 0;
		upWait = BASE_UP_WAIT;
		saturated = false;
		reason = "";
	}

	/** Força um nível (por exemplo, 0 ao desligar o ajuste). */
	public void setLevel(int newLevel, long nowMs) {
		int l = Math.max(0, Math.min(MAX_LEVEL, newLevel));
		if (l != level) {
			level = l;
			lastChangeMs = nowMs;
			goodStreak = 0;
		}
	}

	/** Suspende decisões até {@code nowMs + ms} (janela sem foco, benchmark, telas pesadas). */
	public void holdOff(long nowMs, long ms) {
		long until = nowMs + ms;
		if (until > holdUntilMs) {
			holdUntilMs = until;
		}
		goodStreak = 0;
	}

	public boolean due(long nowMs) {
		return nowMs - lastEvalMs >= EVAL_MS;
	}

	public int level() {
		return level;
	}

	/** Quantas mudanças de nível já aconteceram desde que o controle foi criado. */
	public int changes() {
		return changes;
	}

	/** Verdadeiro se a última avaliação foi ruim mesmo no nível máximo permitido. */
	public boolean saturated() {
		return saturated;
	}

	public int upWait() {
		return upWait;
	}

	/** Motivo da última mudança de nível, em texto. */
	public String reason() {
		return reason;
	}

	/**
	 * Avalia e devolve +1 (reduziu mais um nível), -1 (recuou um nível) ou 0 (manteve).
	 *
	 * @param nowMs   relógio monotônico em milissegundos
	 * @param avgFps  FPS médio da janela
	 * @param low1Fps 1% low da janela
	 * @param frames  quadros na janela
	 * @param targetFps FPS alvo
	 * @param maxLevel nível máximo permitido (1 a 3)
	 */
	public int evaluate(long nowMs, double avgFps, double low1Fps, int frames, int targetFps, int maxLevel) {
		lastEvalMs = nowMs;
		int cap = Math.max(0, Math.min(MAX_LEVEL, maxLevel));
		if (level > cap) {
			level = cap;
			lastChangeMs = nowMs;
			goodStreak = 0;
			changes++;
			reason = "limite máximo reduzido para " + cap;
			return -1;
		}
		if (targetFps <= 0 || nowMs < holdUntilMs || frames < MIN_FRAMES) {
			return 0;
		}
		if (nowMs - lastChangeMs < WINDOW_MS) {
			return 0; // a janela ainda inclui quadros de antes da última mudança
		}
		if (upWait > BASE_UP_WAIT && nowMs - lastChangeMs >= STABLE_RESET_MS) {
			upWait = BASE_UP_WAIT;
		}

		double t = targetFps;
		boolean bad = avgFps < BAD_RATIO * t || (avgFps < t && low1Fps < BAD_LOW1_RATIO * t);
		boolean good = avgFps >= GOOD_RATIO * t && low1Fps >= GOOD_LOW1_RATIO * t;
		saturated = bad && level >= cap;

		if (bad) {
			goodStreak = 0;
			if (level < cap && nowMs - lastChangeMs >= TIGHTEN_COOLDOWN_MS) {
				level++;
				if (everRelaxed && nowMs - lastRelaxMs < PREMATURE_RELAX_MS) {
					upWait = Math.min(upWait * 3, MAX_UP_WAIT);
				}
				lastChangeMs = nowMs;
				changes++;
				reason = String.format(Locale.ROOT, "FPS médio %.0f (1%% low %.0f) abaixo do alvo %d: reduzindo para o nível %d",
						avgFps, low1Fps, targetFps, level);
				return 1;
			}
			return 0;
		}
		if (good && level > 0) {
			goodStreak++;
			if (goodStreak >= upWait && nowMs - lastChangeMs >= RELAX_COOLDOWN_MS) {
				level--;
				everRelaxed = true;
				lastRelaxMs = nowMs;
				lastChangeMs = nowMs;
				goodStreak = 0;
				changes++;
				reason = String.format(Locale.ROOT, "FPS médio %.0f com folga sobre o alvo %d: voltando para o nível %d",
						avgFps, targetFps, level);
				return -1;
			}
			return 0;
		}
		goodStreak = 0; // faixa neutra: mantém o nível e recomeça a contagem
		return 0;
	}
}
