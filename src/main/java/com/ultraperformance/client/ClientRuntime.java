package com.ultraperformance.client;

import com.mojang.blaze3d.platform.GlDebugInfo;
import com.ultraperformance.client.gui.ConfigScreen;
import com.ultraperformance.core.AdaptiveController;
import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.HardwareInfo;
import com.ultraperformance.core.Live;
import com.ultraperformance.core.Log;
import com.ultraperformance.core.Opt;
import com.ultraperformance.core.PerfMonitor;
import com.ultraperformance.core.Profile;
import com.ultraperformance.core.Settings;
import com.ultraperformance.core.SystemProbe;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tudo que roda no cliente a cada tick: teclas, sonda de GPU, primeira execução, adaptativo, benchmark, autosave. */
public final class ClientRuntime {
	private static final int KEY_CONFIG = 344; // GLFW_KEY_RIGHT_SHIFT
	private static final int KEY_OVERLAY = 295; // GLFW_KEY_F6

	private static final AdaptiveController ADAPTIVE = new AdaptiveController();
	private static KeyBinding configKey;
	private static KeyBinding overlayKey;

	private static boolean gpuProbed;
	private static boolean wasInWorld;
	private static List<String> welcome = Collections.emptyList();
	private static String tickError;
	private static List<String> lastBenchmark = Collections.emptyList();

	private static int rdBase = -1;
	private static int rdApplied = -1;

	private ClientRuntime() {
	}

	public static void init() {
		configKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.ultraperformance.config", KEY_CONFIG, "key.categories.ultraperformance"));
		overlayKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.ultraperformance.overlay", KEY_OVERLAY, "key.categories.ultraperformance"));
		ClientTickEvents.END_CLIENT_TICK.register(ClientRuntime::onTick);
		HudRenderCallback.EVENT.register((matrices, delta) -> OverlayRenderer.render(MinecraftClient.getInstance(), matrices));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> PerfMonitor.setClientStartedMs(SystemProbe.jvmUptimeMs()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			restoreViewDistance(client);
			Settings.flush();
		});
	}

	public static String tickError() {
		return tickError;
	}

	public static List<String> lastBenchmarkLines() {
		return lastBenchmark;
	}

	public static AdaptiveController adaptive() {
		return ADAPTIVE;
	}

	/** FPS alvo efetivo: nunca acima do limite de FPS ou da taxa do monitor (com VSync). */
	public static int effectiveTarget(MinecraftClient client) {
		int target = Settings.get(Opt.ADAPTIVE_TARGET);
		GameOptions o = client.options;
		if (o.maxFps > 0 && o.maxFps < 260) {
			target = Math.min(target, Math.max(15, (int) (o.maxFps * 0.92D)));
		}
		if (o.enableVsync) {
			int hz = client.getWindow().getRefreshRate();
			if (hz > 0) {
				target = Math.min(target, Math.max(15, (int) (hz * 0.92D)));
			}
		}
		return target;
	}

	public static void message(MinecraftClient client, String text) {
		if (client.inGameHud != null) {
			client.inGameHud.getChatHud().addMessage(new LiteralText("[Ultra Performance] " + text));
		}
	}

	/** Inicia o benchmark. Devolve uma mensagem de erro ou null se começou. */
	public static String startBenchmark(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return "Entre em um mundo para rodar o benchmark.";
		}
		if (Benchmark.isRunning()) {
			return "Já existe um benchmark em andamento.";
		}
		String where = client.world.getRegistryKey().getValue() + " em " + client.player.getBlockPos().getX() + ", "
				+ client.player.getBlockPos().getY() + ", " + client.player.getBlockPos().getZ();
		Benchmark.start(Settings.get(Opt.BENCH_SECONDS), client.options.viewDistance, where);
		client.openScreen(null);
		message(client, "Benchmark de " + Settings.get(Opt.BENCH_SECONDS) + " s iniciado. Não abra telas nem tire o foco da janela.");
		return null;
	}

	private static void onTick(MinecraftClient client) {
		if (tickError != null) {
			return;
		}
		try {
			tick(client);
		} catch (Throwable t) {
			tickError = t.getClass().getSimpleName() + ": " + t.getMessage();
			Log.warn("Falha no ciclo do cliente; ele foi desativado até reiniciar o jogo", t);
		}
	}

	private static void tick(MinecraftClient client) {
		long nowMs = System.nanoTime() / 1000000L;
		if (!gpuProbed) {
			probeGpu();
		}

		while (configKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.openScreen(new ConfigScreen(null));
			}
		}
		while (overlayKey.wasPressed()) {
			Settings.set(Opt.OVERLAY, (Settings.get(Opt.OVERLAY) + 1) % 4);
		}

		boolean inWorld = client.world != null && client.player != null;
		if (inWorld && !wasInWorld) {
			ADAPTIVE.reset(nowMs);
			applyAdaptiveLevel(client, 0);
			for (String line : welcome) {
				message(client, line);
			}
			welcome = Collections.emptyList();
		} else if (!inWorld && wasInWorld) {
			ADAPTIVE.reset(nowMs);
			applyAdaptiveLevel(client, 0);
		}
		wasInWorld = inWorld;

		boolean focused = client.isWindowFocused();
		if (!focused) {
			ADAPTIVE.holdOff(nowMs, 15000L);
		}
		tickBenchmark(client, inWorld, focused, nowMs);
		tickAdaptive(client, inWorld, nowMs);
		Settings.saveIfDirty();
	}

	private static void probeGpu() {
		gpuProbed = true;
		try {
			HardwareInfo.setGpu(GlDebugInfo.getRenderer(), GlDebugInfo.getVendor(), GlDebugInfo.getVersion(),
					GlDebugInfo.getCpuInfo());
		} catch (Throwable t) {
			Log.warn("Não foi possível ler os dados da GPU", t);
			HardwareInfo.detectBase();
		}
		for (String line : HardwareInfo.summaryLines()) {
			Log.info("Hardware: " + line);
		}
		if (Settings.isFirstRun()) {
			Profile rec = HardwareInfo.recommendedProfile();
			Settings.applyProfile(rec);
			Settings.markFirstRunHandled();
			Settings.save();
			List<String> w = new ArrayList<String>();
			w.add("Primeira execução: perfil recomendado para o seu computador: " + rec.label + ".");
			w.add("CPU: " + HardwareInfo.cpuName() + " | GPU: " + HardwareInfo.gpuRenderer());
			w.add("Shift direito abre as configurações; F6 troca o overlay. Lá há um benchmark para medir o efeito real.");
			welcome = w;
		}
	}

	private static void tickBenchmark(MinecraftClient client, boolean inWorld, boolean focused, long nowMs) {
		if (!Benchmark.isRunning()) {
			return;
		}
		ADAPTIVE.holdOff(nowMs, 15000L);
		String abort = null;
		if (!inWorld) {
			abort = "você saiu do mundo";
		} else if (!focused) {
			abort = "a janela perdeu o foco";
		} else if (client.currentScreen != null) {
			abort = "uma tela foi aberta";
		}
		if (abort != null) {
			Benchmark.abort(abort);
			if (inWorld) {
				message(client, "Benchmark cancelado: " + abort + ".");
			}
			return;
		}
		if (Settings.bool(Opt.BENCH_SPIN)) {
			client.player.yaw += 2.0F;
		}
		if (Benchmark.due()) {
			Benchmark.Result r = Benchmark.finish();
			if (r != null) {
				lastBenchmark = r.summary();
				for (String line : lastBenchmark) {
					message(client, line);
				}
			}
		}
	}

	private static void tickAdaptive(MinecraftClient client, boolean inWorld, long nowMs) {
		boolean on = Settings.bool(Opt.ENABLED) && Settings.bool(Opt.ADAPTIVE);
		if (!on) {
			if (Live.adaptiveLevel != 0 || rdBase >= 0) {
				ADAPTIVE.setLevel(0, nowMs);
				applyAdaptiveLevel(client, 0);
			}
			return;
		}
		if (!inWorld || !ADAPTIVE.due(nowMs)) {
			return;
		}
		PerfMonitor.FrameStats st = PerfMonitor.frameStats(System.nanoTime(), AdaptiveController.WINDOW_MS * 1000000L);
		int d = ADAPTIVE.evaluate(nowMs, st.avgFps, st.low1Fps, st.frames, effectiveTarget(client),
				Settings.get(Opt.ADAPTIVE_MAXLEVEL));
		if (d != 0) {
			applyAdaptiveLevel(client, ADAPTIVE.level());
			Log.info("Ajuste adaptativo: " + ADAPTIVE.reason());
		}
	}

	private static void applyAdaptiveLevel(MinecraftClient client, int level) {
		Live.adaptiveLevel = level;
		Live.rebuild();
		adjustViewDistance(client, level);
	}

	/** Opcional: reduz a distância de renderização junto com o nível e devolve o valor do jogador depois. */
	private static void adjustViewDistance(MinecraftClient client, int level) {
		boolean allowed = Settings.bool(Opt.ENABLED) && Settings.bool(Opt.ADAPTIVE) && Settings.bool(Opt.ADAPTIVE_RD);
		if (!allowed || level == 0) {
			restoreViewDistance(client);
			return;
		}
		GameOptions o = client.options;
		if (rdBase < 0 || (rdApplied >= 0 && o.viewDistance != rdApplied)) {
			rdBase = o.viewDistance; // primeira vez, ou o jogador mexeu: o valor atual vira a base
			rdApplied = -1;
		}
		int target = Math.min(rdBase, Math.max(Settings.get(Opt.ADAPTIVE_MIN_RD), rdBase - 2 * level));
		if (o.viewDistance != target) {
			o.viewDistance = target;
			rdApplied = target;
			client.worldRenderer.scheduleTerrainUpdate();
		}
	}

	private static void restoreViewDistance(MinecraftClient client) {
		if (rdBase >= 0) {
			if (rdApplied >= 0 && client.options.viewDistance == rdApplied) {
				client.options.viewDistance = rdBase;
				client.worldRenderer.scheduleTerrainUpdate();
			}
			rdBase = -1;
			rdApplied = -1;
		}
	}
}
