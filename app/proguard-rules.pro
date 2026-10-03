# Release builds drop verbose, debug and info logging (it includes installed app names);
# warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
