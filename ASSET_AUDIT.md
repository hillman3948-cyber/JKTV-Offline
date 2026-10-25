# Local asset audit

Scanned the named Desktop project folder recursively on 2026-10-25. This is a read-only scan; it did not edit, move, or delete any source asset.

| Type | Count | Total bytes |
| --- | ---: | ---: |
| `.mid` | 47,997 | 2,160,299,040 |
| `.midi` | 0 | 0 |
| `.kar` | 0 | 0 |
| `.sf2` | 4 | 494,079,872 |
| `.sf3` | 0 | 0 |
| **Total supported assets** | **48,001** | **2,654,378,912** |

All 47,997 MIDI files began with a Standard MIDI `MThd` header. All four SF2 files began with the RIFF `sfbk` signature. No invalid headers were detected. This is a header check, not a complete MIDI track or SoundFont chunk parser.

Compared with the previous audit, the net change is **+35,152 `.mid` files** and **+1,546,606,526 bytes**. The scan found 29 exact SHA-256 duplicate pairs (58 files total), up from 12 pairs in the previous audit. These are binary-identical files at different paths; the packager preserves both paths so the source folder structure stays intact. Because the previous audit retained totals rather than a full path-and-hash index, this is a net count change and cannot distinguish newly added files from any files replaced or removed since then.

The default SoundFont is `KaraOke_King_v2.sf2` (98,645,536 bytes). The release packager includes all four SF2 files: `Arachno SoundFont - Version 1.0.sf2` (155,405,818 bytes), `Dream SAM2634 (ver. 2.1).sf2` (141,454,402 bytes), `KaraOke_King_v2.sf2` (98,645,536 bytes), and `megasoundfont.sf2` (98,574,116 bytes).
