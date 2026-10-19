# Veyra

**Your Music, Your Way.**

Veyra is a minimalist Android music player for the audio files stored on your device. No accounts,
no ads, no streaming and no internet connection: your music, playlists and settings never leave
the phone.

## Features

- **Library** from MediaStore: play all / shuffle, sort by title, artist, album, date added or
  length; recordings, voice notes, ringtones and podcasts are excluded; editable excluded folders
  and an optional "hide short tracks" filter.
- **Playback** with Media3: background playback, media notification and lock screen controls,
  headset and Bluetooth buttons, pause when headphones are unplugged, shuffle and repeat
  (also from the notification), swipe the cover or mini-player to change track.
- **Covers**: embedded artwork, or a separate image next to the file (same name, folder cover,
  VidMate thumbnail), or the default Veyra cover.
- **Playlists** stored with Room: create, rename, delete, reorder, play all or from a track;
  tracks that disappear are marked unavailable and skipped.
- **Custom start and end** per track, and a **global rule** ("skip the first X s" /
  "move on X s before the end") with priority *track > global > whole file*. Files are never
  changed.
- **Track options**: properties (format, bitrate, sample rate…), set as ringtone, notification
  or alarm sound.
- **Sound**: skip silence, equalizer with the device's own bands and presets, bass boost.
- **Settings**: library, playback, sound, appearance (system / dark / light), language
  (system / English / Português), about.

## Requirements

- Android 7.0 (API 24) or newer.
- To build: Android Studio with JDK 17+, Android SDK 37. The Gradle wrapper downloads Gradle 9.6.

## Building

```bash
./gradlew assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (device or emulator)
./gradlew lintDebug
./gradlew assembleRelease        # minified release APK: app/build/outputs/apk/release/
```

### Release signing

Release builds are signed with a keystore described in `keystore.properties` at the project root.
That file (and any `*.jks` / `*.keystore`) is git-ignored and must never be committed.

1. Create a keystore once, and keep it (and its passwords) in a safe place outside the project,
   with a backup: losing it means you can no longer update the app.

   ```bash
   keytool -genkeypair -v -keystore ~/keys/veyra-release.jks -alias veyra \
       -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` next to `settings.gradle.kts`:

   ```properties
   storeFile=/home/you/keys/veyra-release.jks
   storePassword=…
   keyAlias=veyra
   keyPassword=…
   ```

Without `keystore.properties`, `assembleRelease` still builds, but the APK is unsigned.

## Architecture

Single Gradle module, no dependency-injection framework. Kotlin, Jetpack Compose, Material 3,
Coroutines/Flow.

| Package | Role |
|---|---|
| `ui/theme` | Palette (Charcoal, Ivory, Champagne), dark and light schemes, typography, brand components |
| `ui/library`, `ui/playlists`, `ui/player`, `ui/settings`, `ui/options`, `ui/clips` | Screens, sheets and their ViewModels |
| `data/media` | MediaStore queries, `Track`, `TrackKey` (stable track identity), artwork loading |
| `data/db` | Room: playlists and per-track settings, with explicit migrations (schemas in `app/schemas`) |
| `data/settings` | DataStore preferences |
| `data/clips` | Start/end rules: validation and priority (track > global > whole file) |
| `data/audio` | Equalizer settings and device capabilities |
| `playback` | `PlaybackService` (MediaSessionService with ExoPlayer), `PlayerConnection` (MediaController), the single `Track → MediaItem` conversion, audio effects |

The UI never touches the player directly: it talks to `PlaybackService` through a
`MediaController`, so playback keeps running in the background.

---

## Resumo em português

O **Veyra** é um leitor de música Android minimalista para os ficheiros de áudio guardados no
dispositivo. Sem contas, anúncios, streaming nem ligação à internet.

**Funcionalidades:** biblioteca com ordenação e exclusão de gravações; reprodução em segundo
plano com notificação, ecrã bloqueado, auscultadores e Bluetooth; capas embebidas ou de imagens
do dispositivo; playlists; início e fim por faixa e regra global; propriedades e definir como
toque; saltar silêncio e equalizador; tema escuro e claro; inglês e português.

**Requisitos:** Android 7.0 ou superior. Para compilar: Android Studio, JDK 17+ e SDK 37.

**Compilar:** `./gradlew assembleDebug` (debug) ou `./gradlew assembleRelease` (release
minificada, assinada com a keystore indicada em `keystore.properties`, que nunca vai para o
repositório).

**Arquitetura:** um único módulo; Compose na interface; Media3 num `MediaSessionService`
controlado por `MediaController`; Room para playlists e configuração por faixa; DataStore para
as preferências.
