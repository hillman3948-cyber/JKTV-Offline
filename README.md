# JKTV Offline Karaoke

JKTV is an Android karaoke player designed to download a MIDI/KAR library and a SoundFont once, verify them, then play them offline. The Android project includes Compose screens, a Room catalog and singer queue, a resumable HTTPS asset installer, incremental package handling, SHA-256 validation, and GitHub release automation.

v0.4.4 includes automatic update prompts and Stage MIDI audio corrections. See [AUTOMATIC_UPDATE_REPORT.md](AUTOMATIC_UPDATE_REPORT.md), [STAGE_MIDI_AUDIO_FIX_V2.md](STAGE_MIDI_AUDIO_FIX_V2.md), and [TV_AUDIO_WIPE_FIX_REPORT.md](TV_AUDIO_WIPE_FIX_REPORT.md). Browser source edits require `node tools/build_stage_assets.cjs`; generated assets are included for normal Gradle builds.

## Current state

- The project builds a debug APK. The APK is a development build, not a production-signed release.
- The downloadable library manifest lists 47,997 songs and four SoundFonts. Media files are downloaded separately and are not included in this source checkout.
- The public GitHub repository is `hillman3948-cyber/JKTV-Offline`. Karaoke assets are published as release downloads, not committed into Git history.
- The app checks for compatible stable releases on foreground entry and every 15 minutes while open. A new build opens a modal; Later snoozes it for 24 hours. The home screen also supports manual APK and library checks. APKs are SHA-256 checked before Android's installer opens; library packages have resumable downloads, SHA-256 checks, and staged installation.
- MIDI/KAR playback is powered on-device by FluidSynth using the installed SF2 SoundFont. Song playback, pause, seek, and timed MIDI lyrics work offline after library setup.
- FluidSynth and wrapper license texts are bundled in the app under `assets/licenses/`.
- Production in-place APK updates require one stable release keystore. The workflow expects its credentials as GitHub Actions secrets and will not publish a tagged APK without them.

## GitHub release layout

The repository contains Android source, workflow files, manifests, documentation, and developer tooling. Karaoke files are excluded from Git and hosted in the Cloudflare R2 bucket `jktv-offline-library`; the Android source and APK releases remain on GitHub. The app reads the library manifest from Cloudflare R2 at `https://pub-b98e45182dd74f2d84728bc81bad6809.r2.dev/library-manifest.json`; the URL can be overridden with `jktvLibraryManifestUrl` during the build.

See [DEPLOYMENT.md](DEPLOYMENT.md) for GitHub release and Android build instructions. The repository is public so the app can download assets without embedded credentials. The owner confirmed permission to redistribute these songs and SoundFonts.

## Build locally

Open this directory in Android Studio or use Gradle 8.11.1 with Java 17 and Android SDK Platform 35:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug -PjktvBuildDirectory=build
```

The debug output is `app/build/outputs/apk/debug/app-debug.apk`. CI builds and attaches the APK as a temporary workflow artifact. A signed release is produced only from an `app-vX.Y.Z` tag after the signing secrets are configured.

## v0.4.4 publication status

The source and release notes are uploaded. The v0.4.4 release is a draft pending original-key signing; no installable public APK is attached yet. Configure ANDROID_KEYSTORE_B64, ANDROID_KEY_ALIAS, ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD before publishing an app-v0.4.4 tag. See [publication instructions](GitHub-v0.4.4-release-copy/README.md).
