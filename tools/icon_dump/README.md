# Icon dump

Debug builds can dump every launcher app's raw icon layers (`bg.png`, `fg.png`, `mono.png` or
`legacy.png`, 432 px) and the generated glyph to
`/sdcard/Android/data/dev.abhay.monopack/files/icon-dump/<package>/`, which helps when checking how
icons come out.

```sh
# Runs inside the app process with no UI (HyperOS drops background broadcasts to apps without
# Autostart, and asks on the phone before installing the test APK the first time):
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.abhay.monopack.debug.IconDumpTest
#   optionally: -Pandroid.testInstrumentationRunnerArguments.packages=a.b,c.d

# Or, with Autostart allowed for Monopack (or while it's running):
adb shell am broadcast -n dev.abhay.monopack/.debug.IconDumpReceiver [--es packages a.b,c.d]

adb pull /sdcard/Android/data/dev.abhay.monopack/files/icon-dump dump
```
