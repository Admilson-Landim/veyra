<div align="center">

# Veyra

**Your Music, Your Way.**

A minimalist, private music player for the audio files on your Android device.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-17181C?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-D8C8A4?style=flat-square)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-D8C8A4?style=flat-square)
![Media3](https://img.shields.io/badge/Media3-1.11-D8C8A4?style=flat-square)
![Version](https://img.shields.io/badge/version-1.0-17181C?style=flat-square)

</div>

---

Veyra plays the music already on your phone. There are no accounts, no ads, no streaming and no
network access at all: your library, playlists and settings never leave the device.

What sets it apart is **control over where each song starts and ends**. Trim the long intro of
one track, end another before its fade-out, or apply one global rule to the whole library — the
audio files themselves are never modified.

## Contents

- [Highlights](#highlights)
- [Features](#features)
- [Privacy](#privacy)
- [Permissions](#permissions)
- [Requirements](#requirements)
- [Build and run](#build-and-run)
- [Release signing](#release-signing)
- [Architecture](#architecture)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [License](#license)
- [Resumo em português](#resumo-em-português)

## Highlights

- **Per-track start and end** with a range slider, ±1 s fine tuning, "set here" from the current
  position and short previews of the start and the end.
- **Global start/end rule** ("skip the first X seconds", "move on X seconds before the end") with
  a clear priority: *track setting › global rule › whole file*, and protection for short tracks.
- **Reliable background playback** built on Media3: media notification, lock screen, headset and
  Bluetooth buttons, pause when headphones are unplugged.
- **Covers even when files have none**: embedded art, an image next to the file, a folder cover or
  a VidMate thumbnail, with automatic removal of letterbox bars.
- **Premium dark design** with a matching light theme, in English and Portuguese.

## Features

| Area | What you get |
|---|---|
| **Library** | All music from MediaStore; play all / shuffle; sort by title, artist, album, recently added or length (accent- and case-insensitive); recordings, voice notes, ringtones, alarms and podcasts excluded; editable excluded folders; optional "hide short tracks". |
| **Player** | Mini-player and full-screen player; play/pause, previous/next, seek, repeat (off / all / one), shuffle; the last queue, track and position are restored (paused) when the app is reopened; swipe the cover or mini-player to change track; two-pane layout in landscape. |
| **Background** | `MediaSessionService` with notification and lock-screen controls; shuffle and repeat buttons in the notification; headset and Bluetooth media buttons; audio focus (calls, other apps); wake lock while playing. |
| **Covers** | Embedded artwork; same-name image, folder cover (`cover`, `folder`, `front`…) or VidMate thumbnail; default Veyra cover otherwise. Also shown in the notification. |
| **Playlists** | Create, rename (no duplicates), delete with confirmation, reorder, remove, play all or from a track. Tracks that disappear are marked *unavailable* and skipped. |
| **Start / end** | Per-track custom range, "always play the whole track", or follow the global rule. Invalid ranges (e.g. a shorter replacement file) are ignored and flagged, never a crash. |
| **Track options** | Add to playlist, adjust start/end, properties (format, bitrate, sample rate, size, path, date added…), set as ringtone / notification / alarm sound — the whole song or only a chosen part (Android 10+), exported to a new file with Media3 Transformer. |
| **Sound** | Skip silence; equalizer with the device's real bands and presets plus a custom preset; bass boost; system effects panel when available. |
| **Settings** | Library, Playback, Sound, Appearance (system / dark / light), Language (system / English / Português), About. |
| **Accessibility** | TalkBack labels and actions (including next/previous for the swipe gestures), touch targets ≥ 48 dp, large text up to 200%, contrast checked by tests. |

A detailed, per-feature description (in Portuguese) is in [Funcionalidade.md](Funcionalidade.md).

## Privacy

- No accounts, no analytics, no ads.
- No `INTERNET` permission: the app cannot send anything anywhere.
- Playlists and per-track settings live in a local Room database; preferences in DataStore.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (≤ 12) | Read the music on the device. Asked from the Library, never at start-up. |
| `READ_MEDIA_IMAGES` (Android 13+, optional) | Find covers stored as separate images. Asked only when that setting is turned on. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background. |
| `WAKE_LOCK` | Keep the CPU awake while playing with the screen off. |
| `WRITE_SETTINGS` (special access) | Only to set a track as ringtone, notification or alarm sound; the user grants it in system settings. |
| `ACCESS_NETWORK_STATE` | Added by the Media3 library, which reads the connection type for streaming heuristics. It cannot open connections; Veyra has no `INTERNET` permission. |

## Requirements

- **Device:** Android 7.0 (API 24) or newer.
- **Build:** Android Studio (AGP 9.4), JDK 17+, Android SDK 37. The Gradle wrapper downloads
  Gradle 9.6.

## Build and run

```bash
git clone git@github.com:Admilson-Landim/veyra.git
cd veyra
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on a connected device
```

Or open the folder in Android Studio and press **Run**.

## Release signing

```bash
./gradlew assembleRelease      # minified (R8) and resource-shrunk APK
```

Release builds are signed with the keystore described in `keystore.properties` at the project
root. That file, `*.jks` and `*.keystore` are git-ignored and must never be committed.

1. Create a keystore once and keep it, with its passwords, **outside the project and backed up** —
   losing it means you can no longer publish updates.

   ```bash
   keytool -genkeypair -v -keystore ~/keys/veyra-release.jks -alias veyra \
       -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties`:

   ```properties
   storeFile=/home/you/keys/veyra-release.jks
   storePassword=…
   keyAlias=veyra
   keyPassword=…
   ```

Without `keystore.properties`, `assembleRelease` still builds, but the APK is unsigned.

## Architecture

Single Gradle module, no dependency-injection framework, no navigation library: the app is small
enough that plain constructors, `ViewModel` factories and saveable state stay readable.

```
Compose UI ── ViewModels ── PlayerConnection ── MediaController
                  │                                   │  (IPC)
                  │                     PlaybackService (MediaSessionService)
                  │                       └─ MediaSession ── ExoPlayer ── Equalizer / BassBoost
                  ├─ MediaStore (tracks, images)
                  ├─ Room (playlists, per-track settings)
                  └─ DataStore (preferences)
```

Key decisions:

- **The service owns the player.** The UI only talks to it through a `MediaController`, so
  playback survives the activity and works from the notification and lock screen.
- **One conversion point.** `Track.toMediaItem(clip)` is the only place a track becomes a
  `MediaItem`; per-track and global start/end are applied there with `ClippingConfiguration`.
- **Stable track identity.** `TrackKey` (MediaStore id + folder, file name, size, duration)
  re-associates playlists and per-track settings after re-indexing, moves or re-tagging.
- **Explicit migrations.** Room schemas are exported to `app/schemas`; there is no destructive
  fallback.
- **No IO on the main thread.** MediaStore, Room, image decoding and file reads run on
  `Dispatchers.IO` / `Default`; `StrictMode` checks debug builds.

| Stack | |
|---|---|
| Language | Kotlin 2.2, Coroutines / Flow |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 1.11 (ExoPlayer, Session, Transformer) |
| Storage | Room 2.8 (KSP), DataStore Preferences 1.2 |
| Localisation | AppCompat per-app languages (English, Portuguese) |
| Build | Gradle Kotlin DSL, version catalog, R8 |

## Testing

```bash
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew connectedDebugAndroidTest    # instrumented tests (device or emulator)
./gradlew lintDebug
```

- **Unit tests:** start/end validation and priority, `TrackKey` resolution, library sorting and
  filters, artwork fallbacks and letterbox trimming, equalizer settings, property formatting,
  ViewModels (library, playback queue requests), WCAG contrast of both themes.
- **Instrumented tests:** playlist DAO, Room migrations 1 → 2 → 3 (`MigrationTestHelper`), the
  `Track → MediaItem` conversion and the ringtone excerpt export.

## Project structure

```
app/src/main/java/io/github/admilsonlandim/veyra/
├── data/
│   ├── audio/       equalizer settings and device capabilities
│   ├── clips/       start/end model, validation and priority rules
│   ├── db/          Room database, entities, DAOs, migrations
│   ├── media/       MediaStore repository, Track, TrackKey, artwork loading
│   ├── playlists/   playlist rules (names, ordering)
│   └── settings/    DataStore preferences
├── playback/        PlaybackService, PlayerConnection, MediaItem mapping, audio effects
└── ui/
    ├── clips/       start/end editor
    ├── library/     library screen and ViewModel
    ├── options/     track options and properties
    ├── player/      mini-player, full player, gestures
    ├── playlists/   playlists screens and dialogs
    ├── settings/    settings screen and sections
    └── theme/       palette, typography, shapes, brand components
```

## Roadmap

Planned for version 1.1 (not implemented yet):

- Search in the library and a folder view.
- Delete a track from the device (with confirmation).
- Handle badly encoded ID3 tags.
- Sleep timer.

## License

Copyright © 2026 Admilson Landim. All rights reserved.

This is proprietary software: the source code is not licensed for copying, modification or
redistribution. See [LICENSE](LICENSE).

---

## Resumo em português

O **Veyra** é um leitor de música Android minimalista e privado para os ficheiros de áudio do
dispositivo. Sem contas, sem anúncios, sem streaming e sem acesso à internet.

O seu ponto forte é o **controlo do início e do fim de cada música**: recortar a introdução longa
de uma faixa, terminar outra antes do fade-out, ou aplicar uma regra global a toda a biblioteca —
sem nunca alterar os ficheiros.

**Funcionalidades principais:** biblioteca com ordenação e exclusão de gravações; reprodução em
segundo plano com notificação, ecrã bloqueado, auscultadores e Bluetooth; capas embebidas ou a
partir de imagens do dispositivo; playlists; início e fim por faixa e regra global; propriedades e
definir como toque; saltar silêncio, equalizador e reforço de graves; tema escuro e claro; inglês e
português.

**Requisitos:** Android 7.0 ou superior. Para compilar: Android Studio, JDK 17+ e SDK 37.

**Compilar:** `./gradlew assembleDebug` (debug) ou `./gradlew assembleRelease` (release
minificada, assinada com a keystore indicada em `keystore.properties`, que nunca vai para o
repositório).

A descrição detalhada de cada funcionalidade está em [Funcionalidade.md](Funcionalidade.md).

**Licença:** software proprietário, todos os direitos reservados (ver [LICENSE](LICENSE)).
