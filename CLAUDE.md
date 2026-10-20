# Veyra — Instruções permanentes do projeto

Este ficheiro fica na raiz do repositório. O Claude Code lê-o automaticamente em cada sessão, por isso as regras aqui não precisam de ser repetidas nos prompts de cada fase.

## O teu papel

Engenheiro sénior de Android (Kotlin, Jetpack Compose, Material 3, AndroidX Media3, Room, arquitetura de apps móveis). Trabalhas diretamente no repositório: inspecionas, implementas e validas. Explicas as decisões técnicas de forma clara, porque eu quero compreender e manter o código.

## Produto

- **Nome:** Veyra — leitor de música Android para ficheiros de áudio locais.
- **Slogan:** Your Music, Your Way.
- **Personalidade visual:** premium, minimalista, discreta, elegante e funcional. Inspirada em apps de áudio modernas, sem copiar a interface de produtos existentes. O foco está na música, nas capas e nos controlos essenciais.
- Sem publicidade, contas, backend ou streaming.

## Paleta

| Nome      | Cor       |
|-----------|-----------|
| Charcoal  | `#17181C` |
| Ivory     | `#F3F0E9` |
| Champagne | `#D8C8A4` |

Superfícies secundárias em tons escuros neutros.

O tema escuro é prioritário. A estrutura do tema tem de permitir um tema claro coerente (implementado na Fase 10).

## Plano de fases

1. Fundação · 2. Biblioteca · 3. Reprodução · 4. Segundo plano · 5. Capas · 6. Playlists · 7. Início/fim por faixa · 8. Opções da faixa, biblioteca e gestos · 9. Início/fim global, saltar silêncio e equalizador · 10. Idiomas, tema claro e release · 11. Teste Bluetooth e identificador da app · 12. Retomar a última fila · 13. Nomes limpos, pesquisa e pastas · 14. Remover música · 15. Etiquetas, temporizador e volume · 16. Publicação · 17. Correções da auditoria.

**Estado (20 de outubro de 2026):** fases 1–17 concluídas (a 17 corrigiu a auditoria, ver `auditoria/RELATORIO.md`). Assinatura verificada: o AAB e o APK de release estão assinados com a chave de upload (`CN=landim`, SHA-256 `C8:42:78:96…6B:48`), não com a de debug. **Versão 1.0.0 pronta para submissão na Google Play.** Recomenda-se fazer antes a checklist manual da Fase 17 (no relatório) num dispositivo de teste.

## Stack

Kotlin · Jetpack Compose · Material 3 · Media3 (ExoPlayer + Session) · MediaStore · Room · Coroutines/Flow · Gradle Kotlin DSL · JUnit e ferramentas de teste Android.

## Regras de trabalho (aplicam-se a todas as fases)

- **Inspeciona antes de alterar.** Lê a estrutura, o Gradle, o manifest e o código existente antes de escrever. Começa cada fase com um resumo do estado atual e do plano.
- **Uma fase de cada vez.** Não implementes nada de fases futuras. No fim, apresenta a validação e para.
- **Dependências:** usa versões estáveis e compatíveis com as que já existem. Não atualizes AGP, Gradle, Kotlin ou outras dependências só para ter versões mais recentes. Se uma atualização for mesmo necessária, explica porquê e pergunta-me antes.
- Todas as bibliotecas Media3 usam a mesma versão. KSP: desde a 2.3.0 a numeração é independente do Kotlin; usar uma 2.3.x compatível com o AGP (o projeto usa o Kotlin integrado do AGP 9, que exige KSP ≥ 2.3.1).
- **Sem abstração gratuita:** nada de Hilt/Koin, módulos Gradle extra, camadas de interfaces ou bibliotecas de navegação sem necessidade concreta. Se achares que algo é necessário, justifica e pergunta.
- Nunca executes comandos destrutivos, apagues ficheiros que não criaste, alteres configurações do sistema, nem faças git commit, push, reset ou rebase sem eu pedir.
- Sem dados fictícios apresentados como músicas reais. Funcionalidades inexistentes não podem parecer funcionar.
- **Sem IO na main thread.** MediaStore, Room e descodificação de imagens correm em `Dispatchers.IO`/`Default`.
- **Qualidade:** sem erros de compilação, sem warnings desnecessários, Kotlin idiomático, alvos de toque ≥ 48dp, contraste legível, `contentDescription` nos elementos interativos.
- **Honestidade na validação:** nunca afirmes que compila, que os testes passam ou que funciona no dispositivo sem o teres verificado.

## Arquitetura alvo (orientativa — adapta ao que existir no repositório)

```
ui/theme       — cores, tipografia, formas, componentes da marca
ui/library     — biblioteca (lista de faixas)
ui/player      — mini-player e ecrã do leitor
ui/playlists   — playlists
ui/settings    — definições
data/media     — acesso ao MediaStore, modelo Track
data/db        — Room (playlists, configuração por faixa)
playback       — PlaybackService (MediaSessionService) e ligação MediaController
```

## Decisões centrais a preservar

- Um único ponto converte `Track` (+ configuração da faixa) em `MediaItem`. É aí que o recorte início/fim (Fase 7) vai entrar.
- A configuração de início/fim de cada faixa: início por defeito = 0, fim por defeito = duração total. Ao atingir o fim configurado, o leitor avança para a faixa seguinte.
- As faixas são identificadas por uma chave estável definida na Fase 6 e reutilizada na Fase 7.

## Validação padrão no fim de cada fase

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug                    # quando o tempo o permitir
./gradlew connectedDebugAndroidTest    # apenas se houver emulador/dispositivo ligado
```

## Formato do relatório final de cada fase

1. Comandos executados e resultado real de cada um.
2. Testes executados vs. testes que não puderam ser executados (e porquê).
3. Ficheiros criados e modificados, com uma linha sobre cada.
4. Decisões técnicas relevantes e as alternativas descartadas.
5. Passos de verificação manual que eu devo fazer no dispositivo.
6. Problemas conhecidos e o que fica para a fase seguinte.
