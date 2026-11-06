# PROGRESSO

Legenda: [x] escrito, [t] escrito e testado localmente (núcleo), [ ] não feito. **Nada foi medido dentro do jogo.**

## Etapa 1 - Estrutura
- [x] Gradle (Loom 1.19, Yarn 1.16.5+build.10, Java 8), `fabric.mod.json`, mixins com `required:false` e `defaultRequire:0`.
- [x] Workflow do GitHub Actions (JDK 25 só para o Gradle; o jar sai em Java 8).
- [ ] Compilação real: só acontece no GitHub.

## Etapas 2 a 6 - Otimizações (ganchos)
- [x] CPU/servidor: IA reduzida de mobs distantes (opcional), medidor de tick.
- [x] RAM: GC ao sair do mundo (efeito depende da JVM). Argumentos de JVM só são sugeridos, não aplicados.
- [x] Chunks: orçamento de tempo de envio para a GPU.
- [x] Render: ocultar entidades e blocos animados distantes, chuva/neve, texturas animadas.
- [x] Partículas: limite e densidade (descartadas na fila, nunca devolvendo null).
- Cada gancho conta execuções e efeitos e se desliga sozinho se lançar erro (aba Sistema).

## Etapa 7 - Interface e perfis
- [x] Tela com abas, 4 perfis, descrições, autosave, restaurar padrões, overlay (3 modos), botão em Opções.

## Etapa 8 - Hardware e adaptativo
- [t] Detecção e classificação (i5-2500S + HD 2000 -> Ultra Econômico), perfil recomendado, argumentos de JVM sugeridos.
- [t] Adaptativo: 4 níveis, histerese, esperas, backoff; simulações de 30 min sem oscilação rápida.
- [x] Redução opcional da distância de renderização (desligada por padrão em todos os perfis), com restauração.

## Etapa 9 - Testes e benchmark
- [t] Autoteste do núcleo: 100.941 verificações, 0 falhas.
- [x] Benchmark com relatório e comparação com o teste anterior.
- [x] `dev/verify_names.py`: todas as classes importadas existem no Yarn 1.16.5.
- [ ] Testes dentro do jogo e números reais de FPS.

## Não viável aqui (explicação)
Renderizador estilo Sodium, reescrita do motor de luz, deduplicação de memória estilo FerriteCore, lógica assíncrona segura e escala de resolução exigiriam milhares de linhas e testes extensos; arriscariam corromper mundos.

## Problemas conhecidos / riscos
- Pode haver erros de compilação (APIs do Minecraft não foram compiladas aqui).
- `MobEntity.tickNewAi` é método herdado: se o mixin não encaixar, a aba Sistema mostra "ainda não executou".
- Posição do botão em Opções e layout da tela não foram vistos na prática.

## Próximos passos
1. Rodar o build no GitHub e corrigir erros.
2. Testar no jogo, olhar a aba Sistema (ganchos) e rodar o benchmark A/B.
3. Ajustar padrões dos perfis com base nos números.
