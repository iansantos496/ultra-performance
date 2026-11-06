package com.ultraperformance.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecção de hardware e recomendação de perfil. As classificações de CPU e GPU
 * são estimativas por heurística sobre o nome do componente: servem de ponto de
 * partida e podem ser ignoradas escolhendo outro perfil.
 */
public final class HardwareInfo {
	public enum Tier {
		MUITO_FRACO("muito fraco"), FRACO("fraco"), MEDIO("médio"), FORTE("forte");

		public final String label;

		Tier(String label) {
			this.label = label;
		}
	}

	private static final Pattern INTEL_CORE = Pattern.compile("i[3579]-(\\d{4,5})");
	private static final Pattern RYZEN = Pattern.compile("ryzen\\s*[3579]\\s*(\\d{4})");
	private static final Pattern INTEL_HD = Pattern.compile("hd graphics\\s*(\\d{3,4})");
	private static final Pattern INTEL_ARC = Pattern.compile("\\barc\\b");
	private static final Pattern INTEL_ARC_ENTRY = Pattern.compile("\\ba3\\d\\d\\b");
	private static final Pattern GL_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");

	private static boolean detected;
	private static int cores = 1;
	private static long maxHeapMb;
	private static long physMb = -1L;
	private static long physFreeMb = -1L;
	private static String os = "?";
	private static String arch = "?";
	private static String javaVersion = "?";
	private static String javaVendor = "?";
	private static String dataModel = "?";
	private static String cpuName = "?";
	private static String gpuRenderer = "?";
	private static String gpuVendor = "?";
	private static String glVersion = "?";
	private static boolean gpuKnown;

	private HardwareInfo() {
	}

	public static synchronized void detectBase() {
		if (detected) {
			return;
		}
		detected = true;
		cores = Math.max(1, Runtime.getRuntime().availableProcessors());
		maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
		physMb = SystemProbe.totalPhysicalMb();
		physFreeMb = SystemProbe.freePhysicalMb();
		os = System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "");
		arch = System.getProperty("os.arch", "?");
		javaVersion = System.getProperty("java.version", "?");
		javaVendor = System.getProperty("java.vendor", "?");
		dataModel = System.getProperty("sun.arch.data.model", "?");
		String env = System.getenv("PROCESSOR_IDENTIFIER");
		if (env != null && !env.isEmpty()) {
			cpuName = env;
		}
	}

	/** Chamado pelo cliente depois que o contexto OpenGL existe. */
	public static synchronized void setGpu(String renderer, String vendor, String version, String cpu) {
		detectBase();
		if (renderer != null && !renderer.isEmpty()) {
			gpuRenderer = renderer.trim();
			gpuKnown = true;
		}
		if (vendor != null && !vendor.isEmpty()) {
			gpuVendor = vendor.trim();
		}
		if (version != null && !version.isEmpty()) {
			glVersion = version.trim();
		}
		if (cpu != null && !cpu.trim().isEmpty() && !cpu.contains("unknown")) {
			cpuName = cpu.trim();
		}
	}

	public static synchronized boolean isGpuKnown() {
		return gpuKnown;
	}

	public static synchronized String gpuRenderer() {
		return gpuRenderer;
	}

	public static synchronized String cpuName() {
		return cpuName;
	}

	public static synchronized int cores() {
		detectBase();
		return cores;
	}

	// ---- classificação ----

	public static Tier classifyGpu(String renderer) {
		if (renderer == null || renderer.isEmpty() || renderer.equals("?")) {
			return Tier.MEDIO;
		}
		String r = renderer.toLowerCase(Locale.ROOT);
		if (r.contains("llvmpipe") || r.contains("software") || r.contains("gdi generic")
				|| r.contains("swiftshader") || r.contains("microsoft basic")) {
			return Tier.MUITO_FRACO;
		}
		if (r.contains("intel")) {
			if (r.contains("gma") || r.contains("sandy bridge") || r.contains("ironlake")) {
				return Tier.MUITO_FRACO;
			}
			Matcher m = INTEL_HD.matcher(r);
			if (m.find()) {
				int n = Integer.parseInt(m.group(1));
				return (n == 2000 || n == 3000) ? Tier.MUITO_FRACO : Tier.FRACO;
			}
			if (r.contains("hd graphics")) {
				return Tier.MUITO_FRACO;
			}
			if (INTEL_ARC.matcher(r).find()) {
				return INTEL_ARC_ENTRY.matcher(r).find() ? Tier.MEDIO : Tier.FORTE;
			}
			if (r.contains("iris")) {
				return Tier.MEDIO;
			}
			return Tier.FRACO;
		}
		if (r.contains("nvidia") || r.contains("geforce")) {
			if (r.contains("rtx")) {
				return Tier.FORTE;
			}
			int gtx = numberAfter(r, "gtx");
			if (gtx >= 0) {
				return gtx >= 900 ? Tier.FORTE : (gtx >= 600 ? Tier.MEDIO : Tier.FRACO);
			}
			if (r.contains(" gt ") || r.contains("geforce gt")) {
				return Tier.FRACO;
			}
			if (r.contains(" mx")) {
				return Tier.MEDIO;
			}
			return Tier.MEDIO;
		}
		if (r.contains("radeon") || r.contains("amd") || r.contains("ati ")) {
			int rx = numberAfter(r, "rx");
			if (rx >= 0) {
				return rx >= 600 ? Tier.FORTE : Tier.MEDIO;
			}
			if (r.contains("vega")) {
				return Tier.MEDIO;
			}
			if (r.contains("radeon hd") || r.contains("radeon r5") || r.contains("radeon(tm) r5")) {
				return Tier.FRACO;
			}
			return Tier.MEDIO;
		}
		return Tier.MEDIO;
	}

	/** Primeiro número que aparece depois da palavra-chave (por exemplo "gtx 1060" -> 1060), ou -1. */
	private static int numberAfter(String text, String keyword) {
		int i = text.indexOf(keyword);
		if (i < 0) {
			return -1;
		}
		Matcher m = Pattern.compile("(\\d{3,4})").matcher(text.substring(i + keyword.length()));
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	public static Tier classifyCpu(String name, int coreCount) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (n.contains("celeron") || n.contains("pentium") || n.contains("atom")) {
			return coreCount <= 2 ? Tier.MUITO_FRACO : Tier.FRACO;
		}
		Matcher m = INTEL_CORE.matcher(n);
		if (m.find()) {
			int gen = Integer.parseInt(m.group(1)) / 1000;
			Tier t = gen <= 3 ? Tier.FRACO : (gen <= 7 ? Tier.MEDIO : Tier.FORTE);
			if (coreCount <= 2 && t.ordinal() > Tier.FRACO.ordinal()) {
				t = Tier.FRACO;
			}
			return t;
		}
		Matcher ry = RYZEN.matcher(n);
		if (ry.find()) {
			int gen = Integer.parseInt(ry.group(1)) / 1000;
			Tier t = gen <= 2 ? Tier.MEDIO : Tier.FORTE;
			if (coreCount <= 4 && t == Tier.FORTE) {
				t = Tier.MEDIO;
			}
			return t;
		}
		if (n.contains("athlon") || n.contains("phenom") || n.contains("fx-") || n.contains("sempron")
				|| n.matches(".*\\ba[468]-.*")) {
			return coreCount <= 2 ? Tier.MUITO_FRACO : Tier.FRACO;
		}
		if (coreCount <= 2) {
			return Tier.MUITO_FRACO;
		}
		if (coreCount <= 4) {
			return Tier.FRACO;
		}
		return coreCount <= 6 ? Tier.MEDIO : Tier.FORTE;
	}

	public static Tier classifyRam(long physicalMb) {
		if (physicalMb <= 0L) {
			return Tier.MEDIO;
		}
		if (physicalMb <= 4096L + 256L) {
			return Tier.FRACO;
		}
		return physicalMb <= 8192L + 512L ? Tier.MEDIO : Tier.FORTE;
	}

	public static Profile recommend(Tier cpu, Tier gpu, Tier ram) {
		if (gpu == Tier.MUITO_FRACO || cpu == Tier.MUITO_FRACO) {
			return Profile.ULTRA_ECONOMICO;
		}
		if (gpu == Tier.FRACO && cpu.ordinal() <= Tier.FRACO.ordinal()) {
			return Profile.ULTRA_ECONOMICO;
		}
		if (gpu == Tier.FRACO || cpu == Tier.FRACO || ram == Tier.FRACO) {
			return Profile.DESEMPENHO;
		}
		return Profile.EQUILIBRADO;
	}

	public static synchronized Tier cpuTier() {
		detectBase();
		return classifyCpu(cpuName, cores);
	}

	public static synchronized Tier gpuTier() {
		return classifyGpu(gpuRenderer);
	}

	public static synchronized Tier ramTier() {
		detectBase();
		return classifyRam(physMb);
	}

	public static Profile recommendedProfile() {
		return recommend(cpuTier(), gpuTier(), ramTier());
	}

	/** Argumentos de JVM sugeridos para o launcher (não são aplicados pelo mod). */
	public static synchronized String recommendedJvmArgs() {
		detectBase();
		long xmx = physMb > 0L ? (long) (physMb * 0.40D) / 1024L * 1024L : 3072L;
		xmx = Math.max(2048L, Math.min(6144L, xmx));
		long xms = Math.min(xmx, 1024L);
		return "-Xms" + (xms / 1024L) + "G -Xmx" + (xmx / 1024L) + "G -XX:+UseG1GC -XX:+ParallelRefProcEnabled"
				+ " -XX:MaxGCPauseMillis=50";
	}

	public static synchronized List<String> notes() {
		detectBase();
		List<String> out = new ArrayList<String>();
		if (maxHeapMb > 0L && maxHeapMb < 1536L) {
			out.add("O heap máximo da JVM é só " + maxHeapMb + " MB. Use pelo menos 2 GB (veja os argumentos sugeridos).");
		}
		if (dataModel.contains("32")) {
			out.add("JVM de 32 bits detectada: o Minecraft fica limitado a ~1,5 GB. Instale um Java 8 de 64 bits.");
		}
		if (physFreeMb >= 0L && physFreeMb < 1024L) {
			out.add("Pouca RAM livre no sistema (" + physFreeMb + " MB). Feche outros programas.");
		}
		Matcher m = GL_VERSION.matcher(glVersion);
		if (gpuKnown && m.find()) {
			int major = Integer.parseInt(m.group(1));
			int minor = Integer.parseInt(m.group(2));
			if (major < 3 || (major == 3 && minor < 2)) {
				out.add("Sua GPU só expõe OpenGL " + major + "." + minor
						+ ": mods de renderização que exigem 3.2 (como o Sodium) provavelmente não funcionam nela.");
			}
		}
		return out;
	}

	public static synchronized List<String> summaryLines() {
		detectBase();
		List<String> out = new ArrayList<String>();
		out.add("CPU: " + cpuName + " (" + cores + " threads) - nível " + cpuTier().label);
		out.add("GPU: " + gpuRenderer + " (" + gpuVendor + ") - OpenGL " + glVersion + " - nível " + gpuTier().label);
		out.add("RAM: " + (physMb > 0L ? physMb + " MB no sistema" : "total desconhecido") + ", heap máximo da JVM "
				+ maxHeapMb + " MB - nível " + ramTier().label);
		out.add("Sistema: " + os + " (" + arch + ") | Java " + javaVersion + " (" + javaVendor + ")");
		out.add("Perfil recomendado: " + recommendedProfile().label);
		out.add("Argumentos de JVM sugeridos: " + recommendedJvmArgs());
		out.addAll(notes());
		return out;
	}
}
