# Ultra Performance

Mod de otimização para **Minecraft Java 1.16.5 + Fabric**, pensado para PCs fracos (ex.: i5-2500S, Intel HD 2000, 8 GB).

> **Estado honesto:** o código foi escrito e conferido contra os nomes do Yarn 1.16.5, e o núcleo (configuração, perfis, adaptativo, benchmark) tem autoteste. **Mas ainda não foi compilado contra o Minecraft real nem rodou dentro do jogo.** Espere de 2 a 4 rodadas de correção de erros de compilação. Nenhum ganho de FPS foi medido ainda: use o benchmark do mod no seu PC.

## Como compilar no GitHub

1. Crie um repositório novo no GitHub e envie todos os arquivos deste projeto (a pasta `.github` precisa ir junto).
2. Abra a aba **Actions**. O workflow `build` roda sozinho a cada envio (ou use *Run workflow*).
3. Quando terminar, abra a execução e baixe o artefato **ultra-performance** (é um zip com os jars).
4. Se der erro, abra o log do passo `./gradlew build`, copie o trecho com `error:` e cole para mim: eu corrijo.

## Instalação

- Fabric Loader 0.11 ou mais novo, e **Fabric API 0.42.0+1.16** (ou outra para 1.16.5).
- Coloque na pasta `mods` o jar `ultra-performance-0.1.0.jar` (**não** o `-sources.jar`).

## Uso

- **Shift direito** abre a tela de configuração (também há um botão "Ultra Performance" no canto superior direito de Opções). **F6** troca o overlay de FPS.
- Perfis: Ultra Econômico, Desempenho, Equilibrado, Personalizado. Mudar qualquer opção vira Personalizado. Tudo salva sozinho em `config/ultraperformance.json`.
- Na primeira execução o mod escolhe um perfil pelo seu hardware. Seu PC (i5-2500S + HD 2000) deve cair em Ultra Econômico.
- Aba **Sistema**: hardware detectado, estado de cada otimização ("ativo, N execuções" ou "FALHOU"), benchmark, botão para ajustar as opções de vídeo do jogo (só reduz) e cópia do diagnóstico.

## O que ele faz de verdade

Ocultar entidades e blocos animados distantes, limite e densidade de partículas, texturas animadas mais lentas, desligar o desenho de chuva/neve, orçamento de tempo para enviar chunks à GPU, FPS baixo com a janela sem foco, coleta de lixo ao sair do mundo, IA reduzida de mobs parados longe de todos os jogadores (desligada no perfil Equilibrado) e ajuste adaptativo com histerese e espera para não oscilar.

## Como medir o ganho (A/B)

1. Entre no mesmo mundo e fique no mesmo lugar. Aba Sistema, **Benchmark**.
2. Na aba Geral, desligue **Otimizações ativas** e rode o benchmark de novo.
3. O relatório (`.minecraft/ultraperformance/benchmarks`) compara com o teste anterior e avisa se a distância de renderização mudou. Diferenças abaixo de 5% são ruído.

## O que NÃO dá para fazer (e por quê)

Veja `PROGRESSO.md`. Em resumo: reescrever o renderizador (como o Sodium), o motor de luz, deduplicar memória (como o FerriteCore) ou paralelizar lógica do jogo não cabem com segurança em um mod deste tamanho.

## Testes locais

`bash dev/run-selftest.sh` compila o núcleo com Java 8 e roda o autoteste (não precisa do Minecraft).
