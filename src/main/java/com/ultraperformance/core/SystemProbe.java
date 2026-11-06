package com.ultraperformance.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

/**
 * Leituras do sistema e da JVM. Usa reflexão para as medidas específicas da
 * implementação da JVM (com.sun.management), que não existem em todas elas;
 * quando indisponíveis, os métodos devolvem -1.
 */
public final class SystemProbe {
	private static final Object OS = ManagementFactory.getOperatingSystemMXBean();
	private static Method cpuLoad;
	private static Method totalPhys;
	private static Method freePhys;

	static {
		try {
			Class<?> c = Class.forName("com.sun.management.OperatingSystemMXBean");
			if (c.isInstance(OS)) {
				cpuLoad = c.getMethod("getProcessCpuLoad");
				totalPhys = c.getMethod("getTotalPhysicalMemorySize");
				freePhys = c.getMethod("getFreePhysicalMemorySize");
			}
		} catch (Throwable ignored) {
			// medidas indisponíveis nesta JVM
		}
	}

	private SystemProbe() {
	}

	/** Uso de CPU do processo de 0 a 1 (todos os núcleos), ou -1. */
	public static double processCpuLoad() {
		if (cpuLoad == null) {
			return -1.0D;
		}
		try {
			Object v = cpuLoad.invoke(OS);
			return v instanceof Number ? ((Number) v).doubleValue() : -1.0D;
		} catch (Throwable t) {
			return -1.0D;
		}
	}

	public static long totalPhysicalMb() {
		return readMb(totalPhys);
	}

	public static long freePhysicalMb() {
		return readMb(freePhys);
	}

	private static long readMb(Method m) {
		if (m == null) {
			return -1L;
		}
		try {
			Object v = m.invoke(OS);
			return v instanceof Number ? ((Number) v).longValue() / (1024L * 1024L) : -1L;
		} catch (Throwable t) {
			return -1L;
		}
	}

	public static long heapUsedBytes() {
		Runtime r = Runtime.getRuntime();
		return r.totalMemory() - r.freeMemory();
	}

	public static long gcCount() {
		long total = 0L;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			long c = b.getCollectionCount();
			if (c > 0L) {
				total += c;
			}
		}
		return total;
	}

	public static long gcTimeMs() {
		long total = 0L;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			long t = b.getCollectionTime();
			if (t > 0L) {
				total += t;
			}
		}
		return total;
	}

	public static long jvmUptimeMs() {
		return ManagementFactory.getRuntimeMXBean().getUptime();
	}
}
