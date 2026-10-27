"""Encrypted receiver smoke probe. Uses synthetic lyrics, never real media or audible playback.
Requires Python cryptography and an already running emulator with JKTV TV Stage open.
Usage: py -3 tools/cast_receiver_smoke.py --adb PATH --serial emulator-5580
"""
import argparse, hashlib, hmac, json, os, pathlib, re, socket, struct, subprocess, threading, time
import xml.etree.ElementTree as ET
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--out", default="casting-validation")
parser.add_argument("--width", type=int, default=1280)
parser.add_argument("--height", type=int, default=720)
parser.add_argument("--density", type=int, default=160)
args = parser.parse_args()
out = pathlib.Path(args.out)
out.mkdir(exist_ok=True)
def adb(*cmd):
    return subprocess.check_output([args.adb, "-s", args.serial, *cmd], stderr=subprocess.STDOUT, timeout=60)
adb("shell", "am", "force-stop", "com.jktv.offline")
adb("shell", "wm", "size", f"{args.width}x{args.height}")
adb("shell", "wm", "density", str(args.density))
adb("shell", "am", "start", "-n", "com.jktv.offline/.cast.TvReceiverActivity")
time.sleep(2)
adb("shell", "uiautomator", "dump", "/sdcard/jktv-pair.xml")
xml = adb("shell", "cat", "/sdcard/jktv-pair.xml")
texts = [n.get("text", "") for n in ET.fromstring(xml).iter("node")]
secret = bytes.fromhex(next(t for t in texts if re.fullmatch(r"[0-9a-f]{4}( [0-9a-f]{4}){7}", t)))
port = int(next(t for t in texts if t.startswith("Manual address:")).rsplit(":", 1)[1])
adb("forward", "tcp:55777", f"tcp:{port}")
class Wire:
    def __init__(self):
        self.sock = socket.create_connection(("127.0.0.1", 55777), timeout=10)
        local = os.urandom(32)
        self.sock.sendall(struct.pack(">I", 0x4a4b5401) + local)
        assert struct.unpack(">I", self.read(4))[0] == 0x4a4b5401
        remote = self.read(32)
        self.aes = AESGCM(hmac.new(secret, remote + local, hashlib.sha256).digest())
        self.sent = self.received = 0
        self.send({"type": "hello", "version": 1})
        assert self.receive()["type"] == "hello"
    def read(self, size):
        result = b""
        while len(result) < size:
            part = self.sock.recv(size - len(result))
            if not part: raise EOFError()
            result += part
        return result
    def send(self, value):
        data = self.aes.encrypt(struct.pack(">IQ", 0, self.sent), json.dumps(value).encode(), None)
        self.sent += 1
        self.sock.sendall(struct.pack(">I", len(data)) + data)
    def receive(self):
        size = struct.unpack(">I", self.read(4))[0]
        assert 16 <= size <= 2 * 1024 * 1024
        value = self.aes.decrypt(struct.pack(">IQ", 1, self.received), self.read(size), None)
        self.received += 1
        return json.loads(value)
    def request(self, value):
        self.send(value)
        reply = self.receive()
        assert reply["type"] == "ok", reply
        return reply

settings = dict(
    lyricFontSizeScale=1.15, lyricPaletteName="CLASSIC_KARAOKE", visualizerStyle="NEON_BARS",
    twoLineKaraokeLayout=True, lyricFontFamily="DEFAULT", lyricFontWeight="BLACK",
    lyricLetterSpacing=0, lyricLineSpacing=14, lyricTextAlignment="CENTER", lyricStagePosition="CENTER",
    lyricOutlineThickness=1.5, lyricShadowIntensity=1, lyricDisplayMode="TWO_LINE",
    backgroundBrightness=1, lyricsScrimAlpha=.4, visualizerSensitivity=1, visualizerSpeed=1,
    visualizerBrightness=1, visualizerLyricsSafeZone=True, visualizerGlowStrength=1,
    visualizerPerformanceMode=False, customActiveFillHex="#00F0FF", customActiveGlowHex="#FFD600",
    customUnsungFillHex="#FFFFFF", customNextLineFillHex="#B5C7E8", karaokeMode="SOLO",
    singer="Stage test", partner="", title="JKTV receiver smoke test", key=0, melody=True,
    mixer=dict(gain=.85, melody=1, drums=1, bass=1, accompaniment=1, reverb=.45, room=.6, chorus=.25, quality=True),
    assets=dict(midi="", font="", video="")
)
lyrics = dict(source="USER_SYNCED_SIDECAR", confidence="ACCURATE_SYLLABLE", lines=[
    dict(index=0, start=0, end=10000, text="Independent TV Stage", syllables=[
        ["Independent ",0,4000,0,12,True],["TV ",4000,6000,12,15,True],["Stage",6000,10000,15,20,True]]),
    dict(index=1, start=11000, end=16000, text="Phone stays in control", syllables=[
        ["Phone stays in control",11000,16000,0,22,True]])
])
wire = Wire()
wire.request(dict(type="settings", data=settings))
wire.request(dict(type="lyrics", data=lyrics))
stop = threading.Event()
failures = []
latencies = []
def ticks():
    try:
        while not stop.is_set():
            start = time.monotonic()
            wire.request(dict(type="tick", position=2500, duration=20000, playing=False,
                              tempo=1, audio=False, transit=0, offset=0, levels=[.25] * 16))
            latencies.append((time.monotonic() - start) * 1000)
            stop.wait(.05)
    except Exception as error:
        failures.append(repr(error))
worker = threading.Thread(target=ticks)
worker.start()
try:
    for width, height in [(args.width, args.height)]:
        time.sleep(1)
        adb("shell", "screencap", "-p", "/sdcard/jktv-stage.png")
        adb("pull", "/sdcard/jktv-stage.png", str(out / f"stage-{width}x{height}.png"))
    stop.set()
    worker.join(5)
    assert not failures, failures
    wire.sock.close()
    time.sleep(.3)
    wire = Wire()
    wire.request(dict(type="tick", position=2500, duration=20000, playing=False,
                      tempo=1, audio=False, transit=0, offset=0, levels=[0] * 16))
    wire.request(dict(type="disconnect"))
    wire.sock.close()
    result = dict(scope="emulator synthetic Stage only, no audio/media/Wi-Fi validation",
                  resolution=f"{args.width}x{args.height}",
                  encrypted_reconnect=True, disconnect=True, ticks=len(latencies),
                  adb_forward_round_trip_ms_mean=round(sum(latencies)/len(latencies),2))
    (out / f"smoke-results-{args.width}x{args.height}.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
finally:
    stop.set()
    worker.join(5)
    wire.sock.close()
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")
    adb("forward", "--remove", "tcp:55777")

