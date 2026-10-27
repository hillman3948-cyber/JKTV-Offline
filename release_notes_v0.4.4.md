# JKTV Offline Karaoke v0.4.4

This release adds automatic update notifications and improves Stage MIDI playback,
alongside the v0.4.4 interface and casting improvements.

- **Automatic update modal:** JKTV checks for a new stable release when the app
  opens or returns to the foreground, then every 15 minutes while it stays open.
  A new release shows an on-screen modal with release notes, Download & Verify,
  and Later. Later snoozes that build for 24 hours; the manual update button is
  still available at any time. Offline checks stay quiet and retry when online.
- **Reliable update detection:** newer Android build numbers are detected even
  when the version remains v0.4.4. Drafts, prereleases, older builds, and releases
  for other app packages are excluded. App updates require app-version.json.
- **Stage MIDI fidelity:** song-authored volume, expression, sustain, bank,
  pitch bend and pressure messages now reach FluidSynth. Mixer faders preserve
  note velocity and instrument tone. Seeking restores MIDI controller state,
  and transport changes stop pedal-held notes.
- **Cleaner audio levels:** conservative synth gain and stereo peak protection
  reduce digital clipping on local and browser/TV Stage playback.
- **v0.4.4 interface:** compact header, live settings previews, theme/accent
  choices, and responsive phone/tablet layouts are retained.
- **Offline karaoke:** existing song, SoundFont, lyric, playlist and casting
  features remain available. Checking/downloading updates requires internet;
  Android still asks before installing an APK.

Validation: automated regression tests and three procedural arrangements rendered
with desktop FluidSynth. Physical phone/Bluetooth/TV listening and the automatic
modal on a connected Android device have not yet been verified. Audio quality
also depends on the SoundFont, source MIDI, output device and speakers.

For existing users, install the APK over the current app with the same package
and original signing certificate. Keep the app installed to preserve its data.
