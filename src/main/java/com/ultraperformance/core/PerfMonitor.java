package com.ultraperformance.core;

import java.util.Arrays;

/**
 * Monitor de desempenho: tempos de quadro (cliente), tempos de tick do servidor,
 * uso de CPU e memória (amostrados uma vez por segundo numa thread própria).
 */
public final class PerfMonitor {
	private static final int CAP = 8192;
	private static final int MASK = CAP - 1;
	private static final long[] FRAME_NS = new long[CAP];
	private static final long[] FRAME_TS = new long[CAP];
	private static volatile int frameHead;
	private static long lastFrameStart;

	private static final float[] TICK_MS = new float[256];
	private static volatile int tickHead;
	private static long tickStart;

	private static volatile double lastCpu = -1.0D;
	private static volatile long lastHeapUsed;
	private static volatile long clientStartedMs = -1L;
	private static Thread sampler;

	private PerfMonitor() {
	}

	/** Estatísticas de uma série de tempos de quadro. */
	public static final class FrameStats {
		public int frames;
		public double avgFps;
		public double minFps;
		public double low1Fps;
		public double low01Fps;
		public double avgMs;
		public double medianMs;
		public double p99Ms;
		public double maxMs;
		public int stutters;
	}

	// ---- quadros ----

	/** Chamado no início de cada quadro (thread de renderização). */
	public static void frameStart(long nowNs) {
		long last = lastFrameStart;
		lastFrameStart = nowNs;
		if (last == 0L) {
			return;
		}
		long d = nowNs - last;
		if (d <= 0L || d > 3000000000L) {
			return; // pausa longa (carregamento, minimizado): não é um quadro de verdade
		}
		int h = frameHead;
		FRAME_NS[h & MASK] = d;
		FRAME_TS[h & MASK] = nowNs;
		frameHead = h + 1;
		Benchmark.onFrame(d);
	}

	/** Estatísticas dos quadros dos últimos {@code windowNs} nanossegundos. */
	public static FrameStats frameStats(long nowNs, long windowNs) {
		int head = frameHead;
		int limit = Math.min(head, CAP);
		long[] tmp = new long[limit];
		int n = 0;
		for (int k = 1; k <= limit; k++) {
			int idx = (head - k) & MASK;
			if (nowNs - FRAME_TS[idx] > windowNs) {
				break;
			}
			tmp[n++] = FRAME_NS[idx];
		}
		return statsOf(tmp, n);
	}

	public static FrameStats statsOf(long[] ns, int n) {
		FrameStats s = new FrameStats();
		if (n <= 0) {
			return s;
		}
		long[] a = Arrays.copyOf(ns, n);
		Arrays.sort(a);
		long sum = 0L;
		for (long v : a) {
			sum += v;
		}
		s.frames = n;
		s.avgMs = sum / (double) n / 1.0e6D;
		s.avgFps = s.avgMs > 0.0D ? 1000.0D / s.avgMs : 0.0D;
		s.maxMs = a[n - 1] / 1.0e6D;
		s.minFps = s.maxMs > 0.0D ? 1000.0D / s.maxMs : 0.0D;
		s.medianMs = a[n / 2] / 1.0e6D;
		s.p99Ms = a[Math.min(n - 1, (int) (n * 0.99D))] / 1.0e6D;
		s.low1Fps = lowFps(a, n, 0.01D);
		s.low01Fps = lowFps(a, n, 0.001D);
		double threshold = Math.max(33.0D, s.medianMs * 2.0D);
		int c = 0;
		for (int i = n - 1; i >= 0 && a[i] / 1.0e6D > threshold; i--) {
			c++;
		}
		s.stutters = c;
		return s;
	}

	private static double lowFps(long[] sorted, int n, double fraction) {
		int k = Math.max(1, (int) Math.ceil(n * fraction));
		long sum = 0L;
		for (int i = n - k; i < n; i++) {
			sum += sorted[i];
		}
		double avgMs = sum / (double) k / 1.0e6D;
		return avgMs > 0.0D ? 1000.0D / avgMs : 0.0D;
	}

	// ---- ticks do servidor ----

	public static void serverTickStart(long nowNs) {
		tickStart = nowNs;
	}

	public static void serverTickEnd(long nowNs) {
		long s = tickStart;
		if (s == 0L) {
			return;
		}
		float ms = (nowNs - s) / 1.0e6F;
		int h = tickHead;
		TICK_MS[h & 255] = ms;
		tickHead = h + 1;
		Benchmark.onServerTick(ms);
	}

	/** {média, máximo, quantidade} dos últimos até 100 ticks do servidor. */
	public static double[] msptStats() {
		int head = tickHead;
		int n = Math.min(head, 100);
		if (n == 0) {
			return new double[] {-1.0D, -1.0D, 0.0D};
		}
		double sum = 0.0D;
		double max = 0.0D;
		for (int k = 1; k <= n; k++) {
			float v = TICK_MS[(head - k) & 255];
			sum += v;
			if (v > max) {
				max = v;
			}
		}
		return new double[] {sum / n, max, n};
	}

	// ---- amostragem de CPU e memória ----

	public static synchronized void startSampler() {
		if (sampler != null) {
			return;
		}
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException e) {
						return;
					}
					try {
						sampleOnce();
					} catch (Throwable ignored) {
						// o monitor nunca pode derrubar o jogo
					}
				}
			}
		}, "UltraPerformance-Sampler");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
		sampler = t;
	}

	static void sampleOnce() {
		double cpu = SystemProbe.processCpuLoad();
		long used = SystemProbe.heapUsedBytes();
		lastCpu = cpu;
		lastHeapUsed = used;
		Benchmark.onSecond(cpu, used);
	}

	public static double lastCpuLoad() {
		return lastCpu;
	}

	public static long lastHeapUsedBytes() {
		return lastHeapUsed;
	}

	public static void setClientStartedMs(long ms) {
		clientStartedMs = ms;
	}

	/** Tempo desde o início da JVM até o cliente ficar pronto, ou -1. */
	public static long clientStartedMs() {
		return clientStartedMs;
	}
}
