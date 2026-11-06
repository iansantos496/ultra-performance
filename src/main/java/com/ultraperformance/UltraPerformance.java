package com.ultraperformance;

import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.HardwareInfo;
import com.ultraperformance.core.Log;
import com.ultraperformance.core.PerfMonitor;
import com.ultraperformance.core.Settings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Inicialização comum (cliente e servidor): log, configuração, hardware, monitor e pasta de relatórios. */
public final class UltraPerformance implements ModInitializer {
	public static final String MOD_ID = "ultraperformance";
	private static final Logger LOGGER = LogManager.getLogger("UltraPerformance");

	@Override
	public void onInitialize() {
		Log.setSink(new Log.Sink() {
			@Override
			public void info(String msg) {
				LOGGER.info(msg);
			}

			@Override
			public void warn(String msg) {
				LOGGER.warn(msg);
			}
		});
		FabricLoader loader = FabricLoader.getInstance();
		Settings.init(loader.getConfigDir().resolve("ultraperformance.json"));
		HardwareInfo.detectBase();
		PerfMonitor.startSampler();
		Benchmark.setReportDir(loader.getGameDir().resolve("ultraperformance").resolve("benchmarks"));
		String version = loader.getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		Log.info("Ultra Performance " + version + " iniciado. Perfil: " + Settings.profile().label
				+ ", otimizações " + (Settings.bool("enabled") ? "ativas" : "desligadas"));
	}
}
