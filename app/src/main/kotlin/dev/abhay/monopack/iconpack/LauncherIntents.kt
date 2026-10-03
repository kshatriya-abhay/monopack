package dev.abhay.monopack.iconpack

/**
 * How launchers discover icon packs: `queryIntentActivities` for these actions (and the Apex
 * category). The pack's single activity declares all of them.
 *
 * The list follows CandyBar's template manifest (Apache-2.0) and Alembicons' `IconPackBuilder`
 * (GPL-3.0), minus launcher-entry and icon-picker actions (`ACTION_PICK_ICON`), which expect an
 * activity that returns a chosen icon; the pack has no code.
 */
object LauncherIntents {
    /** One intent filter: its actions and categories. */
    data class Filter(val actions: List<String>, val categories: List<String>)

    private const val MAIN = "android.intent.action.MAIN"
    private const val DEFAULT = "android.intent.category.DEFAULT"

    val FILTERS: List<Filter> = listOf(
        Filter(listOf("org.adw.launcher.THEMES"), listOf(DEFAULT)), // ADW, and most launchers' fallback
        Filter(listOf("com.novalauncher.THEME"), listOf(DEFAULT)), // Nova
        Filter(listOf("com.teslacoilsw.launcher.THEME"), listOf(DEFAULT)), // Nova (legacy)
        Filter(listOf(MAIN), listOf("com.anddoes.launcher.THEME")), // Apex
        Filter(listOf("com.anddoes.launcher.THEME"), listOf(DEFAULT)), // Apex
        Filter(listOf(MAIN, "com.gau.go.launcherex.theme", "com.zeroteam.zerolauncher.theme"), listOf(DEFAULT)), // GO, Zero
        Filter(listOf("com.fede.launcher.THEME_ICONPACK"), listOf(DEFAULT)), // LauncherPro
        Filter(listOf("ch.deletescape.lawnchair.ICONPACK"), listOf(DEFAULT)), // Lawnchair (legacy)
        Filter(listOf("app.lawnchair.icons.THEMED_ICON"), listOf(DEFAULT)), // Lawnchair
        Filter(listOf("ginlemon.smartlauncher.THEMES"), listOf(DEFAULT)), // Smart Launcher
        Filter(listOf("com.motorola.launcher.ACTION_ICON_PACK", "com.motorola.launcher3.ICON_PACK_CHANGED"), listOf(DEFAULT)), // Moto
        Filter(listOf("com.sonymobile.home.ICON_PACK"), listOf(DEFAULT)), // Sony
        Filter(listOf(MAIN, "com.lge.launcher2.THEME"), listOf(DEFAULT)), // LG
        Filter(listOf("com.gridappsinc.launcher.theme.apk_action"), listOf(DEFAULT)), // Nine
        Filter(listOf("com.tsf.shell.themes"), listOf(DEFAULT)), // TSF Shell
        Filter(listOf(MAIN, "com.vivid.launcher.theme"), listOf(DEFAULT)), // V Launcher
        Filter(listOf(MAIN, "home.solo.launcher.free.THEMES", "home.solo.launcher.free.ACTION_ICON"), emptyList()), // Solo
    )
}
