"""Verify packaged Android MIDI bridge ABIs and defined FluidSynth C exports."""
import json
import struct
import sys
import zipfile


def exports(data):
    assert data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian ELF'
    wide = data[4] == 2
    offset = struct.unpack_from('<Q' if wide else '<I', data, 40 if wide else 32)[0]
    size, count = struct.unpack_from('<HH', data, 58 if wide else 46)
    fmt = '<IIQQQQIIQQ' if wide else '<IIIIIIIIII'
    sections = [struct.unpack_from(fmt, data, offset + n * size) for n in range(count)]
    names = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM
            continue
        strings = sections[section[6]]
        table = data[strings[4]:strings[4]+strings[5]]
        for at in range(section[4], section[4]+section[5], section[9]):
            symbol = struct.unpack_from('<IBBHQQ' if wide else '<IIIBBH', data, at)
            info, index = (symbol[1], symbol[3]) if wide else (symbol[3], symbol[5])
            if index and info >> 4 in (1, 2):
                start = symbol[0]
                names.add(table[start:table.index(b'\0', start)].decode())
    return names


required = {'fluid_synth_cc', 'fluid_synth_pitch_bend', 'fluid_synth_channel_pressure',
            'fluid_synth_key_pressure', 'fluid_synth_system_reset', 'fluid_synth_all_sounds_off'}
abis = {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
with zipfile.ZipFile(sys.argv[1]) as apk:
    packaged = {p.split('/')[1] for p in apk.namelist() if p.startswith('lib/') and p.endswith('.so')}
    assert packaged == abis, f'Unexpected or incomplete native architectures: {packaged}'
    for abi in sorted(abis):
        for library in ('libfluidsynth_jni.so', 'libjnidispatch.so'):
            assert f'lib/{abi}/{library}' in apk.namelist(), (abi, library)
        missing = required - exports(apk.read(f'lib/{abi}/libfluidsynth.so'))
        assert not missing, (abi, missing)
print(json.dumps({'abis': sorted(abis), 'requiredExports': sorted(required),
                  'status': 'passed', 'scope': 'Static APK/ELF verification; no Android execution'}, indent=2))
