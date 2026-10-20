# Changelog

All notable changes to Veyra. Versions follow `MAJOR.MINOR.PATCH`; every Play upload also raises
`versionCode` by one.

## [1.0.0] — 2026-10-20

First public release.

### Library
- Music from MediaStore, refreshed automatically; recordings, voice notes, ringtones, alarms and
  podcasts left out; editable excluded folders; optional "hide short tracks".
- Browse by tracks, folders or artists; sort by title, artist, album, date added or length.
- Search as you type in titles, artists, albums and folder names, ignoring accents and case.
- Clean names from file names when tags are missing or are just the file name; repair of tags
  saved with the wrong encoding ("JoÃ£o" → "João"). Display only.
- Covers: embedded; on Android 12 and older also an image next to the file, a folder cover or a
  VidMate thumbnail (no `READ_MEDIA_IMAGES`, per Google Play's photo policy).

### Playback
- Background playback with notification and lock-screen controls (Media3).
- The last queue, track and position are restored when the app is reopened.
- Shuffle, repeat one/all, swipe the cover to change track, landscape layout.
- Start and end per track, a global "skip the first / last seconds" rule, skip silence.
- Sleep timer: 15–90 minutes, custom or end of track, with a 20-second fade-out.

### Sound
- Equalizer with the device's bands and presets, bass boost, system effects panel.
- Balanced volume: ReplayGain (attenuation only) or a gentle leveller.

### More
- Playlists: create, rename, reorder, remove tracks, delete.
- Track properties; set a song, or part of it, as ringtone, notification or alarm sound.
- Remove a track from the device: system trash on Android 11+, permanent delete before, always
  with the app's confirmation; queue, playlists and start/end settings are cleaned up.
- Dark and light themes; English and Portuguese.
- No ads, no account, no internet permission.

## Ideas for 1.1 (by priority)

1. Test on Android 7 and on Android 11+ (trash path) emulators before the next release.
2. Covers from separate images on Android 13+ without `READ_MEDIA_IMAGES` (for example, the user
   picks the cover folder once with the Storage Access Framework).
3. Multi-select in the library to add to a playlist or remove several tracks at once.
4. ReplayGain from MP4/M4A atoms and Opus R128 gains.
5. Home-screen widget and Android Auto browsing.
6. Backup and restore of playlists and settings to a file chosen by the user.
