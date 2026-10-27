# Automatic updates and GitHub v0.4.4 publication copy

Implemented in MainActivity/KaraokeViewModel using the existing app-wide Compose
update modal. No recurring OS automation, service, background APK installation,
or automatic download is used.

Behavior:

- Check for a compatible new stable app release on foreground entry when due,
  then every 15 minutes while the app stays foregrounded. A lightweight minute
  tick also permits retry when connectivity returns. Network calls run on IO.
- Wait for first-time library setup to finish. Stop the automatic polling job
  when the main activity leaves the foreground; resume on return.
- Stay quiet without validated internet, on errors, or when already up to date.
  After an error, retry after five minutes. Manual checks still show their status.
- Display the existing update modal when a new build is found. It works over
  the main screens, including the full-screen Stage. Later dismisses it and
  persists a 24-hour snooze for that build. A different new build can notify sooner.
- Preserve the manual update button, library update checks, SHA-256 download
  verification and Android's user-controlled installation flow. Avoid overlapping
  checks and duplicate downloads. Do not claim a file is verified before download.

Release selection is now tested independently of Android networking. It skips
drafts, prereleases, library tags and other Android packages; requires valid
app-version.json metadata and an attached APK; compares version components
numerically; and requires a higher Android versionCode. A build 20 of v0.4.4 can
therefore update build 17 of the same version. Legacy APK-only releases without
metadata are deliberately not offered by the new checker because their package
and build compatibility cannot be established. Always attach app-version.json.

Build/validation:

- Version name 0.4.4, Android code 20, production package com.jktv.offline.
- All 56 unit tests passed in 12 suites, including 8 new update selection/snooze
  tests and all existing audio/Stage regression tests.
- assembleRelease and Android release-vital lint passed. The prepared APK is
  unsigned pending the original release key.
- The PowerShell finalizer parsed successfully and rejected a real APK signed
  with the incompatible local key without creating upload metadata.
- APK identity and packaged audio bridge ABIs verified separately.
- No Android phone/emulator is attached: on-device automatic-dialog display,
  lifecycle/connectivity behavior and package installation remain unverified.
  GitHub workflows were updated but have not been run on GitHub.

Publication copy:

GitHub-v0.4.4-release-copy contains the unsigned production APK, release notes,
reviewable source ZIP, README and Finalize-Release.ps1. The finalizer verifies
the original certificate and APK identity, then computes the signed APK's hash
and creates the three upload assets. It never generates metadata for an unsigned
or incompatible-key APK. Existing source files are backed up before replacement.

The existing public app-v0.4.2 metadata's APK hash matches the inspected local
v0.4.2 APK. Its certificate is bf9e3568126ed487c039b3708c094b42ffa8b302fd4caafb255e30fabaa3dd33;
the available local key is 9c973a4bf7dbbd34e16367c5b8ec00ece94a66b146cf402dc944f84c2f687dc6.
The original signing key or the repository's correctly configured signing secrets
are needed to publish an in-place upgrade for existing public users. They were
not available locally; no signed public-release replacement was fabricated.

The release workflow now uses explicit Android build-tools paths, the correct
artifact directory, tests before signing, CI build codes 20 + workflow run number,
original-certificate verification, notes-file support and stable publication for
stable tags. The local uploader uses paths relative to this project, validates
the finalized files, and requires the source tag to exist. It has not been run.
No release, tag, commit, repository setting, or GitHub asset has been changed.

Older installed apps must first install this feature-bearing build manually.
Source changes and an unpublished release bundle cannot make an old installed
APK start checking automatically.

## GitHub source validation

The exact GitHub checkout also passed its two retained legacy regression tests after adapting the LRC test to the separate sync resolver API: 58 tests total, zero failures. Full lintDebug and assembleDebug passed. Source upload and a draft release are authorized; signed publication remains pending the original key.

