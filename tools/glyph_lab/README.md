# Glyph lab

Desktop prototype of the generated-glyph method (colour keying, vector vs image), used to tune
`GlyphExtractor`/`ColorKey` against real device icons. The Kotlin code is the source of truth;
this Python copy mirrors it, but guesses vector vs image from the colour count (Kotlin uses the
drawable classes) and resizes legacy icons slightly differently.

Needs `numpy` and `pillow` (e.g. `uv run --with numpy --with pillow python ...`).

```sh
# 1. Dump raw icon layers + current glyphs from the phone. Runs inside the app process with no
#    UI (HyperOS blocks background broadcasts to apps without Autostart permission):
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.abhay.hypericon.debug.IconDumpTest
#    optionally: -Pandroid.testInstrumentationRunnerArguments.packages=a.b,c.d
adb pull /sdcard/Android/data/dev.abhay.hypericon/files/icon-dump dump

# 2. Run the prototype and build a contact sheet (original | on-device glyph | prototype glyph):
python tools/glyph_lab/simple.py dump out [packages...]
python tools/glyph_lab/sheet.py dump out sheet.png [packages...]
```
