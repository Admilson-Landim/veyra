# Capturas de ecrã para a Google Play

## Regras da Play (verificadas em outubro de 2026)

- JPEG ou PNG de 24 bits **sem transparência**.
- Lado menor ≥ 320 px, lado maior ≤ 3840 px, e o lado maior **não pode ter mais do dobro** do menor.
- De 2 a 8 capturas por tipo de dispositivo. Para a app ser elegível para destaque: pelo menos
  4 capturas com ≥ 1080 px; em retrato, 9:16 (mínimo 1080 × 1920).

> O ecrã do Huawei PPA-LX2 tem 1080 × 2400 (proporção 2,22): **não é aceite tal como sai**.
> Recorta para 1080 × 1920 com o comando do passo 5.

## Direitos de autor: usa só faixas de demonstração

As capturas não podem mostrar capas nem nomes de músicas comerciais. O repositório gera 10
faixas neutras (títulos e artistas inventados, capas abstratas, acordes sintetizados):

```bash
bash store/demo/make_demo_tracks.sh            # cria store/demo/out/ (não entra no Git)
```

Nada disto faz parte da app: são ficheiros normais copiados só para tirar as capturas.

## Passo a passo

1. **Prepara um dispositivo limpo.** O ideal é um emulador (Android Studio → Device Manager →
   Pixel 7, Android 15/16), que só terá as faixas de demonstração. No teu telemóvel, a biblioteca
   real apareceria: exclui as tuas pastas em Definições → Biblioteca → Pastas excluídas
   (VidMate/download, etc.) enquanto tiras as capturas e repõe-nas no fim.
2. **Copia as faixas de demonstração e pede ao sistema que as indexe:**
   ```bash
   adb push store/demo/out "/sdcard/Music/Veyra Demo"
   adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
     -d "file:///sdcard/Music/Veyra%20Demo/"
   # Se não aparecerem: reinicia o dispositivo ou, no emulador, adb reboot.
   ```
3. **Prepara os ecrãs:** cria uma playlist ("Fim de tarde") com 4–5 faixas, define um início/fim
   numa faixa, liga o equalizador numa predefinição.
4. **Tira cada captura** (repete em Definições → Aparência: Escuro e Claro, e em Definições →
   Idioma: Português e English):
   ```bash
   adb exec-out screencap -p > store/screenshots/pt-PT/01-biblioteca-escuro.png
   ```
   Ou, no Android Studio: Running Devices → ícone da câmara.
5. **Recorta para 9:16:** fica com 1920 px a partir de logo abaixo da barra de estado (cerca de
   96 px no PPA-LX2; confirma na imagem e ajusta o `+96`). Sai o fundo do ecrã, normalmente a barra
   de navegação. O emulador Pixel 7 também é 1080 × 2400, por isso o recorte é o mesmo:
   ```bash
   magick in.png -crop 1080x1920+0+96 +repage -alpha off out.png
   ```
   Confirma: `magick identify out.png` deve dizer `1080x1920`.
6. **Quando acabares**, apaga as faixas de demonstração:
   ```bash
   adb shell rm -r "/sdcard/Music/Veyra Demo"
   ```

## Ecrãs recomendados (8, por esta ordem)

| # | Ecrã | O que mostrar |
|---|------|----------------|
| 1 | Biblioteca → Faixas | Lista com capas, "Tocar tudo" / "Aleatório", mini-leitor a tocar |
| 2 | Leitor | Capa grande, controlos, temporizador ativo (contagem visível) |
| 3 | Ajustar início/fim | Folha de recorte de uma faixa |
| 4 | Playlists | A lista e uma playlist aberta |
| 5 | Biblioteca → Pastas ou Artistas | Agrupamento por pasta/artista |
| 6 | Pesquisa | Resultados enquanto se escreve |
| 7 | Definições → Som | Equalizador e volume equilibrado |
| 8 | Tema claro | Biblioteca ou leitor no tema claro |

Guarda-as em `store/screenshots/en-US/` e `store/screenshots/pt-PT/` (a ficha de cada idioma na
Play Console tem as suas próprias capturas). Se usares o fastlane, o caminho equivalente é
`fastlane/metadata/android/<idioma>/images/phoneScreenshots/`.
