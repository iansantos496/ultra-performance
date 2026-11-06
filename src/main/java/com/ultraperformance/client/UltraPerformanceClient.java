package com.ultraperformance.client;

import net.fabricmc.api.ClientModInitializer;

public final class UltraPerformanceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientRuntime.init();
	}
}
