package app.pwhs.core.install

/**
 * Builds the `pm install-*` command lines the root installer runs.
 *
 * Extracted from [RootInstaller] as pure string functions so the flags — the user target in
 * particular — can be asserted in a unit test instead of on a rooted device.
 */
object PmInstallCommand {

    /**
     * Lowest API whose `pm` understands the symbolic user ids `all` and `current`.
     *
     * Before that, `pm install-create --user` was parsed with `Integer.parseInt`, so `--user all`
     * threw a NumberFormatException instead of installing anything.
     */
    const val MIN_SDK_FOR_SYMBOLIC_USER = 26

    /**
     * The `--user` argument for `pm install-create`, or null when the flag must be omitted.
     *
     * Omitted by default: `pm` already targets the calling user, and spelling that out as
     * `--user 0` is wrong on a device with a secondary profile — user 0 is the *owner*, not the
     * current user, and not "everyone".
     *
     * @param allUsers the user's "Install for all users" choice
     * @param sdkInt `Build.VERSION.SDK_INT`, injected so the fallback can be tested
     */
    fun userArgument(allUsers: Boolean, sdkInt: Int): String? = when {
        !allUsers -> null
        sdkInt >= MIN_SDK_FOR_SYMBOLIC_USER -> "--user all"
        // Older builds cannot express "all users"; installing for the calling user is the honest
        // fallback, and on the single-user TV boxes this path exists for it is the same thing.
        else -> null
    }

    /** `pm install-create` with the flags the user enabled in the TV install options. */
    fun installCreate(
        replace: Boolean,
        downgrade: Boolean,
        grantPermissions: Boolean,
        allowTest: Boolean,
        allUsers: Boolean,
        sdkInt: Int,
    ): String = buildString {
        append("pm install-create")
        if (replace) append(" -r")
        if (downgrade) append(" -d")
        if (grantPermissions) append(" -g")
        if (allowTest) append(" -t")
        userArgument(allUsers, sdkInt)?.let { append(' ').append(it) }
    }

    /** Writes one split into the session by path (the fast form, needs a `pm` new enough for it). */
    fun installWrite(sessionId: String, splitName: String, sizeBytes: Long, path: String): String =
        "pm install-write -S $sizeBytes $sessionId $splitName ${escapePath(path)}"

    /** Fallback for older `pm`: pipe the bytes in through stdin instead of passing a path. */
    fun installWriteViaStdin(sessionId: String, splitName: String, sizeBytes: Long, path: String): String =
        "cat ${escapePath(path)} | pm install-write -S $sizeBytes $sessionId $splitName -"

    fun installCommit(sessionId: String): String = "pm install-commit $sessionId"

    /**
     * Escapes a path for the shell `su` hands the command to. Staging paths are app-generated
     * (`cacheDir/root_install_<uuid>/base.apk`) so this is belt and braces, but a space in a
     * vendor's data path would otherwise split the argument in two.
     */
    fun escapePath(path: String): String = path.replace(" ", "\\ ")
}
