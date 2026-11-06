package com.ultraperformance.core;

/**
 * Definição de uma opção configurável: tipo, limites, texto de ajuda e valores
 * padrão de cada perfil (Ultra Econômico, Desempenho, Equilibrado).
 */
public final class OptionDef {
	public enum Type {
		BOOL, INT
	}

	public final String id;
	public final Category category;
	public final Type type;
	public final String label;
	public final String description;
	public final int min;
	public final int max;
	public final int step;
	public final String unit;
	public final String[] valueLabels;

	private final int[] presets;
	private boolean independent;
	private String warning;
	private String zeroLabel;

	private OptionDef(String id, Category category, Type type, String label, String description, int min, int max,
			int step, String unit, String[] valueLabels, int ultra, int perf, int bal) {
		this.id = id;
		this.category = category;
		this.type = type;
		this.label = label;
		this.description = description;
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
		this.valueLabels = valueLabels;
		this.presets = new int[] {ultra, perf, bal};
	}

	public static OptionDef bool(String id, Category c, String label, String description, boolean ultra,
			boolean perf, boolean bal) {
		return new OptionDef(id, c, Type.BOOL, label, description, 0, 1, 1, "", null, ultra ? 1 : 0, perf ? 1 : 0,
				bal ? 1 : 0);
	}

	public static OptionDef range(String id, Category c, String label, String description, int min, int max,
			int step, String unit, int ultra, int perf, int bal) {
		return new OptionDef(id, c, Type.INT, label, description, min, max, step, unit, null, ultra, perf, bal);
	}

	public static OptionDef choice(String id, Category c, String label, String description, String[] labels,
			int ultra, int perf, int bal) {
		return new OptionDef(id, c, Type.INT, label, description, 0, labels.length - 1, 1, "", labels, ultra, perf,
				bal);
	}

	/** Opção que não muda quando um perfil é aplicado (chave geral, overlay, benchmark). */
	public OptionDef independent() {
		this.independent = true;
		return this;
	}

	public OptionDef warn(String text) {
		this.warning = text;
		return this;
	}

	/** Texto exibido quando o valor é 0 (por exemplo "Sem limite"). */
	public OptionDef zero(String text) {
		this.zeroLabel = text;
		return this;
	}

	public boolean isIndependent() {
		return independent;
	}

	public int defaultFor(Profile p) {
		return presets[p.isPreset() ? p.ordinal() : Profile.EQUILIBRADO.ordinal()];
	}

	public int clamp(int v) {
		if (v < min) {
			v = min;
		}
		if (v > max) {
			v = max;
		}
		if (type == Type.INT && step > 1) {
			int k = Math.round((v - min) / (float) step);
			v = min + k * step;
			if (v > max) {
				v = max;
			}
		}
		return v;
	}

	/** Próximo valor, voltando ao mínimo depois do máximo. */
	public int next(int v) {
		v = clamp(v);
		int n = v + step;
		return n > max ? min : n;
	}

	/** Valor anterior, indo ao máximo depois do mínimo. */
	public int prev(int v) {
		v = clamp(v);
		int n = v - step;
		return n < min ? max : n;
	}

	public String format(int v) {
		v = clamp(v);
		if (type == Type.BOOL) {
			return v != 0 ? "LIGADO" : "DESLIGADO";
		}
		if (valueLabels != null) {
			return valueLabels[v - min];
		}
		if (v == 0 && zeroLabel != null) {
			return zeroLabel;
		}
		return v + unit;
	}

	/** Texto de ajuda completo (várias linhas separadas por \n). */
	public String tooltip() {
		StringBuilder sb = new StringBuilder(description);
		if (warning != null) {
			sb.append("\nAtenção: ").append(warning);
		}
		if (!independent) {
			sb.append("\nPerfis: Ultra ").append(format(presets[0]));
			sb.append(" | Desempenho ").append(format(presets[1]));
			sb.append(" | Equilibrado ").append(format(presets[2]));
		}
		return sb.toString();
	}
}
