package com.ultraperformance.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnóstico dos ganchos (mixins). Cada gancho registra quantas vezes executou e
 * quantas vezes realmente alterou o comportamento do jogo. Se um gancho lançar uma
 * exceção, ele é desligado sozinho e o motivo aparece na aba Sistema e no log.
 * Assim, um mixin que não encaixou na versão do jogo fica visível em vez de silencioso.
 */
public final class HookStatus {
	public static final int ENTITY_CULL = 0;
	public static final int BLOCK_ENTITY_CULL = 1;
	public static final int PARTICLES = 2;
	public static final int TEX_ANIM = 3;
	public static final int WEATHER = 4;
	public static final int CHUNK_UPLOAD = 5;
	public static final int UNFOCUSED_FPS = 6;
	public static final int GC_DISCONNECT = 7;
	public static final int AI_THROTTLE = 8;
	public static final int SERVER_TICK = 9;
	public static final int FRAME_TIMER = 10;
	public static final int CHUNK_PACKET = 11;
	public static final int OPTIONS_BUTTON = 12;
	public static final int COUNT = 13;

	private static final String[] NAMES = {"Ocultar entidades distantes", "Ocultar blocos animados distantes",
			"Limite de partículas", "Texturas animadas", "Chuva e neve", "Limite de envio de chunks",
			"FPS com a janela sem foco", "Limpeza de memória ao sair", "IA de mobs distantes",
			"Medidor de tick do servidor", "Medidor de quadros", "Contador de chunks", "Botão nas Opções"};

	private static final String[] SIDES = {"cliente", "cliente", "cliente", "cliente", "cliente", "cliente",
			"cliente", "cliente", "servidor", "servidor", "cliente", "cliente", "cliente"};

	private static final String[] EFFECTS = {"entidades ocultas", "blocos ocultos", "partículas descartadas",
			"ticks de animação pulados", "quadros sem chuva", "envios adiados", "quadros limitados",
			"limpezas pedidas", "ticks de IA pulados", "ticks medidos", "quadros medidos", "chunks recebidos",
			"botões criados"};

	private static final long[] HITS = new long[COUNT];
	private static final long[] EFFECT_COUNT = new long[COUNT];
	private static final boolean[] FAILED = new boolean[COUNT];
	private static final String[] FAIL_MSG = new String[COUNT];

	private HookStatus() {
	}

	public static void hit(int id) {
		HITS[id]++;
	}

	public static void effect(int id) {
		EFFECT_COUNT[id]++;
	}

	public static void effect(int id, long n) {
		EFFECT_COUNT[id] += n;
	}

	public static boolean isFailed(int id) {
		return FAILED[id];
	}

	/** Desliga o gancho e guarda o motivo. */
	public static void fail(int id, Throwable t) {
		if (!FAILED[id]) {
			FAILED[id] = true;
			FAIL_MSG[id] = t.getClass().getSimpleName() + ": " + t.getMessage();
			Log.warn("Gancho '" + NAMES[id] + "' falhou e foi desativado: " + FAIL_MSG[id]);
		}
	}

	public static long hits(int id) {
		return HITS[id];
	}

	public static long effects(int id) {
		return EFFECT_COUNT[id];
	}

	public static long[] snapshotEffects() {
		long[] copy = new long[COUNT];
		System.arraycopy(EFFECT_COUNT, 0, copy, 0, COUNT);
		return copy;
	}

	public static String name(int id) {
		return NAMES[id];
	}

	public static String effectName(int id) {
		return EFFECTS[id];
	}

	public static void reset() {
		for (int i = 0; i < COUNT; i++) {
			HITS[i] = 0L;
			EFFECT_COUNT[i] = 0L;
			FAILED[i] = false;
			FAIL_MSG[i] = null;
		}
	}

	public static String line(int id) {
		String s = NAMES[id] + " (" + SIDES[id] + "): ";
		if (FAILED[id]) {
			return s + "FALHOU e foi desativado - " + FAIL_MSG[id];
		}
		if (HITS[id] == 0L) {
			return s + "ainda não executou";
		}
		return s + "ativo, " + HITS[id] + " execuções, " + EFFECT_COUNT[id] + " " + EFFECTS[id];
	}

	public static List<String> lines() {
		List<String> out = new ArrayList<String>();
		for (int i = 0; i < COUNT; i++) {
			out.add(line(i));
		}
		return out;
	}
}
