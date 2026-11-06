package com.ultraperformance.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Benchmark de duração fixa. Coleta tempos de quadro, ticks do servidor local, CPU,
 * RAM, coletas de lixo e chunks recebidos; ao terminar grava um relatório de texto
 * e compara com o teste anterior. É assim que o mod mostra números reais em vez de
 * promessas: rode uma vez com as otimizações ativas e outra com elas desligadas,
 * no mesmo lugar do mesmo mundo, e compare.
 */
public final class Benchmark {
	private static final int MAX_FRAMES = 1000000;
	private static final int MIN_VALID_FRAMES = 30;
	private static final long NOISE_MARGIN_PCT = 5L;
	private static final Object LOCK = new Object();

	private static volatile boolean running;
	private static Path reportDir;
	private static Result last;
	private static String lastAbort = "";

	// configuração da execução atual
	private static long startNs;
	private static long durationNs;
	private static int seconds;
	private static int renderDistance;
	private static String context = "";
	private static String startedAtText = "";
	private static long startedAtMs;
	private static boolean modEnabled;
	private static String profileLabel = "";
	private static int adaptiveLevel;
	private static long gcCount0;
	private static long gcTime0;
	private static long[] effects0 = new long[HookStatus.COUNT];

	// dados coletados
	private static long[] frames = new long[16384];
	private static int frameCount;
	private static float[] ticks = new float[4096];
	private static int tickCount;
	private static double[] cpuSamples = new double[256];
	private static long[] heapSamples = new long[256];
	private static int sampleCount;
	private static int chunkPackets;

	private Benchmark() {
	}

	/** Resultado de uma execução. */
	public static final class Result {
		public boolean valid;
		public String startedAt = "";
		public int seconds;
		public double actualSeconds;
		public boolean modEnabled;
		public String profile = "";
		public int adaptiveLevel;
		public int renderDistance;
		public String context = "";
		public PerfMonitor.FrameStats frame = new PerfMonitor.FrameStats();
		public int serverTicks;
		public double msptAvg = -1.0D;
		public double msptMax = -1.0D;
		public int slowTicks;
		public double cpuAvg = -1.0D;
		public double cpuMax = -1.0D;
		public long heapAvgMb;
		public long heapMaxMb;
		public long gcCount;
		public long gcTimeMs;
		public int chunkPackets;
		public long[] effects = new long[HookStatus.COUNT];
		public List<String> comparison = new ArrayList<String>();
		public String report = "";
		public String reportFile = "";

		/** Linhas curtas para mostrar no jogo. */
		public List<String> summary() {
			List<String> out = new ArrayList<String>();
			if (!valid) {
				out.add("Benchmark sem amostra suficiente (" + frame.frames + " quadros).");
				return out;
			}
			out.add("Benchmark de " + fmt(actualSeconds, 0) + " s (otimizações " + (modEnabled ? "ATIVAS" : "DESLIGADAS")
					+ ", perfil " + profile + ", distância " + renderDistance + ")");
			out.add("FPS médio " + fmt(frame.avgFps, 1) + " | 1% low " + fmt(frame.low1Fps, 1) + " | 0,1% low "
					+ fmt(frame.low01Fps, 1) + " | travadas " + frame.stutters);
			StringBuilder sb = new StringBuilder();
			sb.append("Quadro: mediana ").append(fmt(frame.medianMs, 1)).append(" ms, p99 ").append(fmt(frame.p99Ms, 1))
					.append(" ms, máx ").append(fmt(frame.maxMs, 0)).append(" ms");
			out.add(sb.toString());
			StringBuilder sys = new StringBuilder();
			if (msptAvg >= 0.0D) {
				sys.append("Tick do servidor ").append(fmt(msptAvg, 1)).append(" ms | ");
			}
			if (cpuAvg >= 0.0D) {
				sys.append("CPU ").append(fmt(cpuAvg * 100.0D, 0)).append("% | ");
			}
			sys.append("RAM ").append(heapAvgMb).append(" MB | GC ").append(gcCount).append(" (").append(gcTimeMs)
					.append(" ms)");
			out.add(sys.toString());
			out.addAll(comparison);
			if (!reportFile.isEmpty()) {
				out.add("Relatório salvo em: " + reportFile);
			}
			return out;
		}

		Properties toProperties() {
			Properties p = new Properties();
			p.setProperty("startedAt", startedAt);
			p.setProperty("seconds", Integer.toString(seconds));
			p.setProperty("modEnabled", Boolean.toString(modEnabled));
			p.setProperty("profile", profile);
			p.setProperty("adaptiveLevel", Integer.toString(adaptiveLevel));
			p.setProperty("renderDistance", Integer.toString(renderDistance));
			p.setProperty("frames", Integer.toString(frame.frames));
			p.setProperty("avgFps", fmt(frame.avgFps, 3));
			p.setProperty("low1Fps", fmt(frame.low1Fps, 3));
			p.setProperty("low01Fps", fmt(frame.low01Fps, 3));
			p.setProperty("p99Ms", fmt(frame.p99Ms, 3));
			p.setProperty("stutters", Integer.toString(frame.stutters));
			p.setProperty("msptAvg", fmt(msptAvg, 3));
			p.setProperty("cpuAvg", fmt(cpuAvg, 4));
			p.setProperty("heapAvgMb", Long.toString(heapAvgMb));
			p.setProperty("gcTimeMs", Long.toString(gcTimeMs));
			return p;
		}
	}

	// ---- controle ----

	public static synchronized void setReportDir(Path dir) {
		reportDir = dir;
	}

	public static boolean isRunning() {
		return running;
	}

	public static Result last() {
		return last;
	}

	public static String lastAbortReason() {
		return lastAbort;
	}

	/**
	 * Inicia uma execução.
	 *
	 * @return false se já havia uma em andamento
	 */
	public static synchronized boolean start(int secs, int renderDist, String ctx) {
		if (running) {
			return false;
		}
		seconds = Math.max(1, Math.min(3600, secs));
		durationNs = seconds * 1000000000L;
		renderDistance = renderDist;
		context = ctx == null ? "" : ctx;
		modEnabled = Settings.bool(Opt.ENABLED);
		profileLabel = Settings.profile().label;
		adaptiveLevel = Live.adaptiveLevel;
		startedAtMs = System.currentTimeMillis();
		startedAtText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(startedAtMs));
		gcCount0 = SystemProbe.gcCount();
		gcTime0 = SystemProbe.gcTimeMs();
		effects0 = HookStatus.snapshotEffects();
		frameCount = 0;
		synchronized (LOCK) {
			tickCount = 0;
			sampleCount = 0;
			chunkPackets = 0;
		}
		lastAbort = "";
		startNs = System.nanoTime();
		running = true;
		return true;
	}

	public static boolean due() {
		return running && System.nanoTime() - startNs >= durationNs;
	}

	public static int secondsLeft() {
		if (!running) {
			return 0;
		}
		long left = durationNs - (System.nanoTime() - startNs);
		return left <= 0L ? 0 : (int) ((left + 999999999L) / 1000000000L);
	}

	/** Progresso de 0 a 1. */
	public static double progress() {
		if (!running) {
			return 0.0D;
		}
		double p = (System.nanoTime() - startNs) / (double) durationNs;
		return Math.max(0.0D, Math.min(1.0D, p));
	}

	public static synchronized void abort(String why) {
		if (running) {
			running = false;
			lastAbort = why == null ? "" : why;
			Log.info("Benchmark cancelado: " + lastAbort);
		}
	}

	// ---- coleta ----

	/** Chamado em cada quadro pelo {@link PerfMonitor} (thread de renderização). */
	public static void onFrame(long ns) {
		if (!running) {
			return;
		}
		int n = frameCount;
		if (n >= MAX_FRAMES) {
			return;
		}
		if (n == frames.length) {
			frames = Arrays.copyOf(frames, Math.min(MAX_FRAMES, n * 2));
		}
		frames[n] = ns;
		frameCount = n + 1;
	}

	public static void onServerTick(float ms) {
		if (!running) {
			return;
		}
		synchronized (LOCK) {
			if (tickCount == ticks.length) {
				ticks = Arrays.copyOf(ticks, tickCount * 2);
			}
			ticks[tickCount++] = ms;
		}
	}

	public static void onChunkPacket() {
		if (!running) {
			return;
		}
		synchronized (LOCK) {
			chunkPackets++;
		}
	}

	/** Chamado uma vez por segundo pela thread de amostragem. */
	public static void onSecond(double cpuLoad, long heapUsedBytes) {
		if (!running) {
			return;
		}
		synchronized (LOCK) {
			if (sampleCount == cpuSamples.length) {
				cpuSamples = Arrays.copyOf(cpuSamples, sampleCount * 2);
				heapSamples = Arrays.copyOf(heapSamples, sampleCount * 2);
			}
			cpuSamples[sampleCount] = cpuLoad;
			heapSamples[sampleCount] = heapUsedBytes;
			sampleCount++;
		}
	}

	// ---- resultado ----

	/** Encerra a execução, calcula os números, grava o relatório e compara com o teste anterior. */
	public static synchronized Result finish() {
		if (!running) {
			return last;
		}
		long endNs = System.nanoTime();
		running = false;

		Result r = new Result();
		r.startedAt = startedAtText;
		r.seconds = seconds;
		r.actualSeconds = (endNs - startNs) / 1.0e9D;
		r.modEnabled = modEnabled;
		r.profile = profileLabel;
		r.adaptiveLevel = adaptiveLevel;
		r.renderDistance = renderDistance;
		r.context = context;

		r.frame = PerfMonitor.statsOf(frames, frameCount);
		r.valid = r.frame.frames >= MIN_VALID_FRAMES;

		synchronized (LOCK) {
			r.serverTicks = tickCount;
			if (tickCount > 0) {
				double sum = 0.0D;
				double max = 0.0D;
				int slow = 0;
				for (int i = 0; i < tickCount; i++) {
					float v = ticks[i];
					sum += v;
					if (v > max) {
						max = v;
					}
					if (v > 50.0F) {
						slow++;
					}
				}
				r.msptAvg = sum / tickCount;
				r.msptMax = max;
				r.slowTicks = slow;
			}
			double cs = 0.0D;
			int cn = 0;
			double cmax = 0.0D;
			double hs = 0.0D;
			long hmax = 0L;
			for (int i = 0; i < sampleCount; i++) {
				double c = cpuSamples[i];
				if (c >= 0.0D) {
					cs += c;
					cn++;
					if (c > cmax) {
						cmax = c;
					}
				}
				hs += heapSamples[i];
				if (heapSamples[i] > hmax) {
					hmax = heapSamples[i];
				}
			}
			if (cn > 0) {
				r.cpuAvg = cs / cn;
				r.cpuMax = cmax;
			}
			if (sampleCount > 0) {
				r.heapAvgMb = (long) (hs / sampleCount) / (1024L * 1024L);
				r.heapMaxMb = hmax / (1024L * 1024L);
			} else {
				r.heapAvgMb = SystemProbe.heapUsedBytes() / (1024L * 1024L);
				r.heapMaxMb = r.heapAvgMb;
			}
			r.chunkPackets = chunkPackets;
		}
		r.gcCount = SystemProbe.gcCount() - gcCount0;
		r.gcTimeMs = SystemProbe.gcTimeMs() - gcTime0;
		long[] now = HookStatus.snapshotEffects();
		for (int i = 0; i < HookStatus.COUNT; i++) {
			r.effects[i] = Math.max(0L, now[i] - effects0[i]);
		}

		Properties previous = loadPrevious();
		if (previous != null && r.valid) {
			r.comparison = compare(previous, r.toProperties());
		}
		r.report = buildReport(r);
		persist(r);
		last = r;
		Log.info("Benchmark concluído: FPS médio " + fmt(r.frame.avgFps, 1) + ", 1% low " + fmt(r.frame.low1Fps, 1));
		return r;
	}

	private static Properties loadPrevious() {
		Path dir = reportDir;
		if (dir == null) {
			return null;
		}
		Path f = dir.resolve("last.properties");
		if (!Files.isRegularFile(f)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(f)) {
			Properties p = new Properties();
			p.load(in);
			return p.getProperty("avgFps") == null ? null : p;
		} catch (IOException | RuntimeException e) {
			Log.warn("Não foi possível ler o benchmark anterior", e);
			return null;
		}
	}

	private static void persist(Result r) {
		Path dir = reportDir;
		if (dir == null) {
			return;
		}
		try {
			Files.createDirectories(dir);
			String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(startedAtMs));
			Path txt = dir.resolve("benchmark-" + stamp + ".txt");
			Files.write(txt, r.report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			r.reportFile = txt.toString();
			if (r.valid) {
				try (OutputStream out = Files.newOutputStream(dir.resolve("last.properties"))) {
					r.toProperties().store(out, "Ultra Performance - último benchmark");
				}
			}
		} catch (IOException | RuntimeException e) {
			Log.warn("Não foi possível gravar o relatório do benchmark", e);
		}
	}

	// ---- comparação ----

	/** Compara duas execuções (anterior e atual) e devolve linhas de texto. Público para os testes. */
	public static List<String> compare(Properties prev, Properties cur) {
		List<String> out = new ArrayList<String>();
		out.add("Comparação com o teste anterior (" + prev.getProperty("startedAt", "?") + "):");
		out.add("  Antes: " + conditions(prev));
		out.add("  Agora: " + conditions(cur));
		if (!same(prev, cur, "renderDistance")) {
			out.add("  AVISO: a distância de renderização é diferente (" + prev.getProperty("renderDistance", "?") + " e "
					+ cur.getProperty("renderDistance", "?") + "): a comparação não é justa.");
		}
		if (!same(prev, cur, "seconds")) {
			out.add("  Nota: as durações são diferentes; testes mais longos são mais confiáveis.");
		}
		if (same(prev, cur, "modEnabled") && same(prev, cur, "profile") && same(prev, cur, "adaptiveLevel")) {
			out.add("  Nota: as condições do mod são idênticas nos dois testes; a diferença abaixo mostra a variação "
					+ "natural entre execuções, não o efeito do mod.");
		}
		delta(out, prev, cur, "FPS médio", "avgFps", true, 1.0D, 1, "");
		delta(out, prev, cur, "1% low", "low1Fps", true, 1.0D, 1, "");
		delta(out, prev, cur, "0,1% low", "low01Fps", true, 1.0D, 1, "");
		delta(out, prev, cur, "Quadro p99", "p99Ms", false, 1.0D, 1, " ms");
		delta(out, prev, cur, "Travadas", "stutters", false, 1.0D, 0, "");
		delta(out, prev, cur, "Tick do servidor", "msptAvg", false, 1.0D, 1, " ms");
		delta(out, prev, cur, "CPU do processo", "cpuAvg", false, 100.0D, 0, "%");
		delta(out, prev, cur, "RAM em uso", "heapAvgMb", false, 1.0D, 0, " MB");
		delta(out, prev, cur, "Tempo em GC", "gcTimeMs", false, 1.0D, 0, " ms");
		return out;
	}

	private static String conditions(Properties p) {
		return "otimizações " + ("true".equals(p.getProperty("modEnabled")) ? "ATIVAS" : "DESLIGADAS") + ", perfil "
				+ p.getProperty("profile", "?") + ", nível adaptativo " + p.getProperty("adaptiveLevel", "?")
				+ ", distância " + p.getProperty("renderDistance", "?") + ", " + p.getProperty("seconds", "?") + " s";
	}

	private static boolean same(Properties a, Properties b, String key) {
		String x = a.getProperty(key);
		String y = b.getProperty(key);
		return x == null ? y == null : x.equals(y);
	}

	private static void delta(List<String> out, Properties prev, Properties cur, String label, String key,
			boolean higherIsBetter, double scale, int decimals, String unit) {
		double a;
		double b;
		try {
			a = Double.parseDouble(prev.getProperty(key));
			b = Double.parseDouble(cur.getProperty(key));
		} catch (RuntimeException e) {
			return;
		}
		if (a < 0.0D || b < 0.0D) {
			return; // medida indisponível em um dos testes
		}
		a *= scale;
		b *= scale;
		String text = "  " + label + ": " + fmt(a, decimals) + unit + " -> " + fmt(b, decimals) + unit;
		if (a == 0.0D) {
			out.add(text + (b == 0.0D ? " (sem mudança)" : ""));
			return;
		}
		double pct = (b - a) / a * 100.0D;
		String verdict;
		if (Math.abs(pct) < NOISE_MARGIN_PCT) {
			verdict = "dentro da margem de ruído";
		} else if ((pct > 0.0D) == higherIsBetter) {
			verdict = "melhor";
		} else {
			verdict = "pior";
		}
		out.add(text + " (" + (pct >= 0.0D ? "+" : "") + fmt(pct, 1) + "%, " + verdict + ")");
	}

	// ---- relatório ----

	private static String buildReport(Result r) {
		StringBuilder sb = new StringBuilder();
		sb.append("=== Ultra Performance - relatório de benchmark ===\n");
		sb.append("Data: ").append(r.startedAt).append('\n');
		sb.append("Duração pedida: ").append(r.seconds).append(" s (medida: ").append(fmt(r.actualSeconds, 1))
				.append(" s)\n\n");
		sb.append("Condições do teste\n");
		sb.append("  Otimizações do mod: ").append(r.modEnabled ? "ATIVAS" : "DESLIGADAS").append('\n');
		sb.append("  Perfil: ").append(r.profile).append('\n');
		sb.append("  Nível do ajuste adaptativo: ").append(r.adaptiveLevel).append(" (fixo durante o teste)\n");
		sb.append("  Distância de renderização: ").append(r.renderDistance).append(" chunks\n");
		if (!r.context.isEmpty()) {
			sb.append("  Local: ").append(r.context).append('\n');
		}
		sb.append('\n');
		if (!r.valid) {
			sb.append("ATENÇÃO: o teste teve só ").append(r.frame.frames)
					.append(" quadros medidos, pouco para qualquer conclusão. Repita com o jogo em primeiro plano.\n\n");
		}
		PerfMonitor.FrameStats f = r.frame;
		sb.append("Quadros (cliente)\n");
		sb.append("  Quadros medidos: ").append(f.frames).append('\n');
		sb.append("  FPS médio: ").append(fmt(f.avgFps, 1)).append('\n');
		sb.append("  FPS do pior quadro: ").append(fmt(f.minFps, 1)).append('\n');
		sb.append("  1% low: ").append(fmt(f.low1Fps, 1)).append('\n');
		sb.append("  0,1% low: ").append(fmt(f.low01Fps, 1)).append('\n');
		sb.append("  Tempo de quadro: médio ").append(fmt(f.avgMs, 2)).append(" ms, mediana ").append(fmt(f.medianMs, 2))
				.append(" ms, p99 ").append(fmt(f.p99Ms, 2)).append(" ms, máximo ").append(fmt(f.maxMs, 1))
				.append(" ms\n");
		sb.append("  Travadas (quadros acima de 33 ms e de 2x a mediana): ").append(f.stutters).append("\n\n");
		sb.append("Servidor local\n");
		if (r.msptAvg >= 0.0D) {
			sb.append("  Tick: média ").append(fmt(r.msptAvg, 2)).append(" ms, máximo ").append(fmt(r.msptMax, 1))
					.append(" ms, ").append(r.serverTicks).append(" ticks, ").append(r.slowTicks)
					.append(" acima de 50 ms\n\n");
		} else {
			sb.append("  Não medido (você estava num servidor remoto ou fora de um mundo).\n\n");
		}
		sb.append("Sistema\n");
		if (r.cpuAvg >= 0.0D) {
			sb.append("  CPU do processo: média ").append(fmt(r.cpuAvg * 100.0D, 0)).append("%, pico ")
					.append(fmt(r.cpuMax * 100.0D, 0)).append("% (todos os núcleos)\n");
		} else {
			sb.append("  CPU do processo: indisponível nesta JVM\n");
		}
		sb.append("  RAM da JVM em uso: média ").append(r.heapAvgMb).append(" MB, pico ").append(r.heapMaxMb)
				.append(" MB\n");
		sb.append("  Coletas de lixo: ").append(r.gcCount).append(", ").append(r.gcTimeMs).append(" ms no total\n");
		sb.append("  Pacotes de chunk recebidos: ").append(r.chunkPackets).append("\n\n");
		sb.append("Ganchos do mod (efeito dentro do período do teste)\n");
		for (int i = 0; i < HookStatus.COUNT; i++) {
			sb.append("  ").append(HookStatus.name(i)).append(": ");
			if (HookStatus.isFailed(i)) {
				sb.append("FALHOU e foi desativado\n");
			} else {
				sb.append(r.effects[i]).append(' ').append(HookStatus.effectName(i)).append('\n');
			}
		}
		sb.append('\n');
		if (!r.comparison.isEmpty()) {
			for (String s : r.comparison) {
				sb.append(s).append('\n');
			}
			sb.append('\n');
		}
		sb.append("Configuração\n");
		sb.append(Settings.dump()).append('\n');
		sb.append("Hardware\n");
		for (String s : HardwareInfo.summaryLines()) {
			sb.append("  ").append(s).append('\n');
		}
		sb.append("\nComo interpretar\n");
		sb.append("  - Compare só testes no mesmo mundo, no mesmo lugar, com a mesma distância de renderização.\n");
		sb.append("  - Para medir o efeito do mod: faça um teste com as otimizações ativas, desligue \"Otimizações ativas\"\n");
		sb.append("    na aba Geral e repita. Diferenças abaixo de ").append(NOISE_MARGIN_PCT)
				.append("% estão dentro da variação normal entre execuções.\n");
		sb.append("  - O 1% low e as travadas mostram a fluidez melhor que o FPS médio.\n");
		return sb.toString();
	}

	static String fmt(double v, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f", v);
	}
}
