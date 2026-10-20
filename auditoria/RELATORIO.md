# Veyra 1.0.0 — Relatório de teste geral, segurança e dependências

Data: 20 de outubro de 2026 · Commit auditado: `52cde81` (master) · Modo: só leitura (nenhum ficheiro do projeto foi alterado).

> **Atualização (Fase 17):** os achados foram corrigidos ou documentados. Ver [Correções aplicadas (Fase 17)](#correções-aplicadas-fase-17) no fim deste relatório. O veredicto abaixo é o da auditoria original.

---

## Resumo executivo

**Veredicto: não pronta para submeter tal como está.** A única coisa que bloqueia é a assinatura (SEC-01): o APK e o AAB de release estão assinados com o **certificado de debug do Android** (`CN=Android Debug`), e a Google Play rejeita esses ficheiros. Depois de trocar a chave de upload, a app fica **pronta com ressalvas**: os achados restantes são de gravidade baixa ou informativos, e **os testes em execução não foram feitos** (ver abaixo).

O que foi verificado e passou:
- `clean assembleDebug`, `testDebugUnitTest` (**215 testes em 30 classes: 0 falhas**), `lintDebug` (**0 erros**, 13 avisos, todos sobre versões de dependências), `assembleRelease` e `bundleRelease`.
- O manifest final do release não tem `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, localização, contactos, câmara nem microfone. Não tem `FileProvider`. Os componentes exportados estão protegidos ou são inofensivos. O `PendingIntent` é imutável e explícito.
- Não há SQL construído com texto do utilizador. Room usa só `@Query` com parâmetros. A seleção da MediaStore é constante e a pesquisa é feita em memória.
- Nenhum segredo no histórico Git (22 commits, todas as revisões).
- As 33 dependências diretas e plugins existem nos repositórios oficiais. **122 binários na cache coincidem com o SHA-1 oficial** e nenhum é diferente. A OSV não reporta **nenhuma vulnerabilidade conhecida nas 168 dependências de runtime**.

O que **não** foi possível testar: tudo o que corre num dispositivo (1.3 e `connectedDebugAndroidTest`). O emulador `veyra_auditoria` crashou 4 vezes no arranque (SIGSEGV no `qemu`). O único dispositivo ligado é um **telemóvel real (Huawei PPA-LX2)**, por isso, como pediste, não corri nele monkey, testes instrumentados nem fluxos `adb`.

### Contagem por gravidade

| Gravidade | Bugs | Segurança | Dependências |
|---|---|---|---|
| Crítica | 0 | 0 | 0 |
| Alta | 0 | **1** (SEC-01) | 0 |
| Média | 1 (BUG-01) | 0 | 0 |
| Baixa | 4 (BUG-02…05) | 3 (SEC-02…04) | 3 (DEP-01, DEP-02, DEP-03) |
| Informativa | 2 (BUG-06, BUG-07) | 5 (SEC-05…09) | 5 (DEP-04…08) |

---

## Ferramentas e ambiente

| Item | Estado |
|---|---|
| Emulador | AVD `veyra_auditoria` (API 36, google_apis x86_64), KVM disponível. **Crashou nas 4 tentativas** (`-gpu swiftshader_indirect`, `-gpu guest`, `-memory 2048`, `-feature -Vulkan`). Os core dumps estão em `coredumpctl`. Ver `logs/13-emulador-falhas.txt`. Causa provável: incompatibilidade do emulator 37.1.11 com o kernel 7.2 ou a pouca RAM livre (7 GB no total, ~6 GB ocupados durante a auditoria). |
| Telemóvel real | `CNXNU20927202909` (Huawei PPA-LX2). **Não usado** para nada. |
| Rede | Disponível: OSV, Google Maven, Maven Central, Gradle Plugin Portal e services.gradle.org responderam. |
| `apksigner`, `aapt2`, `dexdump` | Build-tools 36.1.0 (no SDK, fora do PATH). Usados. |
| `apkanalyzer` | Não existe (sem cmdline-tools). Usei `aapt2 dump` em vez dele. |
| `ffmpeg` | Disponível. Gerei 7 ficheiros de teste (tons, silêncio e um MP3 corrompido) na pasta temporária. Não chegaram a ser usados (sem emulador) e foram apagados no fim. |
| MobSF / outros scanners | Não usados (nada foi instalado no sistema). |

---

## Parte 1 — Testes

### 1.1 Build e testes automáticos

| # | Comando | Resultado | Tempo |
|---|---|---|---|
| 1 | `./gradlew clean assembleDebug` | ✅ BUILD SUCCESSFUL, 42 tarefas, sem avisos `w:` do Kotlin | 83 s |
| 2 | `./gradlew testDebugUnitTest` | ✅ **215 testes, 0 falhas, 0 erros, 0 ignorados** (30 classes) | 12 s |
| 3 | `./gradlew lintDebug` | ✅ **0 erros, 13 avisos** (ver abaixo) | 85 s |
| 4 | `./gradlew assembleRelease` | ✅ `app-release.apk` (3,4 MB), minificado com R8, **assinado com a chave de debug** | 124 s |
| 5 | `./gradlew bundleRelease` | ✅ `app-release.aab` (7,4 MB), **assinado com a chave de debug** | 3 s |
| 6 | `./gradlew connectedDebugAndroidTest` | ⛔ **Não executado.** Sem emulador funcional; só há um telemóvel real ligado. | — |

Os 8 testes instrumentados existentes (MigrationTest, PlaylistDaoTest, TrackRemoverTest, RingtoneExporterTest, etc.) ficam por correr. Pela leitura do código, só mexem em ficheiros que eles próprios criam (por exemplo, `Music/VeyraRemovalTest/`).

**Lint por categoria** (`logs/03-lint-results-debug.txt`):

| Categoria | Erros | Avisos |
|---|---|---|
| Security | 0 | 0 |
| Correctness | 0 | 13 (`GradleDependency` ×9, `NewerVersionAvailable` ×3, `AndroidGradlePluginVersion` ×1) |
| Performance | 0 | 0 |
| Accessibility | 0 | 0 |
| Internationalization | 0 | 0 |

Não há configuração de lint que desative verificações (nem `lint {}` no Gradle nem `lint.xml`).

**Testes que falharam:** nenhum.

### 1.2 Revisão funcional estática: áreas sem problemas encontrados

- **Strings:** 259 em `values`, 253 em `values-pt` (as 6 a menos são `translatable="false"`). Não falta nenhuma tradução. Não encontrei texto escrito diretamente em `Text("…")`.
- **Acessibilidade:** os 12 `contentDescription = null` são ícones decorativos dentro de botões ou linhas que já têm rótulo. Os botões usam `IconButton` (48 dp) ou 56 dp. O `ClipBadge` de 16 dp não é clicável.
- **Room:** versão 4, migrações 1→2, 2→3 e 3→4 completas e cobertas pelo `MigrationTest`. Sem `fallbackToDestructiveMigration` nem `allowMainThreadQueries`. A build não alterou os schemas exportados (`git status` limpo em `app/schemas`).
- **MediaStore:** consulta em `Dispatchers.IO`, seleção constante (sem `selectionArgs` porque não há valores variáveis). Exclui toques, notificações, alarmes, podcasts e gravações (API 31+). Debounce de 300 ms e `mapLatest`. Os erros vão para o estado `Error` através de `catch`.
- **Ciclo de vida:** o `MediaController` é libertado em `PlayerViewModel.onCleared`. `ClipPreviewPlayer` é libertado em `DisposableEffect`. O `PlaybackService.onDestroy` liberta o player, a sessão, o equalizador, o leveller, o executor e o scope. Não há `GlobalScope`. Nenhum `Context` de Activity fica guardado (usa-se sempre `applicationContext`).
- **Reprodução:** `Track.toMediaItem` é o único ponto de conversão. Para mudar o recorte da faixa atual, o código insere o item novo e remove o antigo (contorna a limitação do `canUpdateMediaItem`). A política de erros não entra em ciclo infinito. O temporizador usa `elapsedRealtime`.
- **StrictMode:** só existe em debug (`BuildConfig.DEBUG`). Confirmei no dex do release: 0 referências a `android.os.StrictMode`.

### Bugs encontrados

#### BUG-01 — Média — Criar um excerto de toque é cancelado sem aviso ao rodar o ecrã ou mudar a configuração
- **Ficheiros:** `ui/VeyraApp.kt:123,144,345-362`; `ui/options/RingtoneExporter.kt:45-53,109-114`.
- **Descrição:** a exportação (Transformer, que leva alguns segundos) corre em `rememberCoroutineScope()`, e `ringtoneInProgress` é um `remember` simples. Rodar o ecrã, mudar o tema ou o idioma, ou o sistema recriar a Activity cancela o scope. Daí resulta:
  1. `transformer.cancel()` e o toque não é definido, sem mensagem ao utilizador.
  2. O `finally { withContext(IO) { temp.delete() } }` corre já com a coroutine cancelada. O `withContext` lança `CancellationException` antes de executar, por isso `cache/ringtone-export.m4a` fica no disco.
  3. Se o cancelamento chegar durante ou logo depois de `publish`, o ficheiro já está em `Ringtones/` (ou `Notifications/`, `Alarms/`), mas o toque não é aplicado: fica um excerto órfão visível nos seletores de som do sistema.
- **Passos:** Opções da faixa → Definir como toque → escolher um trecho → Confirmar → rodar o dispositivo enquanto aparece "A criar…".
- **Evidência:** é análise estática. Não reproduzi por falta de emulador.
- **Sugestão:** mover a exportação para um `ViewModel` (`viewModelScope`) ou um `WorkManager`/serviço, guardar o estado "em curso" num `StateFlow`, e apagar o temporário com `withContext(NonCancellable)`.

#### BUG-02 — Baixa — Folhas e diálogos fecham-se ao rodar o ecrã
- **Ficheiro:** `ui/VeyraApp.kt:116-126` (`addToPlaylistTrack`, `adjustClipTrack`, `optionsTrack`, `propertiesTrack`, `ringtoneRequest`, `nameRequest` com `remember`).
- **Descrição:** perde-se o estado ao recriar a Activity. No editor de recorte, os valores ainda não guardados perdem-se. O comentário em `VeyraApp.kt:341` ("a folha fica aberta… ao regressar") só é verdade se a Activity não for recriada.
- **Sugestão:** `rememberSaveable` com o id da faixa (e resolver a `Track` a partir da biblioteca), ou guardar estes estados num ViewModel.

#### BUG-03 — Baixa — IO de disco na main thread em `PlaybackService.onDestroy`
- **Ficheiro:** `playback/QueueSaver.kt:65-74` (chamado em `PlaybackService.kt:263`).
- **Descrição:** `runBlocking(Dispatchers.IO)` bloqueia a main thread até o Room e o DataStore escreverem. Contraria a regra "sem IO na main thread". Num disco lento pode contribuir para um ANR na paragem do serviço. Em debug, o StrictMode não o deteta (a escrita acontece noutra thread).
- **Sugestão:** fazer a gravação final num scope de aplicação (`ProcessLifecycleOwner`/`applicationScope` com `NonCancellable`), ou limitar a espera com `withTimeout`.

#### BUG-04 — Baixa — Remoção: se o processo morrer durante o diálogo do sistema, a limpeza não corre
- **Ficheiro:** `ui/options/RemovalViewModel.kt:56,74-77`.
- **Descrição:** `pending` só vive na memória do ViewModel. No Android 11+, se o processo for morto com o diálogo "Mover para o lixo" aberto e o utilizador confirmar, `onSystemResult` não encontra a faixa. O ficheiro vai para o lixo, mas a fila guardada e a fila do player não são limpas. Isto é mitigado depois: o player salta itens que não consegue ler, e `planRestore` ignora chaves que já não existem.
- **Sugestão:** guardar o id da faixa pendente em `SavedStateHandle`.

#### BUG-05 — Baixa — Condição de corrida na fila guardada entre `TrackReferenceCleaner` e `QueueSaver`
- **Ficheiros:** `data/media/TrackReferenceCleaner.kt:31-38`; `playback/QueueSaver.kt:45-50`.
- **Descrição:** o cleaner lê a posição e os itens, calcula a nova fila e reescreve-a em operações separadas (fora de uma transação). Se o `QueueSaver` gravar entretanto (a cada 15 s, ou numa mudança de item), uma das escritas sobrepõe a outra com dados desatualizados. A janela é pequena e o efeito é apenas uma posição de retoma errada.
- **Sugestão:** fazer a leitura, a modificação e a escrita numa única `@Transaction` do DAO (com a posição também guardada na base de dados), ou serializar as escritas através do serviço.

#### BUG-06 — Informativa — Excertos de toque acumulam-se
- **Ficheiro:** `ui/options/RingtoneExporter.kt`; `data/media/TrackReferenceCleaner.kt:19-20`.
- **Descrição:** cada exportação cria um ficheiro novo em `Ringtones/`, `Notifications/` ou `Alarms/`. Nenhum é removido quando o toque deixa de ser usado. O nome temporário é fixo (`ringtone-export.m4a`), mas o diálogo modal impede duas exportações em simultâneo. É uma decisão documentada; fica aqui apenas o registo.

#### BUG-07 — Informativa — Comentários e ficheiros de modelo desatualizados
- `data/settings/AppPreferences.kt` (`useDeviceImages`): o comentário diz que no Android 13+ "precisa da permissão de fotos", mas a app nunca a pede (`ArtworkLoader` explica porquê).
- `res/xml/backup_rules.xml` e `res/xml/data_extraction_rules.xml` ainda têm o texto do modelo ("Sample… TODO"). Ver SEC-03.
- `MediaMetadataRetriever.release()` pode lançar `IOException` a partir da API 29 (`TrackDetails.kt:71`, `ArtworkLoader.kt:126`), num `finally` sem `catch`. É raro, mas faria crashar a folha de propriedades.

### 1.3 Teste em execução — **não executado**

Os passos 1 a 7 (instalação, `pm grant`, fluxos com `adb`, logcat, monkey com 2 seeds, rotação, PT/EN, tema, escala de texto, `am kill`) **não foram executados**. Não houve emulador funcional e não usei o telemóvel real. Não há logcat nos logs por esse motivo.

---

## Parte 2 — Segurança

### 2.1 Componentes no manifest final do release (`logs/09-release-manifest-aapt2.txt`)

| Componente | Exportado | Proteção | Avaliação |
|---|---|---|---|
| `.MainActivity` | sim (LAUNCHER) | — | Só lê o extra booleano `EXTRA_OPEN_PLAYER`. Não aceita URIs nem outros extras, e não há deep links. Uma app externa só consegue abrir a app no ecrã do leitor. Sem risco. |
| `.playback.PlaybackService` | sim (`MediaSessionService`) | `onConnect` | Ver SEC-02. |
| `androidx.media3.session.BluetoothValidationActivity` | sim | `BLUETOOTH_PRIVILEGED` (signature/privileged) | Inacessível a apps de terceiros. |
| `androidx.profileinstaller.ProfileInstallReceiver` | sim | `android.permission.DUMP` | Só a shell ou o sistema. Normal. |
| `AppLocalesMetadataHolderService` | não | — | — |
| `androidx.room.MultiInstanceInvalidationService` | não | — | — |
| `androidx.startup.InitializationProvider` | não | — | — |

Não há `FileProvider`, nem `file_paths.xml`, nem nenhum `ContentProvider` exportado. Não há `MediaButtonReceiver` no manifest. `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` tem `protectionLevel=signature` (0x2).

**`PlaybackService` / sessão** (verifiquei o bytecode do Media3 1.11.1):
- `ModeButtons.onConnect` usa `AcceptedResultBuilder(session, controller)`. Para controladores **não confiáveis** (apps de terceiros sem `MEDIA_CONTENT_CONTROL` e que não são listeners de notificações), o Media3 1.11.1 aplica `DEFAULT_UNTRUSTED_PLAYER_COMMANDS` (= `addAllReadOnlyCommands`: comandos 16, 17, 18, 21, 22, 23, 28 e 30, ou seja `GET_CURRENT_MEDIA_ITEM`, `GET_TIMELINE`, `GET_METADATA`, volume, texto e faixas) e só comandos de sessão de leitura.
- Por isso, uma app maliciosa **não consegue** tocar, pausar, saltar, `setMediaItems` (tocar URIs arbitrários) nem enviar os comandos personalizados (baralhar, repetir, temporizador). Estes só são adicionados quando `controller.isTrusted`, e o Media3 verifica-o antes de chamar `onCustomCommand`.
- Não existe nenhum comando de sessão para apagar ficheiros ou definir toques. Essas funções estão só na UI, com confirmação do utilizador e do sistema.

### 2.2 Permissões (manifest final)

| Permissão | Origem | Uso | Necessária? |
|---|---|---|---|
| `READ_MEDIA_AUDIO` | app | Ler a biblioteca (API 33+) | Sim |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | app | Biblioteca e capas em ficheiros separados (≤ API 32) | Sim |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=29`) | app | Apagar ficheiros no Android ≤ 10 | Sim |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | app | Serviço de reprodução | Sim |
| `WAKE_LOCK` | app | ExoPlayer `WAKE_MODE_LOCAL` | Sim |
| `WRITE_SETTINGS` | app | Só `RingtoneManager.setActualDefaultRingtoneUri` (`RingtoneSetter.kt:73-78`), com o tipo limitado ao enum `RingtoneTarget` (toque, notificação, alarme). Não há outra escrita em `Settings.System`. | Sim (concedida pelo utilizador nas definições do sistema) |
| `ACCESS_NETWORK_STATE` | **media3-exoplayer** | Não usada (sem rede) | Não. Ver SEC-04. |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | androidx.core | Interna (signature) | Normal |

**Confirmado ausente:** `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `READ_PHONE_STATE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, localização, contactos, câmara, microfone e `READ_MEDIA_IMAGES`. `<queries>` declara só a ação `DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`. A app declara a permissão personalizada do androidx (signature) e nenhuma própria.

### 2.3 Intents e PendingIntents
- Há um único `PendingIntent` (`PlaybackService.kt:301-307`): `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, com intent explícito para `MainActivity`. ✅
- As ações da notificação são geridas pelo Media3 (`MediaNotification`), que usa PendingIntents imutáveis.
- Intents implícitos enviados: o broadcast `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` (ver SEC-07) e as atividades de sistema `ACTION_MANAGE_WRITE_SETTINGS`, `ACTION_APPLICATION_DETAILS_SETTINGS` e `DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`. Nenhum leva dados sensíveis.

### 2.4 Ficheiros e dados
- **Remoção** (`TrackRemover.kt`): no Android 11+ usa `MediaStore.createTrashRequest` (com confirmação do sistema). No Android ≤ 10 usa `resolver.delete` sobre o URI da MediaStore. O URI vem sempre de `ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, id)`, por isso **não consegue apagar fora do áudio da MediaStore**. Antes disso há a confirmação da app (`RemoveTrackDialog`). Cancelar não faz nada.
- **Excertos:** primeiro vão para `cacheDir` (privado). Depois são publicados na MediaStore em `Ringtones/`, `Notifications/` ou `Alarms/`, com `IS_PENDING` até ficarem completos. Ficam legíveis por qualquer app com permissão de áudio, o que é esperado para um toque. Os nomes passam por `replace([\\/:*?"<>|], "_")` e `take(80)`, e a MediaStore volta a validar o `DISPLAY_NAME`. **Sem path traversal.**
- **SQL:** a seleção da MediaStore é constante (`MediaStoreTrackRepository.kt:122-133`). `ArtworkLoader` faz consultas sem seleção. Room tem só `@Query` com parâmetros e nenhum `@RawQuery`. As migrações usam `execSQL` com texto fixo. A pesquisa é filtrada em memória (`LibrarySearch.kt`). **Não encontrei nenhum risco de SQL injection.**
- **Logs:** não há `Log.d`, `Log.v`, `Log.i` nem `println`. Há 3 `Log.w` (erro de exportação, erro ao guardar o excerto, tecla de media ignorada) e nenhum inclui dados pessoais além da exceção.

### 2.5 Configuração da build
- `debuggable`: ausente no release (o `aapt2 dump badging` não mostra `application-debuggable`). ✅
- R8: `isMinifyEnabled = true` e `isShrinkResources = true`. `keepRules/rules.keep` só tem comentários (não há `-keep class **`). ✅
- Código de debug no release: 0 classes de teste, tooling, `Example` ou StrictMode entre as 5048 do dex. ✅ Vai também `DebugProbesKt.bin` (do kotlinx-coroutines), que é inofensivo.
- `usesCleartextTraffic` e `networkSecurityConfig`: ausentes. Como não há `INTERNET`, não se aplicam.
- `allowBackup=true` com regras de modelo vazias. Ver SEC-03.
- `targetSdk = 37` e `compileSdk = 37`: acima do mínimo exigido pela Play para apps novas em 2026 (API 35, e API 36 desde 31/08/2026). Não consultei online a página oficial durante a auditoria, por isso esta conclusão assenta no meu conhecimento atual.
- `requestLegacyExternalStorage=true`: só tem efeito no Android 10 e está justificado no manifest.

### 2.6 Segredos e repositório
- `git log --all --name-only`: nenhum `.jks`, `.keystore`, `keystore.properties`, `local.properties`, `.pem`, `.p12` nem `google-services` foi alguma vez commitado.
- `git grep` em todas as revisões (22 commits) por padrões de password, token, api key, chave privada, AWS, GitHub e Google: **0 resultados**.
- `.gitignore` cobre `keystore.properties`, `*.jks`, `*.keystore`, `local.properties`, `/build`, `app/build` e os ficheiros sensíveis de `.idea` (`workspace.xml` e outros). **Não cobre `.kotlin/`** (ver SEC-09).
- `keystore.properties` existe localmente (fora do Git) e aponta para `~/.android/debug.keystore`, alias `androiddebugkey` (passwords omitidas). Ver SEC-01 e SEC-08.

### 2.7 Análise do APK de release
- `apksigner verify --print-certs`: **Verifies**. v1 false, **v2 true**, v3 false, v3.1 false. Signer: `C=US, O=Android, CN=Android Debug`. SHA-256 do certificado: `25ca4010…`.
- O AAB (`keytool -printcert -jarfile`) tem o mesmo certificado `CN=Android Debug`, válido até 2056.
- Bibliotecas nativas: só `libdatastore_shared_counter.so` e `libandroidx.graphics.path.so` (4 ABIs), ambas do AndroidX.

### Achados de segurança

#### SEC-01 — Alta — O release está assinado com a chave de debug do Android
- **Componente:** `keystore.properties` → `storeFile=~/.android/debug.keystore`, `keyAlias=androiddebugkey`. `app/build.gradle.kts:27-36`.
- **Evidência:** `apksigner` e `keytool` mostram `CN=Android Debug` no APK e no AAB.
- **Impacto:** a Google Play **rejeita** AABs assinados com o certificado de debug, por isso não é possível submeter. Se fosse aceite noutra loja, a chave de debug tem password pública conhecida e qualquer pessoa com acesso a este ficheiro conseguiria assinar atualizações. O problema já está documentado em `store/PLAY_CONSOLE.md:28`, mas continua por resolver. Isto contradiz o estado "pronta para submissão" escrito no `CLAUDE.md`.
- **Correção:** gerar uma chave de upload própria (como em `store/PLAY_CONSOLE.md` §1), apontar o `keystore.properties` para ela, ativar a Play App Signing e guardar uma cópia de segurança da chave fora do projeto.

#### SEC-02 — Baixa — Qualquer app instalada pode ler a fila e os metadados (incluindo pastas e nomes de ficheiro)
- **Ficheiros:** `playback/ModeButtons.kt:72-85`; `playback/MediaItemMapping.kt:61-71`.
- **Cenário:** uma app sem permissões cria um `MediaController` para `io.github.admilsonlandim.veyra/.playback.PlaybackService`. A ligação é aceite com comandos só de leitura (`GET_TIMELINE`, `GET_METADATA`, `GET_CURRENT_MEDIA_ITEM`). Assim consegue ler a fila inteira e, nos `MediaMetadata.extras`, o `content://` URI, a `RELATIVE_PATH`, o `DISPLAY_NAME`, o tamanho e a duração de cada faixa. Isto revela os hábitos de audição e a estrutura de pastas do utilizador sem a app maliciosa ter `READ_MEDIA_AUDIO`. Não consegue controlar a reprodução nem ler o conteúdo dos ficheiros.
- **Correção:** em `onConnect`, rejeitar (`MediaSession.ConnectionResult.reject()`) os controladores que não são confiáveis nem conhecidos (por exemplo, permitir só o próprio pacote, o sistema e controladores Bluetooth/Auto), ou não enviar os extras com a chave da faixa para fora do processo (por exemplo, guardar a `TrackKey` num mapa interno do serviço indexado pelo `mediaId`).

#### SEC-03 — Baixa — Backup automático ativo com regras de modelo vazias
- **Ficheiros:** `AndroidManifest.xml` (`allowBackup=true`); `res/xml/backup_rules.xml`; `res/xml/data_extraction_rules.xml`.
- **Descrição:** a base de dados (playlists, recortes, fila guardada com caminhos e nomes de ficheiros) e as preferências do DataStore vão para o backup na nuvem Google e para a transferência entre dispositivos. É aceitável (são dados do próprio utilizador e o backup é cifrado), mas não foi uma decisão explícita.
- **Correção:** definir regras explícitas. Por exemplo, incluir `veyra.db` e as preferências e excluir a fila guardada, que não faz sentido noutro dispositivo. Também se pode manter tudo, mas limpando o texto de modelo.

#### SEC-04 — Baixa — `ACCESS_NETWORK_STATE` desnecessária (vem do media3-exoplayer)
- **Evidência:** `manifest-merger-blame-release-report.txt:52` → `[androidx.media3:media3-exoplayer:1.11.1]`.
- **Impacto:** é uma permissão normal e não dá acesso à rede. Ainda assim aparece na ficha da Play e aumenta a superfície sem necessidade.
- **Correção:** `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />`.

#### SEC-05 — Informativa — Assinatura só com o esquema v2 (sem v3)
O v3 permite a rotação de chave. Com AAB e Play App Signing, é a Google que assina o que chega aos utilizadores, por isso tem pouco impacto. Fica para considerar quando a chave de upload for criada.

#### SEC-06 — Informativa — `WRITE_SETTINGS` bem delimitada
Só há uma chamada (`RingtoneManager.setActualDefaultRingtoneUri`), com o tipo limitado a três valores e o URI vindo da própria MediaStore (a faixa ou o excerto publicado). Não encontrei nenhum caminho para alterar outras definições do sistema. Fica registado como verificação positiva.

#### SEC-07 — Informativa — Broadcast implícito de sessão de efeitos de áudio
`AudioEffectsController.kt:40,82` envia `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` com o id da sessão e o nome do pacote. Este é o contrato da plataforma para equalizadores externos. Uma app pode, por design, aplicar efeitos ao áudio da Veyra. Não precisa de correção.

#### SEC-08 — Informativa — `keystore.properties` legível por todos os utilizadores locais
O ficheiro tem permissões `-rw-r--r--` (644) e passwords em texto simples. Está fora do Git. Quando houver uma chave de upload real, usar `chmod 600` e uma password forte, ou passar a password por variável de ambiente.

#### SEC-09 — Informativa — `.kotlin/` não está no `.gitignore`
A build do Kotlin cria `.kotlin/sessions/`, e essa pasta não está ignorada. Há o risco de a commitar por engano (é lixo de build, sem segredos). Correção: adicionar `.kotlin/` ao `.gitignore`.

---

## Parte 3 — Bibliotecas

### 3.1 Inventário (dependências diretas)

| Grupo:artefacto | Declarada | Resolvida | Âmbito | Para que serve |
|---|---|---|---|---|
| androidx.compose:compose-bom | 2026.02.01 | 2026.02.01 | impl | Alinhar as versões do Compose |
| androidx.activity:activity-compose | 1.8.0 | **1.8.2** | impl | `setContent`, launchers de resultado |
| androidx.compose.material3:material3 | (BOM) | 1.4.0 | impl | Componentes Material 3 |
| androidx.compose.ui:ui / ui-graphics / ui-tooling-preview | (BOM) | 1.10.4 | impl | UI Compose, `@Preview` |
| androidx.core:core-ktx | 1.10.1 | **1.16.0** | impl | Extensões Kotlin do AndroidX |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.6.1 | **2.9.4** | impl | `lifecycleScope` |
| androidx.lifecycle:lifecycle-runtime-compose | 2.9.4 | 2.9.4 | impl | `collectAsStateWithLifecycle` |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.9.4 | 2.9.4 | impl | ViewModels em Compose |
| androidx.media3:media3-exoplayer | 1.11.1 | 1.11.1 | impl | Leitor |
| androidx.media3:media3-session | 1.11.1 | 1.11.1 | impl | MediaSession e notificação |
| androidx.media3:media3-transformer | 1.11.1 | 1.11.1 | impl | Excertos de toque |
| androidx.room:room-runtime / room-compiler (ksp) | 2.8.5 | 2.8.5 | impl/ksp | Base de dados |
| androidx.datastore:datastore-preferences | 1.2.1 | 1.2.1 | impl | Preferências |
| androidx.appcompat:appcompat | 1.7.1 | 1.7.1 | impl | Idioma por app (≤ API 32) |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.8.1 | 1.8.1 | impl | Só para alinhar com o room-testing (ver DEP-05) |
| org.jetbrains.kotlin:kotlin-stdlib | (plugin) | 2.2.10 | impl | Kotlin |
| junit:junit | 4.13.2 | — | test | Testes JVM |
| org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.9.0 | — | test/androidTest | Testes de coroutines |
| androidx.compose.ui:ui-test-junit4 / ui-test-manifest / ui-tooling | (BOM) 1.10.4 | — | androidTest/debug | Testes e tooling de UI |
| androidx.test.espresso:espresso-core | 3.5.1 | — | androidTest | Testes instrumentados |
| androidx.test.ext:junit | 1.1.5 | — | androidTest | Runner JUnit4 |
| androidx.room:room-testing | 2.8.5 | — | androidTest | `MigrationTestHelper` |

**Plugins:** `com.android.application` 9.4.1 (AGP), `org.jetbrains.kotlin.plugin.compose` 2.2.10 (KGP 2.2.10 resolvido), `com.google.devtools.ksp` 2.3.12, `androidx.room` 2.8.5, `org.gradle.toolchains.foojay-resolver-convention` 1.0.0. Gradle 9.6.0.

**Transitivas:** 161 no `releaseRuntimeClasspath` e 168 no `debugRuntimeClasspath` (mais 7 de tooling de UI, só em debug). A lista completa está em `logs/06-deps-releaseRuntime.log` e `logs/07-deps-debugRuntime.log`. O classpath dos plugins está em `logs/08-buildEnvironment.log`.

### 3.2 Existência e autenticidade
- **Existência** (`logs/10-existencia-dependencias.txt`): as 33 coordenadas diretas e de plugins existem com o grupo, nome e versão exatos. As AndroidX estão no Google Maven. `kotlinx`, `junit`, KGP e KSP estão no Maven Central. O foojay está no Gradle Plugin Portal.
- **Grupos:** todos são os oficiais (`androidx.*`, `org.jetbrains.kotlin(x)`, `com.google.devtools.ksp`, `com.android.*`, `junit`, `org.gradle.toolchains`). **Não encontrei typosquatting nem grupos desconhecidos.**
- **Integridade** (`logs/11-sha1-cache-vs-repositorio.txt`): **122 binários (aar/jar) na cache `~/.gradle/caches/modules-2` coincidem com o `.sha1` publicado. 0 são diferentes.** Outros 41 só têm POM ou `.module` (BOMs e módulos raiz KMP). 5 coordenadas raiz KMP não têm binário próprio na cache, porque resolvem para variantes `-android`/`-jvm` que já foram verificadas.
- **Repositórios** (`settings.gradle.kts`): só `google()` (com filtro de conteúdo `com.android.*`, `com.google.*`, `androidx.*` no `pluginManagement`), `mavenCentral()` e `gradlePluginPortal()`. Usa `FAIL_ON_PROJECT_REPOS`. Não há jcenter, JitPack, HTTP nem repositórios de terceiros. ✅
- **Wrapper:** `distributionUrl` aponta para o oficial `services.gradle.org`. O `distributionSha256Sum` (`bbaeb2fe…`) **coincide** com o oficial da 9.6.0. O `gradle-wrapper.jar` (`76805e32…`) **não** é o da 9.6.0, mas **coincide com o oficial do Gradle 9.0.0/9.1.0** (comparei com os checksums de 178 versões publicadas). É autêntico, só não foi regenerado. Ver DEP-06.

### 3.3 Vulnerabilidades conhecidas (OSV `querybatch`, ecossistema Maven, 281 coordenadas: runtime, debug e classpath de build)

**Runtime (o que vai no APK): 0 vulnerabilidades conhecidas** nas 168 coordenadas. "Não encontrado na OSV" não garante que não existam.

**Classpath de build (não vai no APK)** (`logs/12-osv-vulnerabilidades.txt`):

| Pacote@versão | ID | Gravidade | Corrigida em | Vem de | A app usa o código afetado? |
|---|---|---|---|---|---|
| org.bouncycastle:bcprov-jdk18on@1.80.2 | GHSA-9pwp-9qqc-pr26 / CVE-2026-8763 | Crítica | 1.85 | AGP 9.4.1 | Não no APK. O AGP usa-o para assinar e validar certificados localmente, com chaves próprias. |
| bcprov-jdk18on@1.80.2 | GHSA-qp49-qgx5-5m26 / CVE-2026-13506 | Alta | 1.85 | AGP | Idem |
| bcprov-jdk18on@1.80.2 | GHSA-c3fc-8qff-9hwx / CVE-2026-0636 (LDAP injection) | Média | 1.84 | AGP | Não (sem LDAP) |
| bcpkix-jdk18on@1.80.2 | GHSA-wg6q-6289-32hp / CVE-2026-5588 | Média | 1.84 | AGP | Idem |
| org.jdom:jdom2@2.0.6 | GHSA-2363-cqg2-863c / CVE-2021-33813 (XXE) | Alta | 2.0.6.1 | AGP | Só com XML não confiável na build. Os XML aqui são do próprio projeto. |
| org.bitbucket.b_c:jose4j@0.9.5 | GHSA-3677-xxcr-wjqv / CVE-2024-29371 (DoS) | Alta | 0.9.6 | AGP | Não processa JWE externos |
| org.apache.commons:commons-lang3@3.16.0 | GHSA-j288-q9x7-2f5v / CVE-2025-48924 | Média | 3.18.0 | AGP | Recursão com inputs longos na build |
| org.jetbrains.kotlin:kotlin-gradle-plugin@2.2.10 | GHSA-r937-wjx7-w2jp / CVE-2026-53914 | Média | 2.4.20 | Plugin Kotlin/Compose | Só com uma cache de build partilhada ou remota não confiável. Não é usada. |

### 3.4 Saúde das bibliotecas
- Não há alfa, beta, RC nem SNAPSHOT entre as resolvidas. ✅
- Todas as Media3 estão em 1.11.1 (11 módulos). Não existe `com.google.android.exoplayer`. ✅
- KSP 2.3.12 com o Kotlin 2.2.10 integrado no AGP 9: compila (confirmado pela build). Cumpre a regra do CLAUDE.md (≥ 2.3.1). ✅
- Licenças: AndroidX, Kotlin, kotlinx e Guava usam Apache-2.0. `datastore-preferences-external-protobuf` usa BSD-3-Clause. JUnit 4 usa EPL-1.0 (só testes). **Todas são compatíveis com uma app proprietária**, mas a Apache-2.0 e a BSD-3 obrigam a incluir os avisos de licença na distribuição binária. Ver DEP-07.

### Achados de dependências

- **DEP-01 — Baixa:** o classpath do AGP 9.4.1 tem BouncyCastle 1.80.2 (1 crítica, 1 alta e 2 médias), jdom2 2.0.6 (XXE), jose4j 0.9.5 e commons-lang3 3.16.0. Só são usados na build. O risco fica limitado à máquina de build quando esta processa dados não confiáveis. **Recomendação:** quando houver uma atualização do AGP que as corrija, decidir se vale a pena (pedir aprovação, conforme as regras). Em alternativa, forçar versões corrigidas no `buildscript` com `resolutionStrategy`. Não apliquei nada.
- **DEP-02 — Baixa:** kotlin-gradle-plugin 2.2.10 tem o CVE-2026-53914 (deserialização na cache de build). Não se aplica enquanto não houver cache remota ou partilhada. A correção está na 2.4.20.
- **DEP-03 — Baixa:** as versões declaradas no `libs.versions.toml` estão abaixo das resolvidas (core-ktx 1.10.1→1.16.0, lifecycle-runtime-ktx 2.6.1→2.9.4, activity-compose 1.8.0→1.8.2). O catálogo engana sobre o que vai realmente no APK. **Recomendação:** alinhar as declarações com as versões resolvidas. Isto não muda o binário.
- **DEP-04 — Informativa:** os 13 avisos do lint sobre versões mais recentes (Gradle 9.8.2, Compose BOM 2026.09.00, etc.). Não são vulnerabilidades. Ficam ao critério das regras de dependências.
- **DEP-05 — Informativa:** `kotlinx-serialization-core` está declarada como `implementation` só para alinhar com o `room-testing`, por isso entra no classpath de release (o R8 remove o que não é usado). **Recomendação:** passar para uma restrição em `androidTestImplementation`, ou usar `constraints {}`.
- **DEP-06 — Informativa:** o `gradle-wrapper.jar` é o oficial da 9.0/9.1 e não foi regenerado para a 9.6.0. Correr `./gradlew wrapper --gradle-version 9.6.0` atualiza-o sem mudar a versão do Gradle.
- **DEP-07 — Informativa:** a app não tem um ecrã ou ficheiro de licenças open source (as strings não têm "licen*"). Para cumprir a Apache-2.0 e a BSD-3, convém juntar os avisos (por exemplo, uma página "Licenças" nas definições).
- **DEP-08 — Informativa:** o projeto não tem `gradle/verification-metadata.xml` (verificação de dependências do Gradle). Também tem o plugin foojay e `gradle-daemon-jvm.properties` com `toolchainUrl` para `api.foojay.io`: se faltar um JDK 25, o Gradle descarrega-o desse serviço de terceiros. **Recomendação:** ativar a verificação de checksums (`--write-verification-metadata sha256`) para fixar a cadeia de fornecimento.

---

## O que não foi possível verificar

| Item | Motivo |
|---|---|
| 1.3 inteiro (instalação, fluxos, notificação, segundo plano, logcat, ANR, StrictMode em execução, monkey com 2 seeds, rotação, PT/EN, tema, escala de texto, `am kill` e retoma) | O emulador crashou 4 vezes com SIGSEGV. Só havia um telemóvel real ligado, que não foi usado por segurança. |
| `connectedDebugAndroidTest` (MigrationTest, PlaylistDaoTest, TrackRemoverTest, RingtoneExporterTest, etc.) | Idem |
| Instalação do release | Idem. Além disso, o release está assinado com a chave de debug. |
| Comportamento real de SEC-02 (ligar um `MediaController` de outra app) | Sem dispositivo de teste. A conclusão vem do bytecode do Media3 1.11.1 (`AcceptedResultBuilder`, `DEFAULT_UNTRUSTED_*`). |
| BUG-01 a BUG-05 | Encontrados só por análise estática. Não foram reproduzidos. |
| `targetSdk` atual na documentação oficial | Não consultei a página da Play durante a auditoria. |
| `apkanalyzer` | Não está instalado (sem cmdline-tools). Substituído por `aapt2`, `apksigner` e `dexdump`. |
| Licenças de 44 artefactos | O POM do artefacto raiz KMP não está na cache. São todos `androidx.*` (Apache-2.0 pela política do projeto AndroidX), mas não confirmei um a um. |

---

## Plano de correção sugerido (por prioridade)

1. **SEC-01** — Criar a chave de upload, atualizar o `keystore.properties` (com `chmod 600`, SEC-08), voltar a gerar o AAB e confirmar com `apksigner verify --print-certs` que o certificado já não é `CN=Android Debug`. **Bloqueia a publicação.**
2. **Fazer o teste em execução que ficou por correr:** arranjar um emulador funcional (outra imagem de sistema ou versão do emulador, ou um dispositivo de teste sem música pessoal) e correr `connectedDebugAndroidTest`, os fluxos de 1.3 e o monkey com 2 seeds.
3. **BUG-01** — Mover a criação de excertos para um ViewModel, serviço ou WorkManager. Limpar o temporário com `NonCancellable` e evitar excertos publicados órfãos.
4. **SEC-02** — Restringir `onConnect` a controladores confiáveis ou conhecidos, ou deixar de expor `RELATIVE_PATH`, `DISPLAY_NAME` e o URI nos extras enviados para fora do processo.
5. **BUG-03** — Tirar o `runBlocking` da main thread em `QueueSaver.stop()`.
6. **BUG-04 / BUG-05** — Guardar a faixa pendente em `SavedStateHandle`. Tornar atómica a atualização da fila guardada.
7. **SEC-04, SEC-03, SEC-09** — Remover `ACCESS_NETWORK_STATE` com `tools:node="remove"`. Escrever regras de backup explícitas. Adicionar `.kotlin/` ao `.gitignore`.
8. **BUG-02** — Manter as folhas abertas quando a Activity é recriada.
9. **DEP-07** — Incluir os avisos de licenças open source.
10. **DEP-03, DEP-05, DEP-06, DEP-08** — Arrumar o catálogo, mudar o âmbito do `kotlinx-serialization-core`, regenerar o wrapper e ativar a verificação de dependências.
11. **DEP-01 / DEP-02** — Rever quando houver AGP ou Kotlin que corrijam as dependências de build (com aprovação, conforme o CLAUDE.md).
12. **BUG-06 / BUG-07** — Corrigir os comentários e os ficheiros de modelo e proteger `MediaMetadataRetriever.release()` com `runCatching`.

---

## Ficheiros em `auditoria/logs/`

| Ficheiro | Conteúdo |
|---|---|
| `comandos.txt` | Comandos Gradle, código de saída e duração |
| `01`–`05-*.log` | Saída de `clean assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease` e `bundleRelease` |
| `03-lint-results-debug.txt` | Relatório do lint |
| `06`/`07-deps-*.log`, `08-buildEnvironment.log` | Árvores de dependências e plugins |
| `09-release-manifest-aapt2.txt` | Manifest final do APK release (`aapt2 dump xmltree`) |
| `10-existencia-dependencias.txt` | Existência nos repositórios oficiais |
| `11-sha1-cache-vs-repositorio.txt` | Integridade da cache face aos SHA-1 oficiais |
| `12-osv-vulnerabilidades.txt` | Resultados da OSV |
| `13-emulador-falhas.txt` | Evidência dos crashes do emulador |

Não há logcat porque não houve execução em dispositivo. Os logs não contêm segredos (verificado com `grep`).

---

## Correções aplicadas (Fase 17)

Data: 20 de outubro de 2026 · Base: `52cde81` + alterações por commitar (não fiz commit).

### Estado por ID

| ID | Estado | Explicação |
|---|---|---|
| **SEC-01** (Alta) | **Corrigido e verificado** (ver "Verificação da assinatura" abaixo) | Agora `assembleRelease` e `bundleRelease` param logo em `checkReleaseSigning` (antes de compilar) quando o `keystore.properties` falta, está incompleto, aponta para uma keystore que não existe ou aponta para a chave de debug (`debug.keystore` ou alias `androiddebugkey`). A debug compila sem keystore. O `.gitignore` já tinha `keystore.properties`, `*.jks` e `*.keystore`. Os passos para o Fedora estão no `store/PLAY_CONSOLE.md` §1. O `CLAUDE.md` deixou de dizer "pronta para submissão". **Falta:** criares a chave de upload e eu (ou tu) confirmar com `apksigner`/`keytool` que o certificado já não é `CN=Android Debug`. |
| **BUG-01** (Média) | Corrigido | O trabalho passou para o `RingtoneViewModel` (`viewModelScope`). O diálogo "A criar o som…" tem "Cancelar", e cancelar mostra sempre "Cancelado: o som não foi alterado." O temporário tem nome único em `cache/` e é apagado mesmo com cancelamento (`NonCancellable`). A entrada na MediaStore fica `IS_PENDING` até estar completa, e é apagada se o trabalho for cancelado, se falhar ou se o sistema não aceitar o som. Ao arrancar, num scope de processo em IO, `cleanUpLeftovers` remove os temporários `ringtone-export*.m4a` e as entradas pendentes **desta app** (`OWNER_PACKAGE_NAME`) em `Ringtones/`, `Notifications/` e `Alarms/` criadas antes do arranque. |
| **BUG-02** (Baixa) | Corrigido | Qual folha ou diálogo está aberto (opções, propriedades, adicionar a playlist, recorte, toque, nome de playlist) e para que faixa fica em `rememberSaveable` (id da faixa e alvo do toque). A faixa volta a ser encontrada na biblioteca. Os valores dentro das folhas já eram `rememberSaveable`. Também sobrevive à morte do processo: a folha reaparece quando a biblioteca carrega. |
| **BUG-03** (Baixa) | Corrigido | O `runBlocking` foi removido. Os valores do `QueueSaver` vão para `PendingQueueWrites`, um único consumidor no `VeyraApplication.processScope` (IO) que escreve sempre o mais recente e por ordem. No `onDestroy`, `stop()` entrega a última posição e fecha o canal; o consumidor grava o que falta e termina sozinho, sem bloquear a main thread e sem fugas. Há também uma gravação em `onTaskRemoved`. As outras gravações (mudança de fila ou de item, pausa, a cada 15 s) mantêm-se. |
| **BUG-04** (Baixa) | Corrigido | A faixa pendente (URI, campos da `TrackKey`, título) fica em `SavedStateHandle`. Depois de o processo ser recriado, o resultado do diálogo ou da permissão continua a remoção. No modo lixo, `onSystemConfirmed` confirma na MediaStore que a entrada desapareceu; se ainda existir, é reportada uma falha e nada é limpo. |
| **BUG-05** (Baixa) | Corrigido | Há agora um `SavedQueueStore` por processo com um `Mutex` justo, e é o único acesso à fila guardada (Room + DataStore). O `TrackReferenceCleaner` usa `update { }` atómico. O `QueueSaver` e a retoma (`PlayerViewModel`) usam o mesmo store. |
| **SEC-02** (Baixa) | Corrigido | Os controladores confiáveis (a app, a notificação, o sistema, o ecrã bloqueado, o Bluetooth através do sistema, os listeners de notificações) ficam como antes. As apps não confiáveis só têm play/pausa, anterior/seguinte, procurar na faixa e a faixa atual (`UNTRUSTED_PLAYER_COMMANDS`): sem `GET_TIMELINE`, sem alterar a fila e sem comandos personalizados. Além disso, a pasta, o nome do ficheiro, o tamanho e a duração **já não vão em nenhum `MediaItem` exposto**: a `TrackKeyRegistry` retira-os em `onAddMediaItems` e guarda-os só na memória do serviço. Fica só o `content://` URI, que não revela nada sem permissão. **Depois do push** (revisão automática de segurança): o `ConnectionState` do Media3 leva o token da sessão da plataforma a qualquer controlador ligado, e por essa via `getQueue()` revelava os títulos e artistas da fila. Corrigido: o controlador da notificação deixou de ter `COMMAND_GET_TIMELINE` (`PLATFORM_HIDDEN_COMMANDS`), por isso a sessão da plataforma já não publica a fila. Custo, aceite por ti: os carros deixam de mostrar a lista da fila por Bluetooth. |
| **SEC-03** (Baixa) | Parcialmente corrigido | Regras explícitas nos dois ficheiros: incluem só `veyra.db` (+ `-wal`/`-shm`) e `datastore/settings.preferences_pb`, e deixam de fora caches, capas e temporários. **Não foi possível excluir a fila guardada:** está na mesma base de dados (`saved_queue`) e a posição está no mesmo ficheiro de preferências. Excluí-la obriga a separá-la (migração), o que fica para a 1.1. Noutro telemóvel só é retomada para ficheiros que lá existam. |
| **SEC-04** (Baixa) | Corrigido | `tools:node="remove"`. Verifiquei no bytecode do Media3 1.11.1: o único uso (`NetworkTypeObserver.getNetworkTypeFromConnectivityManager`) apanha a `SecurityException` e devolve "tipo desconhecido", e a reprodução de ficheiros locais não depende disso. O manifest final de release já não tem a permissão (`logs/fase17/09-…`). |
| **DEP-03** (Baixa) | Corrigido | O catálogo foi alinhado: core-ktx 1.16.0, activity-compose 1.8.2, e lifecycle-runtime-ktx passou a usar `lifecycle` (2.9.4). Comparei as coordenadas resolvidas antes e depois: `releaseRuntimeClasspath`, `debugRuntimeClasspath` e `debugAndroidTestRuntimeClasspath` ficaram **iguais**, por isso o APK leva exatamente as mesmas versões. Só o classpath de compilação mudou, e passou a ser igual ao de runtime (antes compilava contra core 1.13.0 e activity 1.8.0). Não subi nenhuma versão. |
| **DEP-01 / DEP-02** (Baixa) | Não corrigido (de propósito) | Documentado abaixo. |
| **BUG-07** (Info) | Corrigido (trivial) | O comentário `useDeviceImages` foi corrigido. `MediaMetadataRetriever.release()` está agora em `runCatching` (`ArtworkLoader`, `TrackDetails`). Os ficheiros de backup de modelo foram substituídos (SEC-03). |
| **SEC-09** (Info) | Corrigido (trivial) | `.kotlin` foi adicionado ao `.gitignore`. |
| **BUG-06** (Info) | Parcial (como efeito da BUG-01) | Os excertos que não ficam como som (cancelados ou recusados) e os pendentes órfãos são apagados. Os excertos que ficaram como som e depois foram substituídos continuam a acumular-se. Fica para a 1.1. |
| SEC-05, SEC-06, SEC-07, SEC-08, DEP-04 a DEP-08 | Para a 1.1 / sem ação | Ver a lista abaixo. |

### DEP-01 / DEP-02 — vulnerabilidades em dependências usadas só na build

As vulnerabilidades da OSV (BouncyCastle 1.80.2, jdom2 2.0.6, jose4j 0.9.5, commons-lang3 3.16.0 e kotlin-gradle-plugin 2.2.10) estão **só no classpath do Gradle e dos plugins** (AGP 9.4.1, plugin Kotlin). **Nenhuma vai no APK ou no AAB:** confirmei que o `releaseRuntimeClasspath` não as tem e que a OSV não reporta nada nas 168 dependências de runtime. O risco fica limitado à máquina de build quando esta processa dados não confiáveis, o que não acontece neste projeto (não há cache de build remota nem XML ou JWT externos). Serão resolvidas numa futura atualização do AGP e do Kotlin, que, segundo as regras do `CLAUDE.md`, precisa de aprovação. Não atualizei nada.

### Lista para a versão 1.1 (não corrigido nesta fase)

- **SEC-03 (resto):** separar a fila guardada para uma base de dados ou ficheiro próprio e excluí-la do backup.
- **BUG-06:** remover os excertos criados pela app quando deixam de ser o som do sistema.
- **SEC-05:** ativar a assinatura v3 (`enableV3Signing`) quando a chave de upload existir.
- **SEC-08:** `chmod 600 keystore.properties` (o ficheiro é teu; não lhe toquei). Os passos estão no `PLAY_CONSOLE.md`.
- **DEP-05:** o `kotlinx-serialization-core` em `implementation` **é necessário** (o APK de teste não pode substituir as classes da app, e o room-testing precisa da 1.8.1). Manter, ou trocar por `constraints {}` com o mesmo efeito.
- **DEP-06:** regenerar o `gradle-wrapper.jar` (`./gradlew wrapper --gradle-version 9.6.0`). Muda ficheiros binários e scripts, por isso fica para tu decidires.
- **DEP-07:** ecrã ou ficheiro de licenças open source (Apache-2.0 / BSD-3). É uma funcionalidade nova.
- **DEP-08:** `gradle/verification-metadata.xml` (checksums) e decidir sobre o foojay.
- **DEP-04:** avisos do lint sobre versões mais recentes. Não são vulnerabilidades.
- SEC-06 e SEC-07: sem ação (verificações positivas ou comportamento da plataforma).

### Validação executada (`auditoria/logs/fase17/`)

| Comando | Resultado |
|---|---|
| `./gradlew clean assembleDebug` | ✅ BUILD SUCCESSFUL, sem avisos `w:` |
| `./gradlew testDebugUnitTest` | ✅ **232 testes, 0 falhas** (35 classes: 215 anteriores + 17 novos), sem avisos |
| `./gradlew lintDebug` | ✅ **0 erros**, 13 avisos (os mesmos de antes: versões de dependências) |
| `./gradlew assembleDebugAndroidTest` | ✅ os testes instrumentados compilam (incluindo o novo `TrackKeyRegistryTest` e o `TrackReferenceCleanerTest` ajustado). **Não foram executados.** |
| `./gradlew bundleRelease` / `assembleRelease` com o `keystore.properties` atual (chave de debug) | ✅ **falham como esperado** em `checkReleaseSigning`, antes de compilar: "keystore.properties aponta para a chave de debug do Android, que a Google Play rejeita — ver store/PLAY_CONSOLE.md, secção 1." |
| `bundleRelease` sem `keystore.properties` (numa cópia temporária do projeto, sem tocar no teu ficheiro) | ✅ falha: "keystore.properties em falta — ver store/PLAY_CONSOLE.md, secção 1." Na mesma cópia, `assembleDebug` ✅ |
| `bundleRelease` + `assembleRelease` com a chave de upload | ✅ BUILD SUCCESSFUL (`logs/fase17/10-release-chave-upload.log`). Assinatura verificada, ver abaixo. |
| Manifest final de release (`processReleaseMainManifest -x checkReleaseSigning`) | ✅ Permissões: `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (≤32), `WRITE_EXTERNAL_STORAGE` (≤29), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `WRITE_SETTINGS` e a interna do androidx. **Sem `ACCESS_NETWORK_STATE`.** `dataExtractionRules` e `fullBackupContent` apontam para as regras novas. |
| Testes em dispositivo | ⛔ **Não executados.** Só está ligado o telemóvel real (Huawei PPA-LX2), e o emulador continua sem arrancar. Ver a checklist abaixo. |

### Testes novos

- `ui/options/RingtoneViewModelTest` (4): sucesso, falha, cancelamento sempre reportado e pedido ignorado enquanto outro decorre.
- `ui/options/RingtoneLeftoversTest` (3): limpeza só de temporários próprios e de entradas pendentes desta app, nas pastas de som e anteriores ao arranque.
- `data/queue/SavedQueueStoreTest` (3): 30 `update` concorrentes em `Dispatchers.Default` sem perder nenhum; `save` e `update` intercalados mantêm itens e posição coerentes; uma atualização sem mudança não escreve.
- `playback/PendingQueueWritesTest` (3): agrupa rajadas e escreve o mais recente; `close` grava o que está pendente e ignora o que vem depois; escritas de 8 threads acabam com o último valor.
- `playback/UntrustedControllerCommandsTest` (2): apps de terceiros sem fila e sem alterações, mas com controlo de reprodução e faixa atual.
- `ui/options/RemovalViewModelTest` (+2): a faixa pendente sobrevive à recriação do processo; sem confirmação da MediaStore, nada é limpo.
- `androidTest/playback/TrackKeyRegistryTest` (2, só compilado): os itens admitidos perdem a `TrackKey`, que fica no registo; a reprodução não é afetada.

### Ficheiros

**Novos:** `data/queue/SavedQueueStore.kt` (acesso serializado à fila guardada), `playback/TrackKeyRegistry.kt` (chaves só no serviço), `ui/options/RingtoneViewModel.kt` (excertos fora do ciclo de vida do ecrã) e os 6 ficheiros de teste acima.

**Alterados:**
- `app/build.gradle.kts`: `checkReleaseSigning`, e assinatura só com chave válida.
- `gradle/libs.versions.toml`: DEP-03.
- `AndroidManifest.xml`: SEC-04.
- `res/xml/*_rules.xml`: SEC-03.
- `strings.xml` (EN/PT): `ringtone_cancelled`.
- `VeyraApplication.kt`: `processScope` e limpeza de restos.
- `VeyraApp.kt`: BUG-01 e BUG-02.
- `RingtoneExporter.kt` e `RingtoneSetter.kt`: BUG-01.
- `QueueSaver.kt` e `PlaybackService.kt`: BUG-03 e SEC-02.
- `ModeButtons.kt` e `MediaItemMapping.kt`: SEC-02.
- `RemovalViewModel.kt` e `TrackRemover.kt`: BUG-04.
- `TrackReferenceCleaner.kt` e `PlayerViewModel.kt`: BUG-05.
- `ArtworkLoader.kt`, `TrackDetails.kt` e `AppPreferences.kt`: BUG-07.
- `TrackReferenceCleanerTest.kt` e `RemovalViewModelTest.kt`: testes ajustados.
- `.gitignore`, `README.md`, `CLAUDE.md` e `store/PLAY_CONSOLE.md`: documentação.

### Impacto da SEC-02 noutros controladores

- **Notificação e ecrã bloqueado:** o controlador interno do Media3 (mesmo pacote) e a system UI são confiáveis, por isso não mudam. Os botões de baralhar e repetir continuam na notificação.
- **Bluetooth (AVRCP, auriculares, carro por Bluetooth):** os comandos chegam através do sistema (confiável). Um carro que mostra a fila através do AVRCP 1.6 também passa pelo serviço Bluetooth do sistema (confiável), por isso deve continuar a ver a fila. Não testei.
- **Android Auto:** a Veyra não tem `MediaLibraryService` nem `automotive_app_desc`, por isso o Android Auto não a lista. Não há mudança.
- **Wear OS:** os controlos multimédia no relógio usam a ponte de notificações (listener de notificações, confiável). Não há mudança.
- **Widgets e apps de terceiros** (por exemplo, automação): se forem listeners de notificações (o caso mais comum), continuam confiáveis. Se não forem, **passam a poder** tocar, pausar e saltar (antes só podiam ler) e deixam de ver a fila. Isto segue o que pediste. Nada de importante parece partir, por isso não precisei de perguntar.

### Checklist manual (dispositivo de teste sem música pessoal, ou emulador)

1. **Rotação durante um excerto:** Opções → Definir como toque → "Só uma parte" → Definir. Durante "A criar o som…", rodar o ecrã duas vezes. Esperado: o diálogo mantém-se e no fim aparece "… definido como toque".
2. **Cancelar um excerto:** igual ao passo 1, mas tocar em "Cancelar". Esperado: aparece "Cancelado: o som não foi alterado." e não fica nenhum ficheiro novo em `Ringtones/` (`adb shell ls /sdcard/Ringtones`) nem `ringtone-export*.m4a` em `cache/`.
3. **Processo morto durante um excerto:** começar um excerto e correr `adb shell am kill io.github.admilsonlandim.veyra` (com a app em segundo plano). Abrir de novo: não deve ficar nenhum ficheiro pendente nem temporário.
4. **Rotação com folha aberta:** abrir Opções, Propriedades, Adicionar a playlist, Ajustar recorte (mudar valores sem guardar), Toque e "Nova playlist", e rodar o ecrã em cada uma. Esperado: a mesma folha continua aberta, com os mesmos valores.
5. **Processo morto durante a remoção:** em Programador, ativar "Não manter atividades" (ou usar `am kill` com a app em segundo plano). Opções → Remover → confirmar. Com o diálogo do sistema aberto, matar o processo e depois confirmar "Mover para o lixo". Esperado: a mensagem de removido aparece, a faixa sai da fila e da retoma, e a biblioteca deixa de a mostrar.
6. **Notificação e ecrã bloqueado** (SEC-02): tocar, pausar, saltar, baralhar e repetir pela notificação e no ecrã bloqueado. Esperado: tudo funciona, a capa aparece e o "seguinte" continua ativo enquanto houver faixas na fila. (O controlador da notificação já não vê a fila inteira: confirmar em especial isto.)
7. **Bluetooth:** auriculares (play/pausa, seguinte) e, se possível, um carro. Esperado: os controlos e a faixa atual funcionam; a lista da fila não aparece (de propósito).
8. **Retoma** (BUG-03): tocar, deslizar a app para fora dos Recentes, e esperar ou forçar a paragem do serviço. Reabrir: a fila e a posição devem voltar.
9. `adb logcat | grep -E "StrictMode|io.github.admilsonlandim.veyra"` durante os passos acima. Esperado: nenhuma violação de disco na main thread vinda do `QueueSaver`.

### Verificação da assinatura (SEC-01)

A chave de upload foi criada por ti em `~/chaves/veyra-upload.jks` (PKCS12, alias `veyra`). O `keystore.properties` e a keystore têm permissões `600` e o `keystore.properties` está ignorado pelo Git. Resultado (`logs/fase17/11-verificacao-assinatura.txt`):

| Artefacto | Ferramenta | Certificado |
|---|---|---|
| `app-release.aab` | `keytool -printcert -jarfile` | `CN=landim, OU=none, O=none, L=Praia, ST=Santiago, C=CV`, SHA384withRSA, RSA 2048, válido de 20/10/2026 a 07/03/2054 |
| `app-release.apk` | `apksigner verify --print-certs` | **Verifies**, esquema v2, o mesmo DN, SHA-256 `c84278961c334ab29683d613440b2b3b473ff5fb3ccd0bae08e7bd42ca736b48` |

`CN=Android Debug`: **0 ocorrências.** A validade ultrapassa o mínimo da Play (depois de 22/10/2033). A chave tem 2048 bits, que é o mínimo aceite pela Play (o exemplo do `PLAY_CONSOLE.md` usava 4096; ambos são aceites). Apaguei a cópia temporária `keystore.properties.debug-antigo`.

**Guarda uma cópia de segurança de `~/chaves/veyra-upload.jks` e da palavra-passe fora deste computador.** Sem elas, não consegues enviar atualizações sem pedir à Google uma chave nova.

### Veredicto atualizado

**Pronta para submissão na Google Play.** A SEC-01, a única que bloqueava, está corrigida e verificada. Os restantes achados estão corrigidos, ou ficaram documentados e para a versão 1.1, e nenhum deles bloqueia.

Ressalva: os testes em dispositivo continuam por fazer, porque não havia emulador funcional nem telemóvel de teste. Recomendo fazer a checklist manual acima antes de passar do teste interno para produção, ou usar a faixa de **teste interno** da Play para isso.
