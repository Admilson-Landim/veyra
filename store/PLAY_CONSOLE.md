# Veyra na Google Play: guia de submissão

Tudo o que a Play Console vai pedir, com as respostas que correspondem ao que a app faz de facto.
Regras verificadas na documentação oficial em 20 de outubro de 2026; confirma na consola, porque
podem mudar.

## Ficheiros prontos

| O quê | Onde |
|-------|------|
| AAB de release | `app/build/outputs/bundle/release/app-release.aab` (gerado por `./gradlew bundleRelease`) |
| Ícone da loja 512 × 512 | `store/graphics/icon-512.png` |
| Imagem de destaque 1024 × 500 | `store/graphics/feature-graphic-1024x500.png` |
| Nome, descrições, notas da versão (en-US, pt-PT) | `fastlane/metadata/android/<idioma>/` |
| Política de privacidade (EN + PT) | `store/privacy/index.html` |
| Guia das capturas e faixas de demonstração | `store/SCREENSHOTS.md`, `store/demo/` |
| Gerar de novo os ícones e a imagem de destaque | `python3 store/render_graphics.py` |

## 1. Assinatura: chave de upload e Play App Signing

Com a **Play App Signing** (obrigatória para apps novas), há duas chaves:

- **Chave da app**: a Google cria-a e guarda-a. É com ela que os utilizadores recebem a app.
- **Chave de upload**: é **tua**. Assinas com ela cada AAB que envias, e a Google confirma que vem
  de ti. Se a perderes, pedes à Google uma nova chave de upload (demora dias, e só funciona com
  acesso à conta). Se alguém a roubar, pode enviar versões em teu nome até a revogares.

**O `keystore.properties` atual assina com a chave de debug do Android** (configuração de teste
da Fase 10). A Play **rejeita** AABs assinados com o certificado de debug. Antes de submeter:

```bash
# Cria a chave de upload (pede palavras-passe: usa palavras-passe fortes e diferentes).
keytool -genkeypair -v -keystore ~/chaves/veyra-upload.jks -alias veyra-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

E troca o `keystore.properties` (na raiz do projeto, fora do Git):

```properties
storeFile=/home/landim/chaves/veyra-upload.jks
storePassword=…
keyAlias=veyra-upload
keyPassword=…
```

Guarda o `.jks` e as palavras-passe **fora do repositório**: num gestor de palavras-passe e numa
cópia de segurança offline (por exemplo, uma pen cifrada). O `.gitignore` já exclui
`keystore.properties`, `*.jks` e `*.keystore`. O histórico do Git foi verificado: não tem segredos.

Depois da troca, a app instalada a partir da Play deixa de se poder atualizar por cima da build de
debug (assinaturas diferentes): desinstala a de debug primeiro.

## 2. Versões

- `versionCode` é um número inteiro que **tem de subir** em cada envio (1, 2, 3…), mesmo para
  testes internos. Nunca se reutiliza.
- `versionName` é o que o utilizador vê: `1.0.0` agora; `1.0.1` para correções, `1.1.0` para
  funcionalidades novas, `2.0.0` para mudanças grandes.
- Ambos estão em `app/build.gradle.kts`.

## 3. Checklist na Play Console

1. **Criar a app**: Todas as apps → Criar app. Nome "Veyra Music Player", idioma predefinido
   Inglês (EUA) ou Português (Portugal), **App**, **Gratuita**, aceitar as declarações.
2. **Configurar a app** (Painel → Configurar a app), cada ponto abaixo.
3. **Ficha da loja principal**: nome, descrição curta e completa (fastlane/…), ícone 512,
   imagem de destaque, capturas de telemóvel (2–8). Adiciona a tradução no outro idioma em
   *Gerir traduções*. Categoria: **Música e áudio**. Email de contacto (obrigatório).
4. **Teste interno**: Testes → Teste interno → Criar versão → enviar o AAB → notas da versão →
   adicionar a tua conta como testador → instalar pela ligação de teste e verificar.
5. **Teste fechado**: obrigatório para contas pessoais criadas depois de 13 de novembro de 2023:
   **pelo menos 12 testadores inscritos durante 14 dias seguidos** antes de pedir acesso à
   produção. Cria uma lista de emails (Google Groups ou lista na consola), partilha a ligação e
   pede-lhes que fiquem inscritos e usem a app.
6. **Pedir acesso à produção** (Painel), responder ao questionário sobre o teste fechado.
7. **Produção**: Criar versão a partir da versão testada → lançamento (pode ser gradual).

## 4. Conteúdo da app (Política → Conteúdo da app)

### Política de privacidade
URL público obrigatório. `store/privacy/index.html` está pronta (troca `CONTACT_EMAIL` pelo teu
email de contacto). Como o repositório do Veyra é proprietário (provavelmente privado, e o GitHub
Pages em repositórios privados é pago), o mais simples é:

1. Criar no GitHub um repositório **público** só para isto, por exemplo `veyra-privacy`.
2. Copiar `index.html` para lá e fazer push.
3. Settings → Pages → Source: *Deploy from a branch*, `main`, `/ (root)` → Save.
4. Em poucos minutos fica em `https://admilson-landim.github.io/veyra-privacy/`. Esse é o URL.

### Anúncios
**Não**, a app não contém anúncios.

### Acesso à app
**Todas as funcionalidades estão disponíveis sem acesso especial** (sem conta, sem login).

### Classificação de conteúdo (questionário IARC)
- Categoria: **Todos os outros tipos de apps** (não é jogo nem rede social).
- Violência, sexo, linguagem, drogas, jogos de azar: **Não** a tudo.
- Os utilizadores interagem ou partilham conteúdo entre si? **Não.**
- Partilha a localização? **Não.** Compras digitais? **Não.**
- A app permite ouvir os ficheiros do próprio utilizador, mas não fornece conteúdo: a resposta
  "a app não contém nem fornece conteúdo deste tipo" é a verdadeira.
- Resultado esperado: **PEGI 3 / Everyone** (todas as idades).

### Público-alvo e conteúdo
- Faixas etárias: escolhe **13–15, 16–17 e 18+**. Incluir menores de 13 obriga a cumprir a
  política Famílias (requisitos adicionais) sem nenhum benefício para um leitor de música.
- A app não é dirigida a crianças.

### Segurança dos dados (Data safety)
- A app recolhe ou partilha algum dos tipos de dados exigidos? **Não.**
- (Por isso não há tipos de dados a declarar, nem encriptação em trânsito ou pedido de
  eliminação a responder.)
- Justificação, se perguntarem: a app não tem permissão de internet nem SDKs de terceiros
  (publicidade, estatísticas, erros); os dados (playlists, definições) ficam só no armazenamento
  privado da app. A permissão ACCESS_NETWORK_STATE vem da biblioteca de reprodução Media3 e não
  serve para enviar nada.

### Serviços em primeiro plano (Android 14+)
Obrigatório declarar o tipo **mediaPlayback**, com descrição, impacto e **vídeo**.
- Caso de utilização: **Reprodução de multimédia**.
- Descrição (EN): "Veyra plays the user's local music files in the background. Playback always
  starts from a user action (tapping a track, Play all or the notification's play button) and
  continues with the screen off or the app in the background, with a media notification."
- Impacto se for adiado ou interrompido (EN): "The music the user is listening to would stop as
  soon as they leave the app or turn off the screen."
- Vídeo: grava o ecrã (Android: Definições rápidas → Gravação de ecrã) a mostrar: abrir o Veyra,
  tocar numa faixa, sair da app/desligar o ecrã, a música continua, controlar pela notificação.
  Usa as faixas de demonstração. Publica no YouTube como "Não listado" e cola a ligação.

### Outras declarações
- App de notícias: **Não**. App governamental: **Não**. Funcionalidades financeiras: **Não**.
  Saúde: **Não**. ID de publicidade: **Não usa** (não há SDKs de publicidade).

## 5. Permissões (manifest final, com as das bibliotecas)

| Permissão | Para quê | A Play pede declaração? |
|-----------|----------|------------------------|
| `READ_MEDIA_AUDIO` (Android 13+) | Ler e tocar a música do dispositivo | Não: é o uso principal de um leitor de música |
| `READ_EXTERNAL_STORAGE` (≤ Android 12) | O mesmo, nas versões antigas | Não |
| `WRITE_EXTERNAL_STORAGE` (≤ Android 10) | Apagar uma faixa quando o utilizador pede | Não |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continuar a tocar em segundo plano | Sim: declaração do tipo `mediaPlayback` (secção 4) |
| `WAKE_LOCK` | Manter o processador ativo a tocar com o ecrã desligado | Não |
| `WRITE_SETTINGS` | Definir um toque, notificação ou alarme (só quando o utilizador pede; concedida nas definições do sistema) | Não há formulário; justifica na revisão se perguntarem |
| `ACCESS_NETWORK_STATE` | Declarada pelo Media3 (deteção do tipo de rede); a app não tem internet | Não |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Interna do AndroidX, protege recetores da própria app | Não |

**Não** existem `MANAGE_EXTERNAL_STORAGE`, `INTERNET`, `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`,
localização, contactos, câmara nem microfone. Por isso **não** é preciso a declaração de
permissões de fotos e vídeos.

`READ_MEDIA_IMAGES` foi removida de propósito: a política de fotos e vídeos da Play só a permite a
apps cuja funcionalidade principal precisa de acesso amplo a fotos, e procurar capas não conta.
Consequência: no Android 13+ as capas vêm só dos próprios ficheiros; no Android 12 e anteriores as
capas em imagens separadas (incluindo as miniaturas do VidMate) continuam a funcionar.
