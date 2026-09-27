# HyperIcon

Material You themed icons for HyperOS, generated on-device and exported as an icons-only `.mtz` theme.

_More documentation to come._

## Credits

HyperIcon builds on, ports code from, or was informed by these open-source projects. Thank you!

| Project | License | Used for |
|---|---|---|
| [AOSP Launcher3 `iconloaderlib`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/) | Apache-2.0 | Port of `MonochromeIconFactory`, `IconNormalizer` and `BaseIconFactory.wrapToAdaptiveIcon` (generated themed icons); themed-icon colour tokens |
| [AOSP ThemePicker](https://android.googlesource.com/platform/packages/apps/ThemePicker/) | Apache-2.0 | Preset "basic colour" seeds |
| [AOSP frameworks/base](https://android.googlesource.com/platform/frameworks/base/) | Apache-2.0 | Default system palette values (to detect missing wallpaper colours) |
| [MaterialKolor](https://github.com/jordond/MaterialKolor) (`material-color-utilities`) | MIT | Tonal palettes and Material schemes from a seed colour; a port of Google's [material-color-utilities](https://github.com/material-foundation/material-color-utilities) (Apache-2.0) |
| [HyperIcons](https://github.com/stbenjam/HyperIcons) by Stephen Benjamin | MIT | Icons-only `.mtz` structure, activity-qualified icon names, applying via Theme Manager's `ApplyThemeForScreenshot` |
| [HyperMonetIconTheme](https://github.com/VincentAzz/HyperMonetIconTheme) | Apache-2.0 | Layered HyperOS icon format (`0.png`/`1.png`, 432 px), `transform_config.xml` and the HyperOS icon mask path |
