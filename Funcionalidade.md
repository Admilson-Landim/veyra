# Veyra — Funcionalidades da versão 1.0.0

Este documento descreve, em português, o que o Veyra faz e como cada funcionalidade se comporta.
Para compilar e para a arquitetura, consulta o [README](README.md).

---

## 1. Biblioteca

A Biblioteca mostra todas as músicas guardadas no dispositivo, lidas a partir do MediaStore do
Android.

- **Permissão contextual:** o acesso à música é pedido a partir do botão "Permitir acesso à
  música", nunca ao abrir a app. Os casos de recusa e de "não voltar a perguntar" têm mensagens
  próprias e um atalho para as definições da app. A permissão é verificada de novo sempre que a
  app volta ao primeiro plano.
- **Atualização automática:** ficheiros novos ou apagados aparecem e desaparecem sozinhos.
- **Tocar tudo / Aleatório:** tocam a lista visível, pela ordem atual ou baralhada.
- **Ordenação:** título, artista, álbum, adicionadas recentemente ou duração. Ignora maiúsculas e
  acentos ("Água" fica junto de "agua"). A escolha é guardada.
- **O que não aparece:** toques, sons de notificação e de alarme, podcasts e gravações (Android
  12+), além das pastas habituais de gravações e notas de voz (`Recordings/`,
  `MIUI/sound_recorder/`, `Sounds/CallRecord/` e as notas de voz do WhatsApp).
- **Pastas excluídas editáveis** e a opção "Ignorar faixas curtas" (desligada por defeito).
- **Contador de faixas** e indicação da faixa a tocar.
- **Vistas:** Faixas, Pastas ou Artistas, escolhidas no seletor por cima da lista (a escolha é
  guardada).
  - **Pastas:** uma lista plana das pastas com música (nome, caminho abreviado e número de
    faixas), por ordem de nome. Abrir uma pasta mostra as faixas, com "Tocar tudo" e "Aleatório".
  - **Artistas:** agrupados pelo artista principal (numa colaboração como "Joeboy ft. Qing Madi"
    conta só "Joeboy"; "&" não separa, por causa de duplas como "Alcides & Vanir"), sem distinguir
    maiúsculas, acentos nem espaços, com a grafia mais frequente e uma capa representativa.
    "Artista desconhecido" fica no fim.
- **Pesquisa:** o ícone de lupa abre um campo que procura no título, artista, álbum e nome da
  pasta enquanto escreves, sem distinguir maiúsculas nem acentos ("joao" encontra "João"). Todas
  as palavras têm de aparecer, por qualquer ordem. Nas vistas Pastas e Artistas, filtra pastas e
  artistas pelo nome. Tocar num resultado toca a lista de resultados a partir dessa faixa. O botão
  voltar fecha primeiro a pasta ou o artista aberto, depois a pesquisa.

## 1.1 Nomes limpos e etiquetas

Só mudam o que o Veyra mostra: **os ficheiros e as etiquetas nunca são alterados**.

- **Nomes a partir do ficheiro** (Definições → Biblioteca, ligado por defeito): quando a faixa não
  tem etiquetas, ou o título é só o nome do ficheiro, o Veyra limpa o nome:
  `Black_Sherif_-_Soja__Official_Visualizer_(256k).mp3` aparece como "Soja", de "Black Sherif".
  Troca `_` por espaços, tira o lixo habitual ("Official Video", "Lyrics", "[HD]", "(256k)", ids
  do YouTube, sites, créditos "prod by"), mantém "(Remix)", "(feat. X)", "(Live)" e separa artista
  e título no primeiro " - ". Etiquetas válidas têm sempre prioridade.
- **Corrigir caracteres estranhos** (ligado por defeito): etiquetas gravadas com a codificação
  errada aparecem corretas ("JoÃ£o" → "João", "Donâ€™t" → "Don’t"). Nomes corretos como "Ângela",
  "São Vicente" ou "Funaná" nunca são tocados. Um carácter perdido (�) não se recupera; com a
  limpeza de nomes ligada, usa-se então o nome do ficheiro.
- A ordenação, a pesquisa, a notificação e o ecrã bloqueado usam estes nomes. Em Propriedades
  aparece também o nome original do ficheiro.

## 2. Reprodução

- **Mini-player** fixo na Biblioteca e nas Playlists, com capa, título, artista, play/pausa e
  barra de progresso.
- **Leitor em ecrã cheio:** capa grande, título, artista, barra de procura com tempo decorrido e
  restante, anterior, play/pausa, seguinte, repetição (desligada / todas / uma) e aleatório.
- **Gestos:** deslizar a capa ou o mini-player para a esquerda passa à seguinte; para a direita
  volta à anterior. A capa acompanha o dedo e volta ao sítio se o gesto for curto. No TalkBack
  existem as ações "Faixa seguinte" e "Faixa anterior".
- **Erros:** um ficheiro apagado ou ilegível mostra uma mensagem discreta e a reprodução avança
  para a seguinte, sem entrar em ciclo se todos os ficheiros falharem.
- **Modos guardados:** aleatório e repetição mantêm-se entre sessões.
- **Temporizador** (ícone de cronómetro no leitor): 15, 30, 45, 60 ou 90 minutos, um tempo à tua
  escolha (1–480 min) ou "No fim da faixa atual". O tempo restante aparece junto ao ícone. Pode
  prolongar-se 15 minutos ou desligar-se. Nos últimos 20 segundos o volume baixa gradualmente e a
  música pausa; o volume volta ao normal para a próxima vez. Funciona com o ecrã bloqueado e a app
  em segundo plano. Pausar à mão não o cancela. Não fica guardado se a app for fechada.
- **Retomar a fila:** ao reabrir a app depois de o Android (ou tu) a fechar, a última fila volta
  a estar carregada, na mesma faixa e no mesmo ponto, **em pausa** — nunca começa a tocar sozinha.
  As faixas são reconhecidas mesmo que o Android as reindexe; as que entretanto foram apagadas
  são ignoradas, e os recortes atuais são aplicados. Se a app for morta enquanto toca, volta ao
  último ponto guardado (no máximo 15 s antes).

## 3. Segundo plano, notificação e ecrã bloqueado

- A música continua com a app fechada ou o ecrã apagado.
- **Notificação de media** com capa, anterior, play/pausa, seguinte, aleatório e repetição. Tocar
  na notificação abre o leitor.
- **Ecrã bloqueado** com os mesmos controlos.
- **Auscultadores e Bluetooth:** os botões de media funcionam; desligar os auscultadores ou o
  Bluetooth pausa a música (não passa para o altifalante); ligar o Bluetooth não começa a tocar
  sozinho. No EMUI (Huawei), algumas teclas que chegam sem identificação do remetente são
  ignoradas em vez de fazerem a app falhar (limitação do Media3).
- **Chamadas e outras apps:** a música pausa durante chamadas e alarmes e retoma depois; baixa o
  volume por instantes para sons curtos.
- **Remover dos recentes:** a tocar, a música continua; em pausa, o serviço termina.
- O serviço pára sozinho depois de 15 minutos sem tocar.

## 4. Capas

Por ordem de preferência:

1. Capa embebida no ficheiro.
2. Imagem com o mesmo nome da música, na mesma pasta (`Música.mp3` → `Música.jpg`).
3. Capa da pasta (`cover`, `folder`, `front`, `album` ou `albumart`).
4. Miniatura do VidMate (`lm_thumbnail/<id>.png`), com as barras pretas cortadas
   automaticamente.
5. Capa padrão do Veyra.

As fontes 2 a 4 funcionam **até ao Android 12** e podem ser desligadas em Definições → Biblioteca.
No Android 13 e posteriores exigiriam a permissão de fotos, que a Google Play só autoriza a apps de
galeria; por isso o Veyra não a pede e, aí, as capas vêm só dos próprios ficheiros (a opção não
aparece). As capas aparecem também na notificação e no ecrã bloqueado. Na vista Artistas, cada
artista mostra a capa da primeira das suas faixas que tenha uma.

## 5. Playlists

- Criar, renomear e apagar (com confirmação). Os nomes não podem estar vazios, têm até 60
  caracteres e não se repetem (sem distinguir maiúsculas nem acentos).
- Adicionar faixas a partir da Biblioteca, do leitor ou das opções da faixa.
- Reordenar (mover para cima/baixo), remover, tocar tudo ou a partir de uma faixa.
- **Faixas indisponíveis:** se um ficheiro desaparecer, a entrada fica esbatida, marcada como
  "Indisponível" e é ignorada na reprodução. Se o ficheiro voltar, a entrada volta a funcionar.
- **Identificação estável:** as playlists reconhecem a faixa mesmo depois de o Android a
  reindexar, de o ficheiro mudar de pasta ou de as etiquetas serem editadas.

## 6. Início e fim de cada faixa

A funcionalidade central do Veyra. **Os ficheiros nunca são alterados**: o recorte só define onde
a reprodução começa e quando avança para a seguinte.

Para cada faixa há três opções:

1. **Usar a regra global** (padrão).
2. **Recorte personalizado:** intervalo escolhido com um slider de duas pontas, ajuste fino de
   ±1 s, "Definir início/fim aqui" a partir da posição atual e pré-escuta do início e dos últimos
   segundos.
3. **Tocar sempre a faixa completa:** ignora a regra global.

**Regra global** (Definições → Reprodução):

- "Saltar o início de cada faixa" e "Avançar antes do fim", com o número de segundos ajustável.
- Só se aplica se restarem pelo menos 30 segundos da faixa, para não apagar faixas curtas.

**Prioridade:** recorte personalizado › regra global › faixa completa.

**Regras de validação:** o início tem de ser antes do fim, o intervalo dura pelo menos 1 segundo e
não passa da duração da faixa. Um recorte que deixe de caber no ficheiro (por exemplo, se o
ficheiro for substituído por um mais curto) é ignorado e assinalado, sem erros.

**Indicadores:** um ícone de tesoura na lista e uma linha discreta no leitor ("Recorte …",
"Regra global …" ou "Faixa completa").

**No leitor:** o tempo e a barra de progresso são relativos ao intervalo que toca. A repetição de
uma faixa repete só o intervalo. Mudar o recorte da faixa que está a tocar aplica-se logo, sem
saltar a música.

## 7. Opções da faixa

Abertas com toque longo ou com o botão ⋮, na Biblioteca, nas Playlists e no leitor:

- **Adicionar a uma playlist.**
- **Ajustar início e fim.**
- **Propriedades:** título, artista, álbum, nome do ficheiro, ano, género, número da faixa,
  duração, formato, taxa de bits, taxa de amostragem, tamanho, caminho e data de adição
  ("Desconhecido" quando falta um valor).
- **Definir como toque:** toque de chamada, som de notificação ou som de alarme, com a música
  completa ou **só uma parte** (Android 10+). A parte escolhe-se com o mesmo editor do recorte
  (slider, ±1 s, pré-escuta) e começa no recorte da faixa ou nos primeiros 30 s. É criado um
  ficheiro de som novo (`.m4a`) na pasta Ringtones, Notifications ou Alarms; a música original
  não é alterada e o ficheiro novo não aparece na Biblioteca. O Android exige a permissão
  especial "Modificar definições do sistema", pedida só ao confirmar; a app explica isso e abre
  a página certa.
- **Remover do dispositivo** (a última opção do menu, separada das outras):
  - **Android 11 e posteriores: "Mover para o lixo".** O ficheiro vai para o lixo do sistema e
    pode ser recuperado durante cerca de 30 dias (por exemplo, na app Ficheiros).
  - **Android 10 e anteriores: "Apagar do dispositivo",** definitivo, com o aviso "Esta ação não
    pode ser desfeita". Pede a permissão de armazenamento uma vez.
  - Há sempre uma confirmação da app, com título, artista e nome do ficheiro, e o botão de
    confirmar a vermelho; no Android 11+ o sistema pergunta outra vez. Nunca apaga a partir de um
    gesto. Se o ficheiro for o toque, a notificação ou o alarme atual, o diálogo avisa.
  - Depois: a faixa sai da fila (se estava a tocar, passa à seguinte; se era a última, pára) e da
    fila guardada. Ao apagar definitivamente saem também as entradas das playlists e o recorte; no
    lixo ficam, como "indisponível", para voltarem se recuperares o ficheiro.
  - Cancelar não é erro. Erros claros: o ficheiro já não existe, ou está num sítio só de leitura
    (como um cartão SD).

## 8. Som

- **Saltar silêncio:** acelera as partes silenciosas das faixas.
- **Equalizador** com as bandas e as predefinições reais do dispositivo, mais uma predefinição
  "Personalizada".
- **Reforço de graves**, quando o dispositivo o suporta.
- **Repor valores.**
- **Volume equilibrado** (desligado por defeito): iguala o volume entre faixas. Se o ficheiro tiver
  etiquetas ReplayGain (MP3 e FLAC/Ogg), usa-as, por faixa ou por álbum, e **só baixa** o volume,
  sem distorção. Nas faixas sem ReplayGain usa um nivelamento suave (compressor e limitador do
  Android 9+). Um único ponto calcula o volume final (ReplayGain × fade do temporizador), por isso
  não entra em conflito com o equalizador nem com o temporizador.
- **Efeitos do sistema:** o Veyra anuncia a sua sessão de áudio, para equalizadores do sistema ou
  de outras apps se poderem ligar. Se o telemóvel tiver um painel de efeitos próprio, aparece um
  botão para o abrir.

Nota: alguns fabricantes aplicam os seus próprios efeitos (por exemplo, o Histen da Huawei), que
podem ignorar ou substituir os do Veyra.

## 9. Definições

| Secção | Opções |
|---|---|
| Biblioteca | Pastas excluídas, capas a partir de imagens do dispositivo (até ao Android 12), limpar nomes a partir do ficheiro, corrigir caracteres estranhos, ignorar faixas curtas |
| Reprodução | Saltar o início, avançar antes do fim, saltar silêncio |
| Som | Equalizador, reforço de graves, efeitos do sistema, volume equilibrado (por faixa ou por álbum) |
| Aparência | Seguir o sistema, escuro, claro |
| Idioma | Seguir o sistema, English, Português |
| Sobre | Nome, slogan, versão e nota de privacidade |

Todas as definições ficam guardadas entre sessões.

## 10. Idiomas, temas e acessibilidade

- **Inglês e português**, com escolha por app. No Android 13+ o idioma também se muda em
  Definições do sistema → Idiomas das apps. O slogan "Your Music, Your Way." fica sempre em inglês.
- **Tema escuro** (prioritário) e **tema claro**, ambos com contraste verificado por testes.
- **Acessibilidade:** descrições para o TalkBack em todos os botões, alvos de toque de pelo menos
  48 dp e texto legível até 200%.
- **Ecrãs:** barra de navegação lateral em ecrãs largos e leitor em duas colunas na horizontal.

## 11. Privacidade

- Sem contas, sem anúncios e sem permissão de internet.
- Playlists, recortes, a fila guardada e as definições ficam apenas no dispositivo.
- Política de privacidade em `store/privacy/index.html`.

---

## Limitações conhecidas

- A limpeza de nomes não adivinha quando o nome do ficheiro não tem separador
  ("NEIDO_WEST-AGUENTA_BO_BRONKA" fica como título) e um artista com vírgula no nome ("Earth, Wind
  & Fire") é agrupado só pela primeira parte.
- Capas em imagens separadas só até ao Android 12 (política de fotos da Google Play).
- O volume equilibrado não lê ReplayGain de M4A nem de Opus; alguns fabricantes ignoram efeitos.
- Em MP3 VBR, o início de um recorte pode desviar 1–2 segundos.
- No EMUI em modo escuro, a barra de estado fica escura mesmo no tema claro (comportamento do
  sistema).
