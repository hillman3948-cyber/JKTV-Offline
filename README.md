# JKTV Offline Karaoke

JKTV is an Android karaoke player designed to download a MIDI/KAR library and a SoundFont once, verify them, then play them offline. The Android project includes Compose screens, a Room catalog and singer queue, a resumable HTTPS asset installer, incremental package handling, SHA-256 validation, and GitHub release automation.

## Current state

- The project builds a debug APK. The APK is a development build, not a production-signed release.
- The Desktop asset folder currently contains 47,997 `.mid` files (about 2.16 GB) and four `.sf2` SoundFonts (about 494 MB). Header checks passed; they do not prove every file is fully parseable.
- The public GitHub repository is `hillman3948-cyber/JKTV-Offline`. Karaoke assets are published as release downloads, not committed into Git history.
- The home screen can check for signed APK and library releases. APKs are SHA-256 checked before Android's installer opens; library packages have resumable downloads, SHA-256 checks, and staged installation.
- The native MIDI synthesizer is still not integrated, so songs cannot play yet.
- Production in-place APK updates require one stable release keystore. The workflow expects its credentials as GitHub Actions secrets and will not publish a tagged APK without them.

## GitHub release layout

The repository contains Android source, workflow files, manifests, documentation, and developer tooling. Karaoke files are excluded from Git and uploaded as versioned GitHub Release assets. App releases use `app-vX.Y.Z`; library releases use `library-vX.Y.Z`. App releases are marked as prereleases so the stable `releases/latest/download/library-manifest.json` URL continues to resolve to the current library manifest.

See [DEPLOYMENT.md](DEPLOYMENT.md) for GitHub release and Android build instructions. The repository is public so the app can download assets without embedded credentials. The owner confirmed permission to redistribute these songs and SoundFonts.

## Build locally

Open this directory in Android Studio or use Gradle 8.11.1 with Java 17 and Android SDK Platform 35:

```powershell
gradle --offline --no-daemon :app:assembleDebug
```

The debug output is `app/build/outputs/apk/debug/app-debug.apk`. CI builds and attaches the APK as a temporary workflow artifact. A signed release is produced only from an `app-vX.Y.Z` tag after the signing secrets are configured.
