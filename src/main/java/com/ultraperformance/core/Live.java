package com.ultraperformance.core;

/**
 * Valores efetivos usados nos pontos quentes (mixins). São campos estáticos simples,
 * recalculados por {@link #rebuild()} sempre que uma opção ou o nível adaptativo muda,
 * para que os mixins não precisem consultar mapas nem fazer contas a cada chamada.
 */
public final class Live {
	public static boolean master = true;

	public static boolean entityCull;
	public static double entityMaxBlocks = 64.0D;
	public static double entityNearSq = 32.0D * 32.0D;

	public static boolean blockEntityCull;
	public static double blockEntityMaxSq = 64.0D * 64.0D;

	public static boolean weatherOff;

	/** 1 = normal, 0 = parado, n >= 2 = atualiza a cada n ticks. */
	public static int animInterval = 1;

	public static long uploadBudgetNs;
	public static int unfocusedFps;

	/** 0 = sem limite. */
	public static int particleCap;
	public static int particleKeep = 100;

	public static boolean gcOnDisconnect;

	/** 1 = IA normal; n >= 2 = IA de mobs distantes roda a cada n ticks. */
	public static volatile int aiInterval = 1;
	public static volatile double aiFarSq = 64.0D * 64.0D;

	/** Nível do ajuste adaptativo (0 = sem redução). Definido pelo cliente. */
	public static int adaptiveLevel;

	static final double[] DIST_SCALE = {1.0D, 0.80D, 0.62D, 0.48D};
	static final double[] PARTICLE_CAP_SCALE = {1.0D, 0.70D, 0.45D, 0.25D};
	static final int[] PARTICLE_KEEP_PCT = {100, 100, 80, 60};
	static final int[] ANIM_INTERVAL = {1, 2, 4, 0};
	static final int[] AI_INTERVAL = {1, 2, 4, 8};

	private static int rng = 0x2545F491;

	private Live() {
	}

	/** Número pseudoaleatório de 0 a 99 (xorshift; chamado só pela thread de renderização). */
	public static int nextPercent() {
		int x = rng;
		x ^= x << 13;
		x ^= x >>> 17;
		x ^= x << 5;
		rng = x;
		return (int) ((x & 0x7fffffffL) % 100L);
	}

	public static void rebuild() {
		boolean m = Settings.bool(Opt.ENABLED);
		master = m;

		boolean adaptiveOn = m && Settings.bool(Opt.ADAPTIVE);
		int lvl = adaptiveOn ? Math.max(0, Math.min(adaptiveLevel, 3)) : 0;
		double ds = DIST_SCALE[lvl];

		boolean ec = Settings.bool(Opt.ENTITY_CULL);
		entityCull = m && (ec || lvl >= 1);
		double eBase = ec ? Settings.get(Opt.ENTITY_DIST) : 80.0D;
		entityMaxBlocks = Math.max(12.0D, eBase * ds);
		double near = entityMaxBlocks * 0.5D;
		entityNearSq = near * near;

		boolean bc = Settings.bool(Opt.BE_CULL);
		blockEntityCull = m && (bc || lvl >= 2);
		double bBase = bc ? Settings.get(Opt.BE_DIST) : 64.0D;
		double bMax = Math.max(12.0D, bBase * ds);
		blockEntityMaxSq = bMax * bMax;

		weatherOff = m && (Settings.bool(Opt.WEATHER_OFF) || lvl >= 3);

		int mode = Math.min(3, Settings.get(Opt.TEX_ANIM) + (lvl + 1) / 2);
		animInterval = m ? ANIM_INTERVAL[mode] : 1;

		uploadBudgetNs = m ? Settings.get(Opt.CHUNK_BUDGET) * 1000000L : 0L;
		unfocusedFps = m ? Settings.get(Opt.UNFOCUSED_FPS) : 0;

		int cap = Settings.get(Opt.PARTICLE_CAP);
		if (cap == 0 && lvl >= 1) {
			cap = 3000;
		}
		if (cap > 0) {
			cap = Math.max(100, (int) (cap * PARTICLE_CAP_SCALE[lvl]));
		}
		particleCap = m ? cap : 0;
		int keep = Settings.get(Opt.PARTICLE_KEEP) * PARTICLE_KEEP_PCT[lvl] / 100;
		particleKeep = m ? Math.max(10, Math.min(100, keep)) : 100;

		gcOnDisconnect = m && Settings.bool(Opt.GC_DISCONNECT);

		aiInterval = m ? AI_INTERVAL[Settings.get(Opt.AI_THROTTLE)] : 1;
		double ad = Settings.get(Opt.AI_DIST);
		aiFarSq = ad * ad;
	}
}
