# TFM TV

Android TV / Fire TV client for Telegram File Manager. Browse your channels on the big
screen and play the videos stored in them.

## Features

- **Channels split by section**: mine, shared, favourites, Telegram chat folders, and all.
- **Folder navigation** inside each channel, showing only videos.
- **All videos** view: every video in the channel, newest first.
- **Messages** view: channel messages that carry a video, newest first, with previews.
- **Video previews** extracted from the stream itself (the server exposes no thumbnail
  endpoint), cached on disk and switchable off for low-powered sticks.
- **Playback choice**: built-in player (ExoPlayer + FFmpeg software decoders via NextLib),
  VLC or any other installed player, the system default, or ask every time.
- D-pad first UI built with Compose for TV.

## Requirements

- Android 6.0 (API 23) or later — covers Fire TV sticks from 2015 onwards.
- A reachable Telegram File Manager server.

## Setup

On first launch enter the server address (`192.168.1.10:5257` is enough, `http://` is
added automatically) and the API key if the server requires one.

## Build

```bash
./gradlew :app:assembleDebug
```

Release builds are signed from environment variables, so no credentials live in the
repository:

| Variable            | Meaning                         |
| ------------------- | ------------------------------- |
| `KEYSTORE_PATH`     | path to the `.jks`              |
| `KEYSTORE_PASSWORD` | keystore password               |
| `KEY_ALIAS`         | key alias (defaults to `tfmtv`) |
| `KEY_PASSWORD`      | key password                    |

Without `KEYSTORE_PATH` the release build stays unsigned, which keeps local builds working.

## Releases

Every push to `main` runs `.github/workflows/release.yml`, which builds a signed APK and
publishes a GitHub Release tagged with the `versionName` from `app/build.gradle.kts`.
The workflow reads the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` and
`KEY_ALIAS` repository secrets.

Run `setup-signing.ps1` once to generate the keystore and upload those secrets. That
script and the keystore it produces are gitignored.
