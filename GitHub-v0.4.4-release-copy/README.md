# GitHub publication copy: JKTV v0.4.4 (build 20)

Repository: hillman3948-cyber/JKTV-Offline
Tag: app-v0.4.4
Release title: JKTV Offline Karaoke v0.4.4
Android package: com.jktv.offline

Nothing has been published. The unsigned release build and copy-ready release
description are prepared. The original signing key is still required before
this can update the existing public GitHub APK. Do not publish an unsigned APK
or a replacement signed with a different key.

## Finish signing

Use Java 17 and Android SDK build-tools 35.0.0. Set the original keystore and key
passwords in local ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD environment
variables, then run:

```powershell
.\Finalize-Release.ps1 -KeystorePath 'C:\path\to\original.keystore' -KeyAlias 'your-original-alias'
```

If you have signed the APK elsewhere, run:

```powershell
.\Finalize-Release.ps1 -SignedApkPath 'C:\path\to\signed-v0.4.4.apk'
```

The script checks signature, certificate, app package, version and build number
before generating an `upload` folder containing exactly these three assets:

- JKTV-v0.4.4.apk
- JKTV-v0.4.4.apk.sha256
- app-version.json (hash and size calculated from the final signed APK)

The expected original certificate SHA-256 is
bf9e3568126ed487c039b3708c094b42ffa8b302fd4caafb255e30fabaa3dd33.
The local debug key has a different certificate and is intentionally rejected.
No keys or passwords are included in this publication copy.

## Publish manually when the upload folder is ready

Create a published stable GitHub release, choose tag `app-v0.4.4`, paste the
contents of `release_notes_v0.4.4.md`, and attach all three files from `upload`.
Do not mark the release as a draft or prerelease. Those releases are excluded
from automatic app checks. Do not attach the unsigned/aligned/candidate APKs.
Upload/commit the reviewed source changes first so the release tag points at the
code used to build this APK. If using the included GitHub signing workflow,
pushing the tag runs that workflow instead of requiring local signing.

Alternatively, after reviewing and signing the files, run from this folder:

```powershell
gh release create app-v0.4.4 upload/JKTV-v0.4.4.apk upload/JKTV-v0.4.4.apk.sha256 upload/app-version.json --repo hillman3948-cyber/JKTV-Offline --verify-tag --title 'JKTV Offline Karaoke v0.4.4' --notes-file release_notes_v0.4.4.md
```

The updated source is in `JKTV-v0.4.4-source.zip`; it contains no keystore,
local SDK settings, cached builds or downloaded test runtimes. Its GitHub release
workflow uses the four existing ANDROID_* signing secrets, a monotonically
increasing CI build code above 20, and stable releases for stable version tags.

Existing older apps without automatic checking still need a manual update to
this build first. After installation, checks run while the app is foregrounded:
on entry when due, then every 15 minutes, with quiet retries after failures.
First-time library setup is allowed to finish before automatic prompts appear.
Later snoozes that release for 24 hours; a different new build can notify sooner.
Download and installation remain user-controlled. No background installation or
automatic APK download is added.
