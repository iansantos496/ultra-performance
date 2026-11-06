package com.ultraperformance.client;

import com.ultraperformance.core.AdaptiveController;
import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import com.ultraperformance.core.Opt;
import com.ultraperformance.core.PerfMonitor;
import com.ultraperformance.core.Settings;
import com.ultraperformance.core.SystemProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Overlay de desempenho no canto superior esquerdo. Modos: 0 desligado, 1 compacto, 2 normal, 3 detalhado. */
public final class OverlayRenderer {
	private static final List<String> LINES = new ArrayList<String>();
	private static final List<Integer> COLORS = new ArrayList<Integer>();
	private static long nextRefresh;
	private static boolean broken;

	private OverlayRenderer() {
	}

	public static void render(MinecraftClient client, MatrixStack matrices) {
		if (broken) {
			return;
		}
		try {
			int mode = Settings.get(Opt.OVERLAY);
			boolean bench = Benchmark.isRunning();
			if ((mode == 0 && !bench) || client.options.hudHidden || client.options.debugEnabled) {
				return;
			}
			long now = System.nanoTime();
			if (now >= nextRefresh) {
				refresh(client, now, mode, bench);
				nextRefresh = now + 500000000L;
			}
			TextRenderer font = client.textRenderer;
			int width = 0;
			for (String s : LINES) {
				width = Math.max(width, font.getWidth(s));
			}
			int x = 4;
			int y = 4;
			DrawableHelper.fill(matrices, x - 2, y - 2, x + width + 2, y + LINES.size() * 10 - 1, 0x90000000);
			for (int i = 0; i < LINES.size(); i++) {
				font.drawWithShadow(matrices, LINES.get(i), x, y + i * 10, COLORS.get(i));
			}
		} catch (Throwable t) {
			broken = true;
			com.ultraperformance.core.Log.warn("Overlay desativado após erro", t);
		}
	}

	private static void add(String s, int color) {
		LINES.add(s);
		COLORS.add(color);
	}

	private static String f(double v, int d) {
		return String.format(Locale.ROOT, "%." + d + "f", v);
	}

	private static void refresh(MinecraftClient client, long now, int mode, boolean bench) {
		LINES.clear();
		COLORS.clear();
		PerfMonitor.FrameStats cur = PerfMonitor.frameStats(now, 2000000000L);
		PerfMonitor.FrameStats lows = PerfMonitor.frameStats(now, AdaptiveController.WINDOW_MS * 1000000L);
		int target = Settings.bool(Opt.ADAPTIVE) ? ClientRuntime.effectiveTarget(client) : 30;
		int color = cur.avgFps >= target ? 0x55FF55 : (cur.avgFps >= target * 0.75D ? 0xFFFF55 : 0xFF5555);

		if (mode >= 1) {
			if (mode == 1) {
				add("FPS " + f(cur.avgFps, 0) + "  1% " + f(lows.low1Fps, 0), color);
			} else {
				add("FPS " + f(cur.avgFps, 0) + "  (1% " + f(lows.low1Fps, 0) + " | 0,1% " + f(lows.low01Fps, 0)
						+ " | pior " + f(lows.minFps, 0) + ")", color);
				add("Quadro " + f(cur.avgMs, 1) + " ms  (p99 " + f(lows.p99Ms, 1) + " ms)", 0xFFFFFF);
				double[] m = PerfMonitor.msptStats();
				add(m[2] > 0 ? "Servidor " + f(m[0], 1) + " ms/tick (máx " + f(m[1], 0) + ")" : "Servidor: sem dados",
						0xFFFFFF);
				Runtime rt = Runtime.getRuntime();
				long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
				double cpu = PerfMonitor.lastCpuLoad();
				add("RAM " + used + "/" + (rt.maxMemory() / 1048576L) + " MB  CPU "
						+ (cpu >= 0 ? f(cpu * 100, 0) + "%" : "?"), 0xFFFFFF);
			}
		}
		if (mode >= 3) {
			add("Perfil: " + Settings.profile().label + " | mod " + (Live.master ? "ATIVO" : "DESLIGADO")
					+ " | adaptativo nível " + Live.adaptiveLevel, 0xAAAAAA);
			add("GC: " + SystemProbe.gcCount() + " coletas, " + SystemProbe.gcTimeMs() + " ms", 0xAAAAAA);
			add("Ocultos: ent " + HookStatus.effects(HookStatus.ENTITY_CULL) + ", blocos "
					+ HookStatus.effects(HookStatus.BLOCK_ENTITY_CULL) + " | part. descartadas "
					+ HookStatus.effects(HookStatus.PARTICLES), 0xAAAAAA);
			add("IA pulada " + HookStatus.effects(HookStatus.AI_THROTTLE) + " | envios adiados "
					+ HookStatus.effects(HookStatus.CHUNK_UPLOAD) + " | chunks " + HookStatus.effects(HookStatus.CHUNK_PACKET),
					0xAAAAAA);
		}
		if (bench) {
			add("BENCHMARK: " + Benchmark.secondsLeft() + " s restantes (abrir qualquer tela cancela)", 0xFFAA00);
		}
	}
}
