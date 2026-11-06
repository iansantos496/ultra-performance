package com.ultraperformance.core;

/** Abas da tela de configurações. */
public enum Category {
	GERAL("Geral", "Geral"),
	RENDER("Renderização", "Render"),
	PARTICULAS("Partículas", "Partíc."),
	MUNDO("Mundo e CPU", "Mundo"),
	ADAPTATIVO("Ajuste adaptativo", "Adapt."),
	SISTEMA("Sistema e benchmark", "Sistema");

	public final String label;
	public final String shortLabel;

	Category(String label, String shortLabel) {
		this.label = label;
		this.shortLabel = shortLabel;
	}
}
