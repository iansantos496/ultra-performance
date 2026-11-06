package com.ultraperformance.core;

/**
 * Registro simples. O núcleo não depende do Minecraft: o mod troca o destino
 * para o log4j do jogo na inicialização.
 */
public final class Log {
	public interface Sink {
		void info(String msg);

		void warn(String msg);
	}

	private static volatile Sink sink = new Sink() {
		@Override
		public void info(String msg) {
			System.out.println("[UltraPerformance] " + msg);
		}

		@Override
		public void warn(String msg) {
			System.err.println("[UltraPerformance] AVISO: " + msg);
		}
	};

	private Log() {
	}

	public static void setSink(Sink s) {
		if (s != null) {
			sink = s;
		}
	}

	public static void info(String msg) {
		sink.info(msg);
	}

	public static void warn(String msg) {
		sink.warn(msg);
	}

	public static void warn(String msg, Throwable t) {
		sink.warn(msg + " -> " + t);
	}
}
