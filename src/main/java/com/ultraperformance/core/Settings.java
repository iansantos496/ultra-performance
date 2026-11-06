package com.ultraperformance.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valores atuais das opções, perfil ativo e persistência em JSON.
 * A leitura não usa trava (pode ser chamada de qualquer thread); as alterações
 * acontecem na thread do cliente.
 */
public final class Settings {
	private static final int FORMAT_VERSION = 1;

	private static final Map<String, Integer> VALUES = new ConcurrentHashMap<String, Integer>();
	private static volatile Profile profile = Profile.EQUILIBRADO;
	private static volatile boolean dirty;
	private static volatile boolean firstRun = true;
	private static volatile long lastSaveMs;
	private static Path file;

	static {
		fill(Profile.EQUILIBRADO, true);
	}

	private Settings() {
	}

	private static void fill(Profile p, boolean includeIndependent) {
		for (OptionDef d : Opt.all()) {
			if (includeIndependent || !d.isIndependent()) {
				VALUES.put(d.id, d.defaultFor(p));
			}
		}
	}

	// ---- leitura ----

	public static int get(String id) {
		Integer v = VALUES.get(id);
		if (v != null) {
			return v;
		}
		OptionDef d = Opt.get(id);
		return d == null ? 0 : d.defaultFor(Profile.EQUILIBRADO);
	}

	public static boolean bool(String id) {
		return get(id) != 0;
	}

	public static Profile profile() {
		return profile;
	}

	public static boolean isFirstRun() {
		return firstRun;
	}

	// ---- alteração ----

	public static synchronized void set(String id, int value) {
		OptionDef d = Opt.get(id);
		if (d == null) {
			return;
		}
		int nv = d.clamp(value);
		Integer old = VALUES.put(id, nv);
		if (old != null && old.intValue() == nv) {
			return;
		}
		dirty = true;
		if (!d.isIndependent()) {
			refreshProfileFlag();
		}
		Live.rebuild();
	}

	/** Aplica um perfil. "Personalizado" apenas passa a valer, mantendo os valores atuais. */
	public static synchronized void applyProfile(Profile p) {
		if (p.isPreset()) {
			fill(p, false);
		}
		profile = p;
		dirty = true;
		Live.rebuild();
	}

	/** Restaura as configurações padrão (perfil Equilibrado, tudo no valor original). */
	public static synchronized void resetDefaults() {
		fill(Profile.EQUILIBRADO, true);
		profile = Profile.EQUILIBRADO;
		dirty = true;
		Live.rebuild();
	}

	public static synchronized void markFirstRunHandled() {
		firstRun = false;
		dirty = true;
	}

	/** Verdadeiro se todas as opções dependentes de perfil têm os valores do perfil dado. */
	public static boolean matches(Profile p) {
		if (!p.isPreset()) {
			return false;
		}
		for (OptionDef d : Opt.all()) {
			if (!d.isIndependent() && get(d.id) != d.defaultFor(p)) {
				return false;
			}
		}
		return true;
	}

	private static void refreshProfileFlag() {
		if (profile.isPreset() && !matches(profile)) {
			profile = Profile.PERSONALIZADO;
		}
	}

	// ---- persistência ----

	public static synchronized void init(Path configFile) {
		file = configFile;
		if (Files.exists(file)) {
			try {
				load();
				firstRun = false;
			} catch (IOException | RuntimeException e) {
				Log.warn("Configuração inválida; usando os padrões (o arquivo antigo foi guardado como .broken)", e);
				try {
					Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
							StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// sem problema: o arquivo será sobrescrito no próximo salvamento
				}
				fill(Profile.EQUILIBRADO, true);
				profile = Profile.EQUILIBRADO;
				firstRun = true;
			}
		} else {
			firstRun = true;
		}
		Live.rebuild();
	}

	private static void load() throws IOException {
		String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		Object root = MiniJson.parse(text);
		if (!(root instanceof Map)) {
			throw new IOException("a raiz do arquivo não é um objeto JSON");
		}
		Map<?, ?> m = (Map<?, ?>) root;
		fill(Profile.EQUILIBRADO, true);
		Object pn = m.get("profile");
		Profile p = Profile.parse(pn instanceof String ? (String) pn : null, Profile.EQUILIBRADO);
		Object vs = m.get("values");
		if (vs instanceof Map) {
			Map<?, ?> vm = (Map<?, ?>) vs;
			for (OptionDef d : Opt.all()) {
				Integer iv = toInt(vm.get(d.id));
				if (iv != null) {
					VALUES.put(d.id, d.clamp(iv.intValue()));
				}
			}
		} else if (p.isPreset()) {
			fill(p, false);
		}
		profile = p;
		if (profile.isPreset() && !matches(profile)) {
			profile = Profile.PERSONALIZADO;
		}
		dirty = false;
	}

	private static Integer toInt(Object o) {
		if (o instanceof Boolean) {
			return ((Boolean) o).booleanValue() ? 1 : 0;
		}
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		return null;
	}

	/** Salva no máximo uma vez por segundo, e só se algo mudou. */
	public static void saveIfDirty() {
		if (dirty && System.currentTimeMillis() - lastSaveMs >= 1000L) {
			save();
		}
	}

	/** Salva imediatamente se algo mudou (usado ao fechar telas e ao sair do jogo). */
	public static void flush() {
		if (dirty) {
			save();
		}
	}

	public static synchronized void save() {
		if (file == null) {
			return;
		}
		try {
			Map<String, Object> root = new LinkedHashMap<String, Object>();
			root.put("version", FORMAT_VERSION);
			root.put("profile", profile.name());
			Map<String, Object> vals = new LinkedHashMap<String, Object>();
			for (OptionDef d : Opt.all()) {
				int v = get(d.id);
				vals.put(d.id, d.type == OptionDef.Type.BOOL ? (Object) Boolean.valueOf(v != 0) : (Object) Integer.valueOf(v));
			}
			root.put("values", vals);
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(tmp, MiniJson.write(root).getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
			lastSaveMs = System.currentTimeMillis();
		} catch (IOException | RuntimeException e) {
			Log.warn("Não foi possível salvar a configuração", e);
		}
	}

	/** Lista legível das opções e valores atuais (usada nos relatórios). */
	public static String dump() {
		StringBuilder sb = new StringBuilder();
		sb.append("Perfil: ").append(profile.label).append('\n');
		for (OptionDef d : Opt.all()) {
			sb.append("  ").append(d.label).append(": ").append(d.format(get(d.id))).append('\n');
		}
		return sb.toString();
	}
}
