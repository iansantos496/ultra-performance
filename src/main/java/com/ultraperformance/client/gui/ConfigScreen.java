package com.ultraperformance.client.gui;

import com.ultraperformance.client.ClientRuntime;
import com.ultraperformance.client.VideoSettings;
import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.Category;
import com.ultraperformance.core.HardwareInfo;
import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import com.ultraperformance.core.Opt;
import com.ultraperformance.core.OptionDef;
import com.ultraperformance.core.PerfMonitor;
import com.ultraperformance.core.Profile;
import com.ultraperformance.core.Settings;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela de configuração: perfis no alto, abas por categoria, uma linha por opção (rótulo + controle) com a
 * descrição ao passar o mouse. Toda mudança é aplicada e salva na hora. A aba Sistema mostra o hardware,
 * o estado dos ganchos, o ajuste adaptativo e o benchmark.
 */
public final class ConfigScreen extends Screen {
	private static final Category[] TABS = Category.values();
	private static int lastTab;

	private static final class Hotspot {
		final int x;
		final int y;
		final int w;
		final int h;
		final String text;

		Hotspot(int x, int y, int w, int h, String text) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.text = text;
		}
	}

	private static final class Line {
		final OrderedText text;
		final int color;

		Line(OrderedText text, int color) {
			this.text = text;
			this.color = color;
		}
	}

	private final Screen parent;
	private int tab;
	private int scroll;
	private int maxScroll;
	private boolean rebuildNext;
	private int tickCount;
	private String status = "";
	private int listTop;
	private int listBottom;
	private List<OptionDef> rows = new ArrayList<OptionDef>();
	private final List<Hotspot> hotspots = new ArrayList<Hotspot>();
	private final List<Line> sysLines = new ArrayList<Line>();
	private int textTop;

	public ConfigScreen(Screen parent) {
		super(new LiteralText("Ultra Performance"));
		this.parent = parent;
		this.tab = lastTab;
	}

	@Override
	protected void init() {
		hotspots.clear();
		int w = this.width;

		// perfis
		Profile[] ps = Profile.values();
		int pw = Math.min(150, (w - 24) / ps.length - 4);
		int px = (w - (ps.length * pw + (ps.length - 1) * 4)) / 2;
		for (int i = 0; i < ps.length; i++) {
			final Profile p = ps[i];
			String label = (Settings.profile() == p ? "» " : "") + p.label;
			int x = px + i * (pw + 4);
			this.addButton(new ButtonWidget(x, 22, pw, 20, new LiteralText(label), b -> {
				Settings.applyProfile(p);
				status = p.isPreset() ? "Perfil aplicado: " + p.label + "." : "Perfil Personalizado: seus valores foram mantidos.";
				rebuildNext = true;
			}));
			hotspots.add(new Hotspot(x, 22, pw, 20, p.label + "\n" + p.description));
		}

		// abas
		int n = TABS.length;
		int tw = Math.min(110, (w - 24) / n - 2);
		int tx = (w - (n * tw + (n - 1) * 2)) / 2;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			String label = idx == tab ? "[" + TABS[i].shortLabel + "]" : TABS[i].shortLabel;
			this.addButton(new ButtonWidget(tx + i * (tw + 2), 46, tw, 20, new LiteralText(label), b -> {
				tab = idx;
				lastTab = idx;
				scroll = 0;
				status = "";
				rebuildNext = true;
			}));
			hotspots.add(new Hotspot(tx + i * (tw + 2), 46, tw, 20, TABS[i].label));
		}

		// rodapé
		int by = this.height - 26;
		this.addButton(new ButtonWidget(w / 2 - 154, by, 150, 20, new LiteralText("Restaurar padrões"), b -> {
			Settings.resetDefaults();
			status = "Configurações restauradas para os padrões (perfil Equilibrado).";
			rebuildNext = true;
		}));
		this.addButton(new ButtonWidget(w / 2 + 4, by, 150, 20, ScreenTexts.DONE, b -> this.onClose()));

		listTop = 72;
		listBottom = this.height - 52;
		if (TABS[tab] == Category.SISTEMA) {
			buildSystemTab();
		} else {
			buildOptionRows();
		}
	}

	// ------------------------------------------------------------ abas de opções

	private void buildOptionRows() {
		rows = Opt.byCategory(TABS[tab]);
		int rowH = 24;
		int visible = Math.max(1, (listBottom - listTop) / rowH);
		maxScroll = Math.max(0, rows.size() - visible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int left = this.width / 2 - 170;
		int cx = this.width / 2 + 20;
		for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
			final OptionDef d = rows.get(scroll + i);
			int y = listTop + i * rowH;
			hotspots.add(new Hotspot(left, y, 340, 20, d.label + "\n" + d.tooltip()));
			if (d.type == OptionDef.Type.INT && d.valueLabels == null) {
				this.addButton(new ButtonWidget(cx, y, 20, 20, new LiteralText("-"),
						b -> change(d, Math.max(d.min, Settings.get(d.id) - d.step))));
				this.addButton(new ButtonWidget(cx + 22, y, 106, 20, new LiteralText(d.format(Settings.get(d.id))),
						b -> change(d, d.next(Settings.get(d.id)))));
				this.addButton(new ButtonWidget(cx + 130, y, 20, 20, new LiteralText("+"),
						b -> change(d, Math.min(d.max, Settings.get(d.id) + d.step))));
			} else {
				this.addButton(new ButtonWidget(cx, y, 150, 20, new LiteralText(d.format(Settings.get(d.id))),
						b -> change(d, hasShiftDown() ? d.prev(Settings.get(d.id)) : d.next(Settings.get(d.id)))));
			}
		}
	}

	private void change(OptionDef d, int value) {
		Settings.set(d.id, value);
		rebuildNext = true;
	}

	// ------------------------------------------------------------ aba Sistema

	private void buildSystemTab() {
		int bx = this.width / 2 - 154;
		int y = listTop;
		this.addButton(new ButtonWidget(bx, y, 150, 20, new LiteralText("Benchmark (" + Settings.get(Opt.BENCH_SECONDS) + " s)"), b -> {
			String err = ClientRuntime.startBenchmark(this.client);
			if (err != null) {
				status = err;
			}
		}));
		hotspots.add(new Hotspot(bx, y, 150, 20, "Benchmark\nMede FPS, 1% low, travadas, tick do servidor, CPU, RAM e GC por "
				+ Settings.get(Opt.BENCH_SECONDS) + " s e salva um relatório. Para medir o efeito do mod: rode uma vez, "
				+ "desligue \"Otimizações ativas\" na aba Geral e rode de novo no mesmo lugar."));
		this.addButton(new ButtonWidget(bx + 158, y, 150, 20, new LiteralText("Ajustar vídeo"), b -> {
			Profile p = Settings.profile().isPreset() ? Settings.profile() : HardwareInfo.recommendedProfile();
			List<String> ch = VideoSettings.apply(this.client, p);
			status = ch.isEmpty() ? "O vídeo já está igual ou mais leve que o do perfil " + p.label + "."
					: "Vídeo (perfil " + p.label + "): " + String.join(", ", ch) + ".";
		}));
		hotspots.add(new Hotspot(bx + 158, y, 150, 20, "Ajustar vídeo\nReduz as opções de vídeo do jogo para os valores do "
				+ "perfil atual (ou do recomendado, se for Personalizado). Só diminui; nunca aumenta nada."));
		this.addButton(new ButtonWidget(bx, y + 24, 150, 20, new LiteralText("Copiar diagnóstico"), b -> {
			this.client.keyboard.setClipboard(diagnostics());
			status = "Diagnóstico copiado para a área de transferência.";
		}));
		this.addButton(new ButtonWidget(bx + 158, y + 24, 150, 20, new LiteralText("Copiar relatório"), b -> {
			Benchmark.Result r = Benchmark.last();
			if (r == null) {
				status = "Ainda não há relatório de benchmark.";
			} else {
				this.client.keyboard.setClipboard(r.report);
				status = "Relatório do último benchmark copiado.";
			}
		}));
		textTop = y + 52;
		collectSystemLines();
		int visible = Math.max(1, (listBottom - textTop) / 11);
		maxScroll = Math.max(0, sysLines.size() - visible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	private void addWrapped(String text, int color) {
		boolean header = text.startsWith("## ");
		String s = header ? text.substring(3) : text;
		for (OrderedText o : this.textRenderer.wrapLines(new LiteralText(s), this.width - 40)) {
			sysLines.add(new Line(o, header ? 0xFFFF55 : color));
		}
	}

	private void collectSystemLines() {
		sysLines.clear();
		addWrapped("## Hardware", 0xFFFFFF);
		for (String s : HardwareInfo.summaryLines()) {
			addWrapped(s, 0xDDDDDD);
		}
		long start = PerfMonitor.clientStartedMs();
		if (start >= 0L) {
			addWrapped("Do início da JVM até o menu principal: " + (start / 100L) / 10.0D + " s (referência; muda de PC para PC)", 0xDDDDDD);
		}
		addWrapped("## Ajuste adaptativo", 0xFFFFFF);
		addWrapped("Nível atual: " + Live.adaptiveLevel + " | mudanças nesta sessão: " + ClientRuntime.adaptive().changes()
				+ (ClientRuntime.adaptive().saturated() ? " | FPS abaixo do alvo mesmo no nível máximo" : ""), 0xDDDDDD);
		if (!ClientRuntime.adaptive().reason().isEmpty()) {
			addWrapped("Última mudança: " + ClientRuntime.adaptive().reason(), 0xDDDDDD);
		}
		addWrapped("## Ganchos (otimizações que realmente mexem no jogo)", 0xFFFFFF);
		for (int i = 0; i < HookStatus.COUNT; i++) {
			addWrapped(HookStatus.line(i), HookStatus.isFailed(i) ? 0xFF5555 : 0xDDDDDD);
		}
		if (ClientRuntime.tickError() != null) {
			addWrapped("Ciclo do cliente desativado por erro: " + ClientRuntime.tickError(), 0xFF5555);
		}
		addWrapped("## Último benchmark", 0xFFFFFF);
		List<String> b = ClientRuntime.lastBenchmarkLines();
		if (b.isEmpty()) {
			addWrapped("Nenhum ainda nesta sessão. Relatórios ficam em .minecraft/ultraperformance/benchmarks.", 0xDDDDDD);
		}
		for (String s : b) {
			addWrapped(s, 0xDDDDDD);
		}
		if (!Benchmark.lastAbortReason().isEmpty()) {
			addWrapped("Último cancelamento: " + Benchmark.lastAbortReason(), 0xDDDDDD);
		}
	}

	private String diagnostics() {
		StringBuilder sb = new StringBuilder("Ultra Performance - diagnóstico\n");
		for (String s : HardwareInfo.summaryLines()) {
			sb.append(s).append('\n');
		}
		sb.append("Perfil: ").append(Settings.profile().label).append(" | nível adaptativo ").append(Live.adaptiveLevel).append('\n');
		for (String s : HookStatus.lines()) {
			sb.append(s).append('\n');
		}
		if (ClientRuntime.tickError() != null) {
			sb.append("Erro no ciclo do cliente: ").append(ClientRuntime.tickError()).append('\n');
		}
		sb.append(Settings.dump());
		return sb.toString();
	}

	// ------------------------------------------------------------ ciclo da tela

	@Override
	public void tick() {
		tickCount++;
		if (rebuildNext || (TABS[tab] == Category.SISTEMA && tickCount % 10 == 0)) {
			rebuildNext = false;
			this.init(this.client, this.width, this.height);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int s = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(amount)));
		if (s != scroll) {
			scroll = s;
			rebuildNext = true;
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 344) { // Shift direito de novo fecha a tela
			this.onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		this.client.openScreen(parent);
	}

	@Override
	public void removed() {
		Settings.flush();
	}

	private List<OrderedText> wrap(String text, int width) {
		List<OrderedText> out = new ArrayList<OrderedText>();
		for (String part : text.split("\n")) {
			out.addAll(this.textRenderer.wrapLines(new LiteralText(part), width));
		}
		return out;
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

		if (TABS[tab] == Category.SISTEMA) {
			int visible = Math.max(1, (listBottom - textTop) / 11);
			for (int i = 0; i < visible && scroll + i < sysLines.size(); i++) {
				Line l = sysLines.get(scroll + i);
				this.textRenderer.drawWithShadow(matrices, l.text, 20, textTop + i * 11, l.color);
			}
		} else {
			int left = this.width / 2 - 170;
			int visible = Math.max(1, (listBottom - listTop) / 24);
			for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
				this.textRenderer.drawWithShadow(matrices, rows.get(scroll + i).label, left, listTop + i * 24 + 6, 0xFFFFFF);
			}
		}
		if (maxScroll > 0) {
			drawCenteredText(matrices, this.textRenderer, new LiteralText("(role o mouse para ver mais)"), this.width / 2,
					listBottom + 2, 0x888888);
		}
		if (!status.isEmpty()) {
			List<OrderedText> st = wrap(status, this.width - 40);
			for (int i = 0; i < Math.min(2, st.size()); i++) {
				this.textRenderer.drawWithShadow(matrices, st.get(i), 20, listBottom + 12 + i * 10, 0x55FF55);
			}
		}

		super.render(matrices, mouseX, mouseY, delta);

		for (Hotspot h : hotspots) {
			if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
				this.renderOrderedTooltip(matrices, wrap(h.text, 240), mouseX, mouseY);
				break;
			}
		}
	}
}
