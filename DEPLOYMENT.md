# Deployment: GitHub app releases and Cloudflare R2 library

## Before publishing

1. Sign in to GitHub CLI (`gh auth login`). The public repository is `hillman3948-cyber/JKTV-Offline`; app downloads are public, so a private repository cannot serve first-launch downloads without putting a secret in the APK.
2. Review [ASSET_AUDIT.md](ASSET_AUDIT.md). The owner confirmed permission to redistribute the 47,997 MIDI files and all four SF2 SoundFonts.
3. Create one long-lived Android release keystore, keep its backup offline, and add these Actions secrets: `ANDROID_KEYSTORE_B64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD`. Never commit the keystore or passwords. The existing debug APK is not a production-signed release.
4. Push the Android source to the repository's `main` branch. `android-ci.yml` builds a debug APK on pushes and pull requests.

The Android source and APK release metadata stay on GitHub. The MIDI/KAR packages, SoundFonts, background videos, and library manifest are hosted in the Cloudflare R2 bucket `jktv-offline-library`; downloads are public to anyone who has a file URL. Keep `source-index.json` private and out of the bucket. A production APK release still requires the signing secrets above.

## Configure the R2 library endpoint

The app reads the manifest URL from the Gradle property `jktvLibraryManifestUrl` (or environment variable `JKTV_LIBRARY_MANIFEST_URL`). The current public R2 manifest address is `https://pub-b98e45182dd74f2d84728bc81bad6809.r2.dev/library-manifest.json`. For a local APK build, use:

```powershell
gradle --no-daemon :app:assembleDebug -PjktvLibraryManifestUrl='https://pub-b98e45182dd74f2d84728bc81bad6809.r2.dev/library-manifest.json'
```

Do not use the S3 API endpoint or dashboard object-detail links. Use the public bucket URL, which points to `library-manifest.json`. Manifest package `url` fields can be relative paths; the Android installer resolves them against the manifest's public URL. Keep the BGV objects under the `bgv/` prefix and upload the updated `library-manifest.json` at the bucket root. The app then downloads and verifies each video with the song and SoundFont packages. Never put R2 S3 access keys in the APK.
## Build and release APKs

The `app-release.yml` workflow builds on tags like `app-v1.0.0`, signs the APK using the four Actions secrets, verifies the signature, creates a SHA-256 file, and publishes a GitHub prerelease. App releases are prereleases so the stable `latest` release can remain the current library release.

Use a monotonically increasing `versionCode` for every uploaded APK. The workflow uses its increasing Actions run number. Keep the Android application ID `com.jktv.offline` and the release signing key unchanged so Android can install updates over an existing app while preserving its data.

## Package and publish library updates

The packager defaults to the Desktop asset folder, includes all `.sf2` files, indexes `.mp4` files from `bgv/`, and packages only new or modified songs relative to the previous source index. It maintains a cumulative manifest and adds deletion tombstones. Upload new or changed video files from the local `bgv/` folder under the R2 `bgv/` prefix, then replace the root `library-manifest.json`; GitHub may continue to host Android source and APK releases.

Prepare the first release locally:

```powershell
./tools/publish-github-assets.ps1 `
  -Version 1.0.0 `
  -RepoOwner hillman3948-cyber `
  -SourceRoot 'C:\Users\hillm\OneDrive\Desktop\JKT Offiline'
```

Review the generated `build/assets/library-v1.0.0/library-manifest.json` and ZIPs. After confirming redistribution rights and repository access, publish explicitly:

```powershell
./tools/publish-github-assets.ps1 `
  -Version 1.0.0 `
  -RepoOwner hillman3948-cyber `
  -SourceRoot 'C:\Users\hillm\OneDrive\Desktop\JKT Offiline' `
  -Publish -IConfirmRedistributionRights
```

For the next release, pass the previous manifest and source index:

```powershell
./tools/publish-github-assets.ps1 `
  -Version 1.1.0 `
  -RepoOwner hillman3948-cyber `
  -SourceRoot 'C:\Users\hillm\OneDrive\Desktop\JKT Offiline' `
  -PreviousManifestPath '.\build\assets\library-v1.0.0\library-manifest.json' `
  -PreviousIndexPath '.\build\assets\library-v1.0.0\source-index.json'
```

The legacy `publish-github-assets.ps1` script still invokes `gh` only when `-Publish` is supplied. The current app should be built with the R2 manifest URL from the Cloudflare dashboard. The old GitHub library URL remains:

```text
https://github.com/hillman3948-cyber/JKTV-Offline/releases/latest/download/library-manifest.json
```

Each release asset must be under 2 GiB. GitHub documents no aggregate release-asset storage or bandwidth cap. See [GitHub's release limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## App update behavior

The app's home-screen refresh button checks public GitHub `app-v*` releases. It downloads the release APK, compares SHA-256 with release metadata, and opens Android's package installer. Android shows the user an install confirmation and validates the signing certificate. If Android blocks app installs, grant the per-app “Install unknown apps” permission, return to JKTV, and tap Install Update again.

Android preserves app data during an in-place update only when the package ID and signing certificate match. The Desktop debug APK and current development build share a local debug certificate; production signing must use that same certificate for direct updates from that specific installed APK, or users must transition once to a newly signed installation. Do not publish a differently signed update as a seamless upgrade. Keep the package ID and certificate stable thereafter.

Set `jktvGithubRepository=hillman3948-cyber/JKTV-Offline` in local Gradle properties for local builds. GitHub Actions supplies `GITHUB_REPOSITORY` automatically, so CI and tagged releases embed the workflow's repository.
