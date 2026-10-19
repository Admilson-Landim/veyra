# Veyra — Funcionalidades da versão 1.0

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
  sozinho.
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

As fontes 2 a 4 podem ser desligadas em Definições → Biblioteca. No Android 13+ precisam da
permissão de fotos, pedida só quando se liga essa opção. As capas aparecem também na notificação
e no ecrã bloqueado.

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
- **Propriedades:** título, artista, álbum, ano, género, número da faixa, duração, formato, taxa
  de bits, taxa de amostragem, tamanho, caminho e data de adição ("Desconhecido" quando falta um
  valor).
- **Definir como toque:** toque de chamada, som de notificação ou som de alarme, com a música
  completa ou **só uma parte** (Android 10+). A parte escolhe-se com o mesmo editor do recorte
  (slider, ±1 s, pré-escuta) e começa no recorte da faixa ou nos primeiros 30 s. É criado um
  ficheiro de som novo (`.m4a`) na pasta Ringtones, Notifications ou Alarms; a música original
  não é alterada e o ficheiro novo não aparece na Biblioteca. O Android exige a permissão
  especial "Modificar definições do sistema", pedida só ao confirmar; a app explica isso e abre
  a página certa.

## 8. Som

- **Saltar silêncio:** acelera as partes silenciosas das faixas.
- **Equalizador** com as bandas e as predefinições reais do dispositivo, mais uma predefinição
  "Personalizada".
- **Reforço de graves**, quando o dispositivo o suporta.
- **Repor valores.**
- **Efeitos do sistema:** o Veyra anuncia a sua sessão de áudio, para equalizadores do sistema ou
  de outras apps se poderem ligar. Se o telemóvel tiver um painel de efeitos próprio, aparece um
  botão para o abrir.

Nota: alguns fabricantes aplicam os seus próprios efeitos (por exemplo, o Histen da Huawei), que
podem ignorar ou substituir os do Veyra.

## 9. Definições

| Secção | Opções |
|---|---|
| Biblioteca | Pastas excluídas, capas a partir de imagens do dispositivo, ignorar faixas curtas |
| Reprodução | Saltar o início, avançar antes do fim, saltar silêncio |
| Som | Equalizador, reforço de graves, efeitos do sistema |
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
- Playlists, recortes e definições ficam apenas no dispositivo.

---

## Limitações conhecidas

- Etiquetas ID3 mal codificadas aparecem com caracteres estranhos.
- Em MP3 VBR, o início de um recorte pode desviar 1–2 segundos.
- No EMUI em modo escuro, a barra de estado fica escura mesmo no tema claro (comportamento do
  sistema).
