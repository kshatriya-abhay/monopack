package dev.abhay.hypericon.mtz

/**
 * Icon folder names inside `icons/res/drawable-xxhdpi/`.
 *
 * HyperOS looks icons up by activity first: a launcher entry whose icon differs from the app's
 * (a second launcher activity, or an activity-alias used for icon switching) only picks up an
 * activity-qualified icon. Following MIUI's naming:
 * the class name if it starts with the package name, otherwise `<package>#<class>`.
 */
object MtzNaming {
    fun activityFolder(packageName: String, className: String): String =
        if (className.startsWith("$packageName.")) className else "$packageName#$className"

    /** Folders for one launcher entry: always activity-level, plus package-level for the main entry. */
    fun folders(packageName: String, className: String, isMainActivity: Boolean): List<String> = buildList {
        add(activityFolder(packageName, className))
        if (isMainActivity) add(packageName)
    }
}
