import com.ultraperformance.core.AdaptiveController;
import com.ultraperformance.core.Benchmark;
import com.ultraperformance.core.Category;
import com.ultraperformance.core.HardwareInfo;
import com.ultraperformance.core.HookStatus;
import com.ultraperformance.core.Live;
import com.ultraperformance.core.MiniJson;
import com.ultraperformance.core.Opt;
import com.ultraperformance.core.OptionDef;
import com.ultraperformance.core.PerfMonitor;
import com.ultraperformance.core.Profile;
import com.ultraperformance.core.Settings;
import com.ultraperformance.core.SystemProbe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

/**
 * Autoteste do núcleo (tudo que não depende do Minecraft): JSON, opções, perfis, configuração,
 * valores efetivos, ajuste adaptativo, monitor de quadros, hardware, diagnóstico dos ganchos e benchmark.
 * Rode com dev/run-selftest.sh. Isto NÃO testa os mixins nem o jogo: eles só podem ser testados dentro do Minecraft.
 */
public final class CoreSelfTest {
	private static int passed;
	private static int failed;

	private static void check(boolean cond, String what) {
		if (cond) {
			passed++;
		} else {
			failed++;
			System.out.println("  FALHOU: " + what);
		}
	}

	private static void eq(Object expected, Object actual, String what) {
		boolean ok = expected == null ? actual == null : expected.equals(actual);
		if (!ok) {
			System.out.println("  FALHOU: " + what + " (esperado " + expected + ", veio " + actual + ")");
			failed++;
		} else {
			passed++;
		}
	}

	private static void near(double expected, double actual, double tol, String what) {
		if (Math.abs(expected - actual) <= tol) {
			passed++;
		} else {
			failed++;
			System.out.println("  FALHOU: " + what + " (esperado " + expected + " +-" + tol + ", veio " + actual + ")");
		}
	}

	private static void section(String name) {
		System.out.println("[" + name + "]");
	}

	public static void main(String[] args) throws Exception {
		testOptions();
		testJson();
		testSettings();
		testPersistence();
		testLive();
		testAdaptiveRules();
		testAdaptiveSimulations();
		testPerfMonitor();
		testHardware();
		testHookStatus();
		testBenchmark();
		testSystemProbe();
		System.out.println();
		System.out.println(passed + " verificações passaram, " + failed + " falharam");
		System.exit(failed == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ opções

	private static void testOptions() throws Exception {
		section("opções");
		Set<String> ids = new HashSet<String>();
		for (OptionDef d : Opt.all()) {
			check(ids.add(d.id), "id repetido: " + d.id);
			check(d.label != null && !d.label.isEmpty(), "sem rótulo: " + d.id);
			check(d.description != null && d.description.length() > 20, "descrição curta demais: " + d.id);
			check(d.tooltip().length() >= d.description.length(), "tooltip: " + d.id);
			for (Profile p : Profile.values()) {
				int v = d.defaultFor(p);
				check(v >= d.min && v <= d.max, "padrão fora dos limites: " + d.id + " " + p);
				check(d.clamp(v) == v, "padrão não é estável sob clamp: " + d.id + " " + p + " " + v);
			}
			// next/prev percorrem todos os valores sem sair dos limites e sem travar
			int v = d.min;
			Set<Integer> seen = new HashSet<Integer>();
			for (int i = 0; i < 400 && seen.add(v); i++) {
				check(v >= d.min && v <= d.max, "next fora dos limites: " + d.id);
				check(d.format(v) != null && !d.format(v).isEmpty(), "format vazio: " + d.id);
				v = d.next(v);
			}
			check(d.prev(d.min) == d.max || d.type == OptionDef.Type.BOOL || d.step > 1 || d.prev(d.min) >= d.min,
					"prev do mínimo: " + d.id);
		}
		// cada constante de Opt tem uma definição
		int constants = 0;
		for (Field f : Opt.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
				constants++;
				check(Opt.get((String) f.get(null)) != null, "constante sem definição: " + f.getName());
			}
		}
		eq(Integer.valueOf(Opt.all().size()), Integer.valueOf(constants), "uma constante por opção");
		int sum = 0;
		for (Category c : Category.values()) {
			sum += Opt.byCategory(c).size();
		}
		eq(Integer.valueOf(Opt.all().size()), Integer.valueOf(sum), "todas as opções estão em alguma aba");
		eq("Sem limite", Opt.get(Opt.PARTICLE_CAP).format(0), "rótulo do zero");
		eq("LIGADO", Opt.get(Opt.ENABLED).format(1), "rótulo booleano");
		eq(Integer.valueOf(128), Integer.valueOf(Opt.get(Opt.ENTITY_DIST).clamp(9999)), "clamp máximo");
		eq(Integer.valueOf(16), Integer.valueOf(Opt.get(Opt.ENTITY_DIST).clamp(-3)), "clamp mínimo");
		check(Profile.parse("desempenho", Profile.EQUILIBRADO) == Profile.DESEMPENHO, "Profile.parse");
		check(Profile.parse("???", Profile.EQUILIBRADO) == Profile.EQUILIBRADO, "Profile.parse inválido");
		check(Profile.ULTRA_ECONOMICO.ordinal() == 0 && Profile.DESEMPENHO.ordinal() == 1
				&& Profile.EQUILIBRADO.ordinal() == 2, "ordem dos perfis (usada nas tabelas)");
	}

	// ------------------------------------------------------------------ JSON

	private static void testJson() {
		section("json");
		String src = "{\"a\":1,\"b\":[true,false,null,-2.5e2,\"x\\n\\\"y\\\" \\u00e9 \\u4e2d\"],\"c\":{\"d\":{}},\"e\":[]}";
		Object o = MiniJson.parse(src);
		check(o instanceof Map, "raiz é objeto");
		Map<?, ?> m = (Map<?, ?>) o;
		eq(Long.valueOf(1L), m.get("a"), "inteiro");
		List<?> b = (List<?>) m.get("b");
		eq(Boolean.TRUE, b.get(0), "true");
		eq(Boolean.FALSE, b.get(1), "false");
		check(b.get(2) == null, "null");
		near(-250.0D, ((Number) b.get(3)).doubleValue(), 1e-9, "número com expoente");
		eq("x\n\"y\" \u00e9 \u4e2d", b.get(4), "escapes e unicode");
		String written = MiniJson.write(o);
		Object again = MiniJson.parse(written);
		eq(MiniJson.write(o), MiniJson.write(again), "ida e volta estável");

		Map<String, Object> w = new LinkedHashMap<String, Object>();
		w.put("texto", "linha1\nlinha2\t\"aspas\" \\ barra");
		w.put("n", Integer.valueOf(-7));
		w.put("ok", Boolean.TRUE);
		Map<?, ?> back = (Map<?, ?>) MiniJson.parse(MiniJson.write(w));
		eq("linha1\nlinha2\t\"aspas\" \\ barra", back.get("texto"), "escrita de strings");
		eq(Long.valueOf(-7L), back.get("n"), "escrita de inteiros");

		String[] bad = {"", "{", "{\"a\":}", "[1,2", "{\"a\":1,}", "nul", "{\"a\":\"\\q\"}", "{} lixo", "[1 2]", "{\"a\" 1}"};
		for (String s : bad) {
			boolean threw = false;
			try {
				MiniJson.parse(s);
			} catch (RuntimeException e) {
				threw = true;
			}
			check(threw, "JSON inválido deveria falhar: '" + s + "'");
		}
	}

	// ------------------------------------------------------------------ configurações

	private static void testSettings() {
		section("configurações e perfis");
		Settings.resetDefaults();
		check(Settings.profile() == Profile.EQUILIBRADO, "perfil inicial");
		check(Settings.matches(Profile.EQUILIBRADO), "valores iniciais = Equilibrado");
		check(!Settings.matches(Profile.PERSONALIZADO), "Personalizado nunca 'casa'");

		Settings.applyProfile(Profile.ULTRA_ECONOMICO);
		check(Settings.profile() == Profile.ULTRA_ECONOMICO, "aplicou Ultra");
		eq(Integer.valueOf(32), Integer.valueOf(Settings.get(Opt.ENTITY_DIST)), "Ultra: distância das entidades");
		eq(Integer.valueOf(500), Integer.valueOf(Settings.get(Opt.PARTICLE_CAP)), "Ultra: limite de partículas");
		eq(Integer.valueOf(3), Integer.valueOf(Settings.get(Opt.TEX_ANIM)), "Ultra: texturas paradas");
		check(Settings.bool(Opt.ADAPTIVE), "Ultra liga o adaptativo");

		Settings.set(Opt.OVERLAY, 2);
		check(Settings.profile() == Profile.ULTRA_ECONOMICO, "opção independente não muda o perfil");
		Settings.applyProfile(Profile.DESEMPENHO);
		eq(Integer.valueOf(2), Integer.valueOf(Settings.get(Opt.OVERLAY)), "perfil não mexe nas opções independentes");
		eq(Integer.valueOf(1000), Integer.valueOf(Settings.get(Opt.PARTICLE_CAP)), "Desempenho: partículas");

		Settings.set(Opt.ENTITY_DIST, 40);
		check(Settings.profile() == Profile.PERSONALIZADO, "alterar uma opção vira Personalizado");
		Settings.applyProfile(Profile.PERSONALIZADO);
		eq(Integer.valueOf(40), Integer.valueOf(Settings.get(Opt.ENTITY_DIST)), "Personalizado mantém os valores");

		Settings.set(Opt.ENTITY_DIST, 9999);
		eq(Integer.valueOf(128), Integer.valueOf(Settings.get(Opt.ENTITY_DIST)), "set limita ao máximo");
		Settings.set(Opt.PARTICLE_CAP, -5);
		eq(Integer.valueOf(0), Integer.valueOf(Settings.get(Opt.PARTICLE_CAP)), "set limita ao mínimo");
		Settings.set("nao_existe", 5);
		eq(Integer.valueOf(0), Integer.valueOf(Settings.get("nao_existe")), "id desconhecido é ignorado");

		Settings.applyProfile(Profile.EQUILIBRADO);
		Settings.set(Opt.ENABLED, 0);
		check(Settings.profile() == Profile.EQUILIBRADO, "a chave geral não altera o perfil");

		Settings.resetDefaults();
		check(Settings.bool(Opt.ENABLED), "restaurar padrão religa o mod");
		check(Settings.profile() == Profile.EQUILIBRADO && Settings.matches(Profile.EQUILIBRADO), "restaurar padrão");
		eq(Integer.valueOf(0), Integer.valueOf(Settings.get(Opt.OVERLAY)), "restaurar padrão: overlay");
	}

	private static void testPersistence() throws Exception {
		section("persistência");
		Path dir = Files.createTempDirectory("up-selftest-cfg");
		Path cfg = dir.resolve("sub").resolve("ultraperformance.json");

		Settings.resetDefaults();
		Settings.init(cfg);
		check(Settings.isFirstRun(), "primeira execução sem arquivo");
		Settings.applyProfile(Profile.DESEMPENHO);
		Settings.set(Opt.PARTICLE_CAP, 1250);
		Settings.set(Opt.OVERLAY, 3);
		Settings.markFirstRunHandled();
		Settings.save();
		check(Files.exists(cfg), "arquivo criado (inclusive a pasta)");
		check(!Files.exists(cfg.resolveSibling("ultraperformance.json.tmp")), "sem arquivo temporário sobrando");

		Settings.resetDefaults();
		Settings.init(cfg);
		check(!Settings.isFirstRun(), "segunda execução");
		eq(Integer.valueOf(1250), Integer.valueOf(Settings.get(Opt.PARTICLE_CAP)), "valor salvo volta");
		eq(Integer.valueOf(3), Integer.valueOf(Settings.get(Opt.OVERLAY)), "opção independente volta");
		check(Settings.profile() == Profile.PERSONALIZADO, "perfil vira Personalizado se os valores divergem");

		Settings.applyProfile(Profile.ULTRA_ECONOMICO);
		Settings.save();
		Settings.resetDefaults();
		Settings.init(cfg);
		check(Settings.profile() == Profile.ULTRA_ECONOMICO, "perfil puro volta como perfil");

		// arquivo quebrado: guardado como .broken e padrões restaurados
		Files.write(cfg, "{ isto nao e json".getBytes(StandardCharsets.UTF_8));
		Settings.init(cfg);
		check(Files.exists(cfg.resolveSibling("ultraperformance.json.broken")), "arquivo quebrado preservado");
		check(Settings.profile() == Profile.EQUILIBRADO && Settings.isFirstRun(), "padrões após arquivo quebrado");

		Files.write(cfg, new byte[0]);
		Settings.init(cfg);
		check(Settings.profile() == Profile.EQUILIBRADO, "arquivo vazio não derruba o jogo");
		Files.write(cfg, "[1,2,3]".getBytes(StandardCharsets.UTF_8));
		Settings.init(cfg);
		check(Settings.profile() == Profile.EQUILIBRADO, "raiz que não é objeto");

		// valores fora dos limites, chaves desconhecidas, tipos trocados
		String odd = "{\"profile\":\"DESEMPENHO\",\"values\":{\"entityCullDistance\":99999,\"weatherOff\":true,"
				+ "\"zzz\":5,\"particleCap\":\"texto\",\"aiThrottle\":1.0}}";
		Files.write(cfg, odd.getBytes(StandardCharsets.UTF_8));
		Settings.init(cfg);
		eq(Integer.valueOf(128), Integer.valueOf(Settings.get(Opt.ENTITY_DIST)), "valor enorme é limitado");
		check(Settings.bool(Opt.WEATHER_OFF), "booleano lido");
		eq(Integer.valueOf(2000), Integer.valueOf(Settings.get(Opt.PARTICLE_CAP)), "texto no lugar de número é ignorado");
		eq(Integer.valueOf(1), Integer.valueOf(Settings.get(Opt.AI_THROTTLE)), "número decimal lido como inteiro");

		Settings.resetDefaults();
		deleteTree(dir);
	}

	// ------------------------------------------------------------------ valores efetivos

	private static void testLive() {
		section("valores efetivos (Live)");
		Live.adaptiveLevel = 0;

		Settings.resetDefaults();
		Settings.applyProfile(Profile.ULTRA_ECONOMICO);
		check(Live.master && Live.entityCull && Live.blockEntityCull && Live.weatherOff, "Ultra: chaves ligadas");
		near(32.0D, Live.entityMaxBlocks, 1e-9, "Ultra: entidades 32 blocos");
		near(16.0D * 16.0D, Live.entityNearSq, 1e-9, "Ultra: metade da distância ao quadrado");
		near(32.0D * 32.0D, Live.blockEntityMaxSq, 1e-9, "Ultra: blocos animados");
		eq(Integer.valueOf(0), Integer.valueOf(Live.animInterval), "Ultra: animação parada");
		eq(Long.valueOf(2000000L), Long.valueOf(Live.uploadBudgetNs), "Ultra: orçamento de envio");
		eq(Integer.valueOf(10), Integer.valueOf(Live.unfocusedFps), "Ultra: FPS sem foco");
		eq(Integer.valueOf(500), Integer.valueOf(Live.particleCap), "Ultra: limite de partículas");
		eq(Integer.valueOf(40), Integer.valueOf(Live.particleKeep), "Ultra: densidade");
		check(Live.gcOnDisconnect, "Ultra: GC ao sair");
		eq(Integer.valueOf(4), Integer.valueOf(Live.aiInterval), "Ultra: IA a cada 4 ticks");
		near(48.0D * 48.0D, Live.aiFarSq, 1e-9, "Ultra: distância da IA");

		Settings.applyProfile(Profile.DESEMPENHO);
		near(48.0D, Live.entityMaxBlocks, 1e-9, "Desempenho: entidades");
		check(Live.blockEntityCull && !Live.weatherOff, "Desempenho: blocos animados sim, clima não");
		eq(Integer.valueOf(4), Integer.valueOf(Live.animInterval), "Desempenho: animação 1/4");
		eq(Integer.valueOf(2), Integer.valueOf(Live.aiInterval), "Desempenho: IA a cada 2 ticks");

		Settings.applyProfile(Profile.EQUILIBRADO);
		near(80.0D, Live.entityMaxBlocks, 1e-9, "Equilibrado: entidades");
		check(!Live.blockEntityCull && !Live.weatherOff, "Equilibrado: sem ocultar blocos nem clima");
		eq(Integer.valueOf(2), Integer.valueOf(Live.animInterval), "Equilibrado: animação 1/2");
		eq(Integer.valueOf(1), Integer.valueOf(Live.aiInterval), "Equilibrado: IA normal");
		eq(Long.valueOf(0L), Long.valueOf(Live.uploadBudgetNs), "Equilibrado: sem orçamento de envio");
		eq(Integer.valueOf(100), Integer.valueOf(Live.particleKeep), "Equilibrado: densidade 100%");

		// adaptativo desligado ignora o nível
		Live.adaptiveLevel = 3;
		Live.rebuild();
		check(!Live.weatherOff && !Live.blockEntityCull, "nível adaptativo é ignorado com o ajuste desligado");

		// adaptativo ligado: níveis
		Settings.set(Opt.ADAPTIVE, 1);
		Live.adaptiveLevel = 1;
		Live.rebuild();
		near(64.0D, Live.entityMaxBlocks, 1e-9, "nível 1: 80% da distância");
		eq(Integer.valueOf(1400), Integer.valueOf(Live.particleCap), "nível 1: limite de partículas 70%");
		eq(Integer.valueOf(4), Integer.valueOf(Live.animInterval), "nível 1: animação um passo mais lenta");
		check(!Live.blockEntityCull && !Live.weatherOff, "nível 1 ainda não oculta blocos nem clima");
		Live.adaptiveLevel = 2;
		Live.rebuild();
		check(Live.blockEntityCull, "nível 2 liga o corte de blocos animados");
		near(Math.pow(64.0D * 0.62D, 2.0D), Live.blockEntityMaxSq, 1e-6, "nível 2: distância dos blocos");
		Live.adaptiveLevel = 3;
		Live.rebuild();
		check(Live.weatherOff, "nível 3 desliga o clima visual");
		eq(Integer.valueOf(0), Integer.valueOf(Live.animInterval), "nível 3: animação parada");
		check(Live.particleKeep < 100, "nível 3: densidade menor");
		eq(Integer.valueOf(1), Integer.valueOf(Live.aiInterval), "o adaptativo nunca liga a redução de IA");

		// sem limite de partículas + adaptativo: coloca um teto
		Settings.set(Opt.PARTICLE_CAP, 0);
		Live.adaptiveLevel = 1;
		Live.rebuild();
		eq(Integer.valueOf(2100), Integer.valueOf(Live.particleCap), "sem limite + nível 1 vira teto de 3000 x 70%");
		Live.adaptiveLevel = 0;
		Live.rebuild();
		eq(Integer.valueOf(0), Integer.valueOf(Live.particleCap), "sem limite no nível 0");

		// chave geral desligada: tudo neutro
		Settings.applyProfile(Profile.ULTRA_ECONOMICO);
		Live.adaptiveLevel = 3;
		Settings.set(Opt.ENABLED, 0);
		check(!Live.master && !Live.entityCull && !Live.blockEntityCull && !Live.weatherOff && !Live.gcOnDisconnect,
				"chave geral desligada: sem ocultação");
		eq(Integer.valueOf(1), Integer.valueOf(Live.animInterval), "chave geral desligada: animação normal");
		eq(Long.valueOf(0L), Long.valueOf(Live.uploadBudgetNs), "chave geral desligada: sem orçamento");
		eq(Integer.valueOf(0), Integer.valueOf(Live.unfocusedFps), "chave geral desligada: sem limite sem foco");
		eq(Integer.valueOf(0), Integer.valueOf(Live.particleCap), "chave geral desligada: sem limite de partículas");
		eq(Integer.valueOf(100), Integer.valueOf(Live.particleKeep), "chave geral desligada: densidade 100%");
		eq(Integer.valueOf(1), Integer.valueOf(Live.aiInterval), "chave geral desligada: IA normal");

		Live.adaptiveLevel = 0;
		Settings.resetDefaults();

		// gerador pseudoaleatório 0..99
		int[] hist = new int[100];
		for (int i = 0; i < 100000; i++) {
			int v = Live.nextPercent();
			check(v >= 0 && v < 100, "nextPercent fora de 0..99: " + v);
			hist[v]++;
		}
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int h : hist) {
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		check(min > 700 && max < 1300, "nextPercent razoavelmente uniforme (min " + min + ", max " + max + ")");
	}

	// ------------------------------------------------------------------ adaptativo

	/** Simulador: o FPS depende só do nível; um passo de relógio = uma avaliação. */
	private static final class Sim {
		final AdaptiveController c = new AdaptiveController();
		long t;
		int target = 30;
		int maxLevel = 3;
		double[] fpsByLevel = {30, 30, 30, 30};
		double low1Ratio = 0.7D;
		final List<long[]> changes = new ArrayList<long[]>();
		long badSeconds;

		void run(long seconds) {
			long end = t + seconds * 1000L;
			while (t < end) {
				t += AdaptiveController.EVAL_MS;
				if (!c.due(t)) {
					continue;
				}
				double fps = fpsByLevel[c.level()];
				if (fps < target * AdaptiveController.BAD_RATIO) {
					badSeconds += AdaptiveController.EVAL_MS / 1000L;
				}
				int d = c.evaluate(t, fps, fps * low1Ratio, 300, target, maxLevel);
				if (d != 0) {
					changes.add(new long[] {t / 1000L, c.level()});
				}
			}
		}

		int changesAfter(long second) {
			int n = 0;
			for (long[] ch : changes) {
				if (ch[0] >= second) {
					n++;
				}
			}
			return n;
		}
	}

	private static void testAdaptiveRules() {
		section("ajuste adaptativo: regras");

		// aquecimento e reduções escalonadas
		Sim s = new Sim();
		s.fpsByLevel = new double[] {10, 10, 10, 10};
		s.run(15);
		eq(Integer.valueOf(0), Integer.valueOf(s.changes.size()), "nada acontece durante o aquecimento");
		s.run(5);
		eq(Integer.valueOf(1), Integer.valueOf(s.changes.size()), "primeira redução ao fim do aquecimento");
		eq(Long.valueOf(20L), Long.valueOf(s.changes.get(0)[0]), "primeira redução em 20 s");
		s.run(60);
		eq(Integer.valueOf(3), Integer.valueOf(s.c.level()), "chega ao nível 3");
		eq(Integer.valueOf(3), Integer.valueOf(s.changes.size()), "uma mudança por vez, nada além do nível máximo");
		eq(Long.valueOf(35L), Long.valueOf(s.changes.get(1)[0]), "segunda redução 15 s depois");
		eq(Long.valueOf(50L), Long.valueOf(s.changes.get(2)[0]), "terceira redução 15 s depois");
		check(s.c.saturated(), "saturado: ruim mesmo no nível máximo");
		check(s.c.reason().contains("nível"), "motivo registrado");

		// nível máximo respeitado
		s = new Sim();
		s.maxLevel = 2;
		s.fpsByLevel = new double[] {10, 10, 10, 10};
		s.run(200);
		eq(Integer.valueOf(2), Integer.valueOf(s.c.level()), "respeita o nível máximo");
		// diminuir o máximo com o ajuste num nível alto
		AdaptiveController c = new AdaptiveController();
		c.setLevel(3, 100000L);
		int d = c.evaluate(105000L, 60, 50, 300, 30, 1);
		eq(Integer.valueOf(1), Integer.valueOf(c.level()), "limite reduzido derruba o nível na hora");
		eq(Integer.valueOf(-1), Integer.valueOf(d), "e conta como recuo");

		// recuo exige sequência de avaliações boas
		s = new Sim();
		s.c.setLevel(3, 0L);
		s.fpsByLevel = new double[] {40, 40, 40, 40};
		s.run(200);
		eq(Integer.valueOf(0), Integer.valueOf(s.c.level()), "recua até o nível 0 com folga");
		eq(Integer.valueOf(3), Integer.valueOf(s.changes.size()), "três recuos");
		// 20 s de aquecimento + 6 avaliações boas (5 s cada) = primeiro recuo em 45 s; os seguintes, 35 s depois
		eq(Long.valueOf(45L), Long.valueOf(s.changes.get(0)[0]), "primeiro recuo só depois do aquecimento e de 6 avaliações boas");
		eq(Long.valueOf(80L), Long.valueOf(s.changes.get(1)[0]), "recuos espaçados de 35 s");
		eq(Long.valueOf(115L), Long.valueOf(s.changes.get(2)[0]), "terceiro recuo");

		// faixa neutra: nada muda
		s = new Sim();
		s.c.setLevel(2, 0L);
		s.fpsByLevel = new double[] {28.5D, 28.5D, 28.5D, 28.5D}; // 95% do alvo
		s.run(1800);
		eq(Integer.valueOf(0), Integer.valueOf(s.changes.size()), "faixa neutra (95% do alvo) não faz nada");

		// 1% low ruim abaixo do alvo conta como ruim; acima do alvo com low1 ruim fica neutro
		s = new Sim();
		s.low1Ratio = 0.3D;
		s.fpsByLevel = new double[] {29, 29, 29, 29};
		s.run(60);
		check(s.c.level() >= 1, "FPS perto do alvo com 1% low péssimo reduz");
		s = new Sim();
		s.low1Ratio = 0.3D;
		s.fpsByLevel = new double[] {45, 45, 45, 45};
		s.c.setLevel(2, 0L);
		s.run(600);
		eq(Integer.valueOf(2), Integer.valueOf(s.c.level()), "FPS alto com 1% low péssimo não recua nem reduz");

		// holdOff
		s = new Sim();
		s.fpsByLevel = new double[] {10, 10, 10, 10};
		s.t = 100000L;
		s.c.reset(100000L);
		s.c.holdOff(100000L, 60000L);
		s.run(55);
		eq(Integer.valueOf(0), Integer.valueOf(s.changes.size()), "holdOff bloqueia decisões");
		s.run(30);
		check(s.changes.size() >= 1, "depois do holdOff volta a decidir");

		// poucos quadros / alvo inválido
		c = new AdaptiveController();
		eq(Integer.valueOf(0), Integer.valueOf(c.evaluate(60000L, 5, 2, 10, 30, 3)), "poucos quadros: sem decisão");
		eq(Integer.valueOf(0), Integer.valueOf(c.evaluate(70000L, 5, 2, 300, 0, 3)), "alvo inválido: sem decisão");
		check(!c.due(72000L) && c.due(75000L), "due() respeita o intervalo de 5 s");

		// reset zera o estado
		c = new AdaptiveController();
		c.setLevel(2, 1000L);
		c.reset(5000L);
		eq(Integer.valueOf(0), Integer.valueOf(c.level()), "reset volta ao nível 0");
		eq(Integer.valueOf(AdaptiveController.BASE_UP_WAIT), Integer.valueOf(c.upWait()), "reset restaura a espera");
	}

	private static void testAdaptiveSimulations() {
		section("ajuste adaptativo: simulações de 30 min (anti-oscilação)");
		final long thirtyMin = 1800L;

		// FPS exatamente no alvo em qualquer nível: nenhuma mudança
		Sim s = new Sim();
		s.run(thirtyMin);
		eq(Integer.valueOf(0), Integer.valueOf(s.changes.size()), "FPS = alvo: zero mudanças");

		// FPS oscilando ao acaso dentro da faixa neutra: nenhuma mudança
		s = new Sim();
		Random rnd = new Random(42L);
		int changes = 0;
		long t = 0L;
		AdaptiveController c = new AdaptiveController();
		c.setLevel(1, 0L);
		while (t < thirtyMin * 1000L) {
			t += AdaptiveController.EVAL_MS;
			double fps = 30.0D * (0.93D + rnd.nextDouble() * 0.15D); // 93% a 108% do alvo
			changes += Math.abs(c.evaluate(t, fps, fps * 0.6D, 300, 30, 3));
		}
		eq(Integer.valueOf(0), Integer.valueOf(changes), "ruído dentro da faixa neutra: zero mudanças");

		// cena pesada em que o nível 0 é ruim e o nível 1 resolve com folga
		s = new Sim();
		s.fpsByLevel = new double[] {25, 40, 45, 50};
		s.run(thirtyMin);
		check(s.changes.size() <= 12, "cena pesada: no máximo 12 mudanças em 30 min (foram " + s.changes.size() + ")");
		check(s.changesAfter(1000L) <= 4, "cena pesada: no máximo 4 mudanças nos últimos 13 min (foram "
				+ s.changesAfter(1000L) + ")");
		check(s.badSeconds * 100L / thirtyMin < 8L, "cena pesada: menos de 8% do tempo abaixo de 90% do alvo (foi "
				+ (s.badSeconds * 100L / thirtyMin) + "%)");
		eq(Integer.valueOf(AdaptiveController.MAX_UP_WAIT), Integer.valueOf(s.c.upWait()),
				"a espera para recuar cresce até o máximo");
		long minGap = Long.MAX_VALUE;
		for (int i = 1; i < s.changes.size(); i++) {
			minGap = Math.min(minGap, s.changes.get(i)[0] - s.changes.get(i - 1)[0]);
		}
		check(minGap >= 15L, "nunca duas mudanças com menos de 15 s de intervalo (mínimo " + minGap + " s)");

		// o nível 1 deixa o FPS na faixa neutra: estabiliza e nunca mais mexe
		s = new Sim();
		s.fpsByLevel = new double[] {25, 30, 36, 45};
		s.run(thirtyMin);
		eq(Integer.valueOf(1), Integer.valueOf(s.c.level()), "estabiliza no nível 1");
		eq(Integer.valueOf(1), Integer.valueOf(s.changes.size()), "uma única mudança em 30 min");

		// carga some depois de um tempo: o controle volta a 0
		s = new Sim();
		s.fpsByLevel = new double[] {20, 25, 28, 32};
		s.run(300);
		check(s.c.level() >= 2, "sob carga pesada sobe de nível");
		s.fpsByLevel = new double[] {60, 60, 60, 60};
		s.run(600);
		eq(Integer.valueOf(0), Integer.valueOf(s.c.level()), "com folga volta ao nível 0");

		// carga extrema: nunca passa do máximo, nunca anda para trás sozinho
		s = new Sim();
		s.fpsByLevel = new double[] {3, 3, 3, 3};
		s.run(thirtyMin);
		eq(Integer.valueOf(3), Integer.valueOf(s.c.level()), "carga extrema: fica no máximo");
		eq(Integer.valueOf(3), Integer.valueOf(s.changes.size()), "carga extrema: só três mudanças");
	}

	// ------------------------------------------------------------------ monitor

	private static void testPerfMonitor() {
		section("monitor de desempenho");
		long[] ns = new long[100];
		for (int i = 0; i < ns.length; i++) {
			ns[i] = 16666667L;
		}
		PerfMonitor.FrameStats st = PerfMonitor.statsOf(ns, ns.length);
		eq(Integer.valueOf(100), Integer.valueOf(st.frames), "quadros");
		near(60.0D, st.avgFps, 0.01D, "FPS médio de quadros iguais");
		near(60.0D, st.low1Fps, 0.01D, "1% low de quadros iguais");
		near(16.67D, st.medianMs, 0.01D, "mediana");
		eq(Integer.valueOf(0), Integer.valueOf(st.stutters), "sem travadas");

		ns[50] = 200000000L;
		st = PerfMonitor.statsOf(ns, ns.length);
		eq(Integer.valueOf(1), Integer.valueOf(st.stutters), "uma travada");
		near(200.0D, st.maxMs, 1e-6D, "pior quadro");
		near(5.0D, st.minFps, 0.01D, "FPS do pior quadro");
		near(5.0D, st.low1Fps, 0.01D, "1% low com 100 quadros = pior quadro");
		near(1000.0D / 18.5D, st.avgFps, 0.01D, "média puxada para baixo pela travada (99 x 16,67 ms + 200 ms)");
		check(st.p99Ms >= 16.0D, "p99");

		// limiar de travada: 33 ms ou 2x a mediana, o que for maior
		long[] slow = new long[100];
		for (int i = 0; i < slow.length; i++) {
			slow[i] = 50000000L; // 20 FPS constante
		}
		slow[10] = 90000000L; // 90 ms < 2x50 = 100 ms: não é travada
		slow[11] = 120000000L; // 120 ms > 100 ms: travada
		st = PerfMonitor.statsOf(slow, slow.length);
		eq(Integer.valueOf(1), Integer.valueOf(st.stutters), "travada relativa à mediana");

		st = PerfMonitor.statsOf(new long[0], 0);
		eq(Integer.valueOf(0), Integer.valueOf(st.frames), "série vazia");

		// quadros reais pelo frameStart: pausas longas são ignoradas
		long t0 = 5000000000000L;
		PerfMonitor.frameStart(t0);
		long t = t0;
		for (int i = 0; i < 100; i++) {
			t += 16666667L;
			PerfMonitor.frameStart(t);
		}
		st = PerfMonitor.frameStats(t, 10000000000L);
		eq(Integer.valueOf(100), Integer.valueOf(st.frames), "quadros registrados pelo frameStart");
		near(60.0D, st.avgFps, 0.1D, "FPS pelo frameStart");
		t += 5000000000L; // pausa de 5 s (carregamento): não conta
		PerfMonitor.frameStart(t);
		t += 16666667L;
		PerfMonitor.frameStart(t);
		st = PerfMonitor.frameStats(t, 10000000000L);
		eq(Integer.valueOf(101), Integer.valueOf(st.frames), "pausa longa ignorada, quadro seguinte contado");
		// janela curta só pega os últimos quadros
		for (int i = 0; i < 60; i++) {
			t += 16666667L;
			PerfMonitor.frameStart(t);
		}
		st = PerfMonitor.frameStats(t, 500000000L);
		check(st.frames >= 28 && st.frames <= 32, "janela de 0,5 s ~ 30 quadros (veio " + st.frames + ")");
		st = PerfMonitor.frameStats(t, 10000000000L);
		eq(Integer.valueOf(161), Integer.valueOf(st.frames), "janela de 10 s: 100 + 1 + 60 quadros");

		// ticks do servidor
		long base = 1000000000L;
		for (int i = 0; i < 30; i++) {
			PerfMonitor.serverTickStart(base);
			PerfMonitor.serverTickEnd(base + 5000000L);
			base += 50000000L;
		}
		double[] m = PerfMonitor.msptStats();
		near(5.0D, m[0], 0.01D, "MSPT médio");
		near(5.0D, m[1], 0.01D, "MSPT máximo");
		near(30.0D, m[2], 0.01D, "amostras de MSPT");
		PerfMonitor.serverTickStart(base);
		PerfMonitor.serverTickEnd(base + 60000000L);
		m = PerfMonitor.msptStats();
		near(60.0D, m[1], 0.01D, "MSPT máximo depois de um tick lento");
	}

	// ------------------------------------------------------------------ hardware

	private static void testHardware() {
		section("detecção de hardware");
		HardwareInfo.Tier vw = HardwareInfo.Tier.MUITO_FRACO;
		HardwareInfo.Tier w = HardwareInfo.Tier.FRACO;
		HardwareInfo.Tier mid = HardwareInfo.Tier.MEDIO;
		HardwareInfo.Tier st = HardwareInfo.Tier.FORTE;

		check(HardwareInfo.classifyGpu("Intel(R) HD Graphics 2000") == vw, "HD Graphics 2000");
		check(HardwareInfo.classifyGpu("Intel(R) HD Graphics 3000") == vw, "HD Graphics 3000");
		check(HardwareInfo.classifyGpu("Intel(R) HD Graphics 4000") == w, "HD Graphics 4000");
		check(HardwareInfo.classifyGpu("Intel(R) HD Graphics 530") == w, "HD Graphics 530");
		check(HardwareInfo.classifyGpu("Intel(R) UHD Graphics 630") == w, "UHD 630 (cai em Intel genérico)");
		check(HardwareInfo.classifyGpu("Intel(R) Iris(R) Xe Graphics") == mid, "Iris Xe");
		check(HardwareInfo.classifyGpu("Intel(R) Arc(TM) A750 Graphics") == st, "Arc A750");
		check(HardwareInfo.classifyGpu("Intel(R) Arc(TM) A380 Graphics") == mid, "Arc A380 (de entrada)");
		check(HardwareInfo.classifyGpu("NVIDIA GeForce RTX 3060/PCIe/SSE2") == st, "RTX 3060");
		check(HardwareInfo.classifyGpu("NVIDIA GeForce GTX 1060 6GB/PCIe/SSE2") == st, "GTX 1060");
		check(HardwareInfo.classifyGpu("NVIDIA GeForce GTX 750 Ti/PCIe/SSE2") == mid, "GTX 750 Ti");
		check(HardwareInfo.classifyGpu("NVIDIA GeForce GTX 460/PCIe/SSE2") == w, "GTX 460");
		check(HardwareInfo.classifyGpu("AMD Radeon RX 580 Series") == mid, "RX 580");
		check(HardwareInfo.classifyGpu("AMD Radeon RX 6600") == st, "RX 6600");
		check(HardwareInfo.classifyGpu("AMD Radeon(TM) Vega 8 Graphics") == mid, "Vega 8");
		check(HardwareInfo.classifyGpu("Radeon HD 5450") == w, "Radeon HD 5450");
		check(HardwareInfo.classifyGpu("llvmpipe (LLVM 12.0.0, 256 bits)") == vw, "renderizador por software");
		check(HardwareInfo.classifyGpu("GDI Generic") == vw, "GDI Generic");
		check(HardwareInfo.classifyGpu("?") == mid, "GPU desconhecida = médio");
		check(HardwareInfo.classifyGpu(null) == mid, "GPU nula = médio");

		check(HardwareInfo.classifyCpu("Intel(R) Core(TM) i5-2500S CPU @ 2.70GHz", 4) == w, "i5-2500S");
		check(HardwareInfo.classifyCpu("Intel(R) Core(TM) i7-6700K CPU @ 4.00GHz", 8) == mid, "i7-6700K");
		check(HardwareInfo.classifyCpu("Intel(R) Core(TM) i7-12700 CPU", 20) == st, "i7-12700");
		check(HardwareInfo.classifyCpu("12th Gen Intel(R) Core(TM) i5-12400F", 12) == st, "i5-12400F");
		check(HardwareInfo.classifyCpu("Intel(R) Core(TM) i3-6100 CPU @ 3.70GHz", 4) == mid, "i3-6100 (4 threads)");
		check(HardwareInfo.classifyCpu("Intel(R) Core(TM) i3-6100 CPU @ 3.70GHz", 2) == w, "i3 com 2 threads");
		check(HardwareInfo.classifyCpu("Intel(R) Celeron(R) CPU G1610", 2) == vw, "Celeron de 2 núcleos");
		check(HardwareInfo.classifyCpu("AMD Ryzen 5 3600 6-Core Processor", 12) == st, "Ryzen 5 3600");
		check(HardwareInfo.classifyCpu("AMD Ryzen 3 1200 Quad-Core Processor", 4) == mid, "Ryzen 3 1200");
		check(HardwareInfo.classifyCpu("AMD Athlon(tm) II X2 250", 2) == vw, "Athlon II X2");
		check(HardwareInfo.classifyCpu("Intel64 Family 6 Model 42 Stepping 7, GenuineIntel", 4) == w,
				"identificador genérico do Windows cai na contagem de núcleos");
		check(HardwareInfo.classifyCpu("?", 2) == vw, "2 núcleos desconhecido");
		check(HardwareInfo.classifyCpu("?", 16) == st, "16 núcleos desconhecido");

		check(HardwareInfo.classifyRam(4096L) == w, "4 GB");
		check(HardwareInfo.classifyRam(8192L) == mid, "8 GB");
		check(HardwareInfo.classifyRam(16384L) == st, "16 GB");
		check(HardwareInfo.classifyRam(-1L) == mid, "RAM desconhecida");

		check(HardwareInfo.recommend(w, vw, mid) == Profile.ULTRA_ECONOMICO, "i5-2500S + HD 2000 + 8 GB = Ultra Econômico");
		check(HardwareInfo.recommend(vw, mid, st) == Profile.ULTRA_ECONOMICO, "CPU muito fraca = Ultra Econômico");
		check(HardwareInfo.recommend(w, w, mid) == Profile.ULTRA_ECONOMICO, "CPU e GPU fracas = Ultra Econômico");
		check(HardwareInfo.recommend(mid, w, mid) == Profile.DESEMPENHO, "só a GPU fraca = Desempenho");
		check(HardwareInfo.recommend(mid, mid, w) == Profile.DESEMPENHO, "pouca RAM = Desempenho");
		check(HardwareInfo.recommend(st, st, st) == Profile.EQUILIBRADO, "máquina forte = Equilibrado");

		// o caso do usuário, de ponta a ponta
		HardwareInfo.setGpu("Intel(R) HD Graphics 2000", "Intel", "3.1.0 - Build 9.17.10.4229",
				"4x Intel(R) Core(TM) i5-2500S CPU @ 2.70GHz");
		check(HardwareInfo.isGpuKnown(), "GPU registrada");
		check(HardwareInfo.gpuTier() == vw, "GPU do usuário");
		check(HardwareInfo.cpuTier() == w, "CPU do usuário");
		check(HardwareInfo.recommendedProfile() == Profile.ULTRA_ECONOMICO, "perfil recomendado ao usuário");
		String jvm = HardwareInfo.recommendedJvmArgs();
		check(jvm.contains("-Xmx") && jvm.contains("-XX:+UseG1GC"), "argumentos de JVM: " + jvm);
		boolean hasProfile = false;
		for (String line : HardwareInfo.summaryLines()) {
			check(!line.isEmpty(), "linha de resumo vazia");
			if (line.contains("Perfil recomendado: Ultra Econômico")) {
				hasProfile = true;
			}
		}
		check(hasProfile, "resumo mostra o perfil recomendado");
		boolean oldGl = false;
		for (String n : HardwareInfo.notes()) {
			if (n.contains("OpenGL 3.1")) {
				oldGl = true;
			}
		}
		check(oldGl, "aviso de OpenGL 3.1 para mods de renderização modernos");
	}

	// ------------------------------------------------------------------ ganchos

	private static void testHookStatus() {
		section("diagnóstico dos ganchos");
		HookStatus.reset();
		eq(Integer.valueOf(HookStatus.COUNT), Integer.valueOf(HookStatus.lines().size()), "uma linha por gancho");
		for (int i = 0; i < HookStatus.COUNT; i++) {
			check(HookStatus.name(i) != null && !HookStatus.name(i).isEmpty(), "nome do gancho " + i);
			check(HookStatus.effectName(i) != null && !HookStatus.effectName(i).isEmpty(), "efeito do gancho " + i);
			check(HookStatus.line(i).contains("ainda não executou"), "gancho " + i + " começa sem execuções");
		}
		HookStatus.hit(HookStatus.PARTICLES);
		HookStatus.hit(HookStatus.PARTICLES);
		HookStatus.effect(HookStatus.PARTICLES);
		HookStatus.effect(HookStatus.PARTICLES, 5L);
		eq(Long.valueOf(2L), Long.valueOf(HookStatus.hits(HookStatus.PARTICLES)), "execuções");
		eq(Long.valueOf(6L), Long.valueOf(HookStatus.effects(HookStatus.PARTICLES)), "efeitos");
		check(HookStatus.line(HookStatus.PARTICLES).contains("6 partículas descartadas"), "linha de status");
		long[] snap = HookStatus.snapshotEffects();
		HookStatus.effect(HookStatus.PARTICLES, 4L);
		eq(Long.valueOf(6L), Long.valueOf(snap[HookStatus.PARTICLES]), "snapshot é cópia");

		check(!HookStatus.isFailed(HookStatus.ENTITY_CULL), "gancho começa sem falha");
		HookStatus.fail(HookStatus.ENTITY_CULL, new IllegalStateException("exemplo"));
		check(HookStatus.isFailed(HookStatus.ENTITY_CULL), "falha registrada");
		check(HookStatus.line(HookStatus.ENTITY_CULL).contains("FALHOU"), "linha mostra a falha");
		check(HookStatus.line(HookStatus.ENTITY_CULL).contains("exemplo"), "linha mostra o motivo");
		HookStatus.reset();
		check(!HookStatus.isFailed(HookStatus.ENTITY_CULL), "reset limpa as falhas");
		eq(Long.valueOf(0L), Long.valueOf(HookStatus.effects(HookStatus.PARTICLES)), "reset zera os contadores");
	}

	// ------------------------------------------------------------------ benchmark

	private static void testBenchmark() throws Exception {
		section("benchmark");
		Path dir = Files.createTempDirectory("up-selftest-bench");
		Benchmark.setReportDir(dir);
		Settings.resetDefaults();
		Live.adaptiveLevel = 0;
		HookStatus.reset();

		check(!Benchmark.isRunning(), "começa parado");
		Benchmark.onFrame(1000L); // fora de execução: ignorado
		check(Benchmark.start(1, 8, "mundo de teste"), "inicia");
		check(!Benchmark.start(1, 8, "outro"), "não inicia duas vezes");
		check(Benchmark.isRunning(), "em execução");
		check(Benchmark.progress() >= 0.0D && Benchmark.progress() <= 1.0D, "progresso entre 0 e 1");
		check(Benchmark.secondsLeft() >= 0 && Benchmark.secondsLeft() <= 1, "segundos restantes");
		for (int i = 0; i < 120; i++) {
			Benchmark.onFrame(16666667L);
		}
		for (int i = 0; i < 20; i++) {
			Benchmark.onServerTick(5.0F);
		}
		Benchmark.onServerTick(60.0F);
		Benchmark.onSecond(0.5D, 1024L * 1024L * 1024L);
		Benchmark.onSecond(-1.0D, 2048L * 1024L * 1024L); // CPU indisponível nessa amostra
		for (int i = 0; i < 7; i++) {
			Benchmark.onChunkPacket();
		}
		HookStatus.effect(HookStatus.ENTITY_CULL, 42L);
		Thread.sleep(1100L);
		check(Benchmark.due(), "terminou o tempo");
		eq(Integer.valueOf(0), Integer.valueOf(Benchmark.secondsLeft()), "0 s restantes");
		Benchmark.Result r1 = Benchmark.finish();
		check(!Benchmark.isRunning(), "parou");
		check(r1 != null && r1.valid, "resultado válido");
		eq(Integer.valueOf(120), Integer.valueOf(r1.frame.frames), "quadros contados");
		near(60.0D, r1.frame.avgFps, 0.1D, "FPS médio");
		eq(Integer.valueOf(21), Integer.valueOf(r1.serverTicks), "ticks contados");
		near((20 * 5.0D + 60.0D) / 21.0D, r1.msptAvg, 0.01D, "MSPT médio");
		near(60.0D, r1.msptMax, 0.01D, "MSPT máximo");
		eq(Integer.valueOf(1), Integer.valueOf(r1.slowTicks), "ticks lentos");
		near(0.5D, r1.cpuAvg, 1e-9D, "CPU ignora amostra indisponível");
		eq(Long.valueOf(1536L), Long.valueOf(r1.heapAvgMb), "RAM média");
		eq(Long.valueOf(2048L), Long.valueOf(r1.heapMaxMb), "RAM máxima");
		eq(Integer.valueOf(7), Integer.valueOf(r1.chunkPackets), "chunks");
		eq(Long.valueOf(42L), Long.valueOf(r1.effects[HookStatus.ENTITY_CULL]), "efeito do gancho dentro da janela");
		check(r1.modEnabled, "registrou que o mod estava ativo");
		check(r1.comparison.isEmpty(), "primeiro teste não tem comparação");
		check(!r1.reportFile.isEmpty() && Files.exists(java.nio.file.Paths.get(r1.reportFile)), "relatório gravado");
		check(Files.exists(dir.resolve("last.properties")), "último resultado gravado");
		check(r1.report.contains("FPS médio: 60"), "relatório traz o FPS médio");
		check(r1.report.contains("mundo de teste"), "relatório traz o contexto");
		check(r1.report.contains("Hardware"), "relatório traz o hardware");
		check(!r1.summary().isEmpty(), "resumo curto");
		check(Benchmark.last() == r1, "last()");

		// segundo teste: otimizações desligadas, quadros de 20 ms
		Settings.set(Opt.ENABLED, 0);
		check(Benchmark.start(1, 8, "mundo de teste"), "segundo teste");
		for (int i = 0; i < 100; i++) {
			Benchmark.onFrame(20000000L);
		}
		Benchmark.Result r2 = Benchmark.finish();
		check(!r2.modEnabled, "registrou que o mod estava desligado");
		near(50.0D, r2.frame.avgFps, 0.1D, "FPS do segundo teste");
		check(!r2.comparison.isEmpty(), "segundo teste compara com o primeiro");
		String cmp = String.join("\n", r2.comparison);
		check(cmp.contains("ATIVAS") && cmp.contains("DESLIGADAS"), "comparação mostra as duas condições");
		check(cmp.contains("FPS médio: 60.0 -> 50.0 (-16.7%, pior)"), "variação de FPS: " + cmp);
		check(!cmp.contains("AVISO"), "mesma distância: sem aviso");
		check(!cmp.contains("variação natural"), "condições diferentes: sem nota de repetição");

		// terceiro: mesma condição do segundo, FPS quase igual -> ruído; distância diferente -> aviso
		check(Benchmark.start(1, 12, "mundo de teste"), "terceiro teste");
		for (int i = 0; i < 100; i++) {
			Benchmark.onFrame(19900000L);
		}
		Benchmark.Result r3 = Benchmark.finish();
		String cmp3 = String.join("\n", r3.comparison);
		check(cmp3.contains("AVISO") && cmp3.contains("distância de renderização"), "aviso de distância diferente");
		check(cmp3.contains("dentro da margem de ruído"), "diferença pequena = ruído: " + cmp3);
		check(cmp3.contains("variação natural"), "nota de condições idênticas");

		// cancelamento
		check(Benchmark.start(30, 8, ""), "quarto teste");
		Benchmark.abort("teste de cancelamento");
		check(!Benchmark.isRunning(), "cancelado");
		eq("teste de cancelamento", Benchmark.lastAbortReason(), "motivo do cancelamento");
		check(Benchmark.finish() == r3, "finish sem execução devolve o último resultado");

		// amostra insuficiente
		check(Benchmark.start(1, 8, ""), "quinto teste");
		Benchmark.onFrame(16666667L);
		Benchmark.Result r5 = Benchmark.finish();
		check(!r5.valid, "poucos quadros = inválido");
		check(r5.report.contains("ATENÇÃO"), "relatório avisa");
		check(r5.summary().get(0).contains("sem amostra"), "resumo avisa");
		Benchmark.Result r6 = Benchmark.last();
		check(r6 == r5, "last() é o inválido");

		// comparação direta com valores sintéticos
		Properties a = new Properties();
		a.setProperty("startedAt", "2026-01-01 10:00:00");
		a.setProperty("seconds", "60");
		a.setProperty("modEnabled", "false");
		a.setProperty("profile", "Equilibrado");
		a.setProperty("adaptiveLevel", "0");
		a.setProperty("renderDistance", "8");
		a.setProperty("avgFps", "40.0");
		a.setProperty("low1Fps", "20.0");
		a.setProperty("low01Fps", "10.0");
		a.setProperty("p99Ms", "50.0");
		a.setProperty("stutters", "30");
		a.setProperty("msptAvg", "-1.000");
		a.setProperty("cpuAvg", "0.8000");
		a.setProperty("heapAvgMb", "1500");
		a.setProperty("gcTimeMs", "0");
		Properties b = new Properties();
		b.putAll(a);
		b.setProperty("modEnabled", "true");
		b.setProperty("avgFps", "48.0");
		b.setProperty("low1Fps", "19.5");
		b.setProperty("stutters", "10");
		b.setProperty("cpuAvg", "0.6000");
		List<String> lines = Benchmark.compare(a, b);
		String all = String.join("\n", lines);
		check(all.contains("FPS médio: 40.0 -> 48.0 (+20.0%, melhor)"), "FPS +20% melhor: " + all);
		check(all.contains("1% low: 20.0 -> 19.5 (-2.5%, dentro da margem de ruído)"), "1% low ruído: " + all);
		check(all.contains("Travadas: 30 -> 10 (-66.7%, melhor)"), "travadas: " + all);
		check(all.contains("CPU do processo: 80% -> 60% (-25.0%, melhor)"), "CPU: " + all);
		check(all.contains("Quadro p99: 50.0 ms -> 50.0 ms (+0.0%, dentro da margem de ruído)"), "p99: " + all);
		check(!all.contains("Tick do servidor"), "medida indisponível não aparece");
		check(all.contains("DESLIGADAS") && all.contains("ATIVAS"), "condições");

		deleteTree(dir);
		Settings.resetDefaults();
	}

	// ------------------------------------------------------------------ sistema

	private static void testSystemProbe() {
		section("leituras do sistema");
		check(SystemProbe.heapUsedBytes() > 0L, "heap em uso");
		check(SystemProbe.gcCount() >= 0L, "contagem de GC");
		check(SystemProbe.gcTimeMs() >= 0L, "tempo de GC");
		check(SystemProbe.jvmUptimeMs() > 0L, "tempo da JVM");
		double cpu = SystemProbe.processCpuLoad();
		check(cpu == -1.0D || (cpu >= 0.0D && cpu <= 1.0D), "CPU entre 0 e 1 ou -1: " + cpu);
		check(SystemProbe.totalPhysicalMb() >= -1L, "RAM total");
		check(SystemProbe.freePhysicalMb() >= -1L, "RAM livre");
	}

	private static void deleteTree(Path root) {
		try {
			java.util.stream.Stream<Path> s = Files.walk(root);
			List<Path> all = new ArrayList<Path>();
			s.forEach(all::add);
			s.close();
			for (int i = all.size() - 1; i >= 0; i--) {
				Files.deleteIfExists(all.get(i));
			}
		} catch (Exception ignored) {
			// limpeza de arquivos temporários: sem importância
		}
	}
}
