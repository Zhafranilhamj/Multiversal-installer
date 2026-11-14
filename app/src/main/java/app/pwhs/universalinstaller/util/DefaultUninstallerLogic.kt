package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import androidx.core.net.toUri
import timber.log.Timber
import java.lang.reflect.InvocationTargetException

/**
 * Toggles the system's preferred-activity registration so our `DialogUninstallActivity` becomes
 * the default handler for `android.intent.action.UNINSTALL_PACKAGE` and `android.intent.action.DELETE`.
 * Ported the same way as [DefaultInstallerLogic], and subject to the same contract:
 *
 * **Everything here that has to work, throws when it doesn't.** Registering the preference used to
 * fail two ways that both reported success to the user: the competitor query could return an empty
 * set, in which case no preference was written at all (the caller still got a success toast and the
 * Settings toggle snapped back off, because the write-time `resolveActivity` probe saw no match);
 * and the `match` value was computed at runtime through a hidden `IntentFilter.match(...)` call,
 * whose result PackageManagerService is not guaranteed to match against. The candidate set is now
 * load-bearing and `match` is the fixed `MATCH_CATEGORY_SCHEME or MATCH_ADJUSTMENT_MASK` constant,
 * exactly as the installer path uses `MATCH_CATEGORY_TYPE`.
 *
 * Registration goes through [MimePreferredActivity], the same routine the installer uses —
 * `addPreferredActivity` with `removeExisting` on Android 12+, which replaces only this filter and
 * preserves this package's unrelated defaults. It is used instead of `replacePreferredActivity`
 * so the uninstaller path has exactly the registration behavior that is proven on-device.
 *
 * Caller must supply an [IPackageManager] obtained either via Shizuku binder wrapping (shell
 * UID 2000) or from inside a libsu RootService (UID 0). [hasSystemLevelPermission] controls
 * whether we also call the persistent variants — those require UID 0 and throw on shell.
 */
object DefaultUninstallerLogic {

    private const val SCHEME = "package"

    @Suppress("DEPRECATION")
    private val ACTIONS = arrayOf(Intent.ACTION_UNINSTALL_PACKAGE, Intent.ACTION_DELETE)

    fun setDefaultUninstaller(
        iPackageManager: IPackageManager,
        component: ComponentName,
        lock: Boolean,
        hasSystemLevelPermission: Boolean,
    ) {
        try {
            applyPreference(iPackageManager, component, lock, hasSystemLevelPermission)
        } catch (e: InvocationTargetException) {
            // Reflection buries the real failure — a SecurityException from PackageManagerService
            // reads as "InvocationTargetException" otherwise, in the log and in the toast.
            throw e.cause ?: e
        }
    }

    private fun applyPreference(
        iPackageManager: IPackageManager,
        component: ComponentName,
        lock: Boolean,
        hasSystemLevelPermission: Boolean,
    ) {
        val userId = Process.myUid() / 100000

        android.util.Log.i(
            "DefaultUninstallerLogic",
            "setDefaultUninstaller: component=${component.flattenToShortString()} lock=$lock userId=$userId systemLevel=$hasSystemLevelPermission",
        )
        Timber.d(
            "setDefaultUninstaller: component=%s lock=%b userId=%d systemLevel=%b",
            component.flattenToShortString(), lock, userId, hasSystemLevelPermission,
        )

        val systemPackages = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        if (!lock) clearPackagePreferredActivities(iPackageManager, component.packageName, userId, hasSystemLevelPermission)

        if (!lock) {
            if (hasSystemLevelPermission) {
                for (pkg in systemPackages) {
                    runCatching {
                        setComponentEnabled(iPackageManager, ComponentName(pkg, "com.android.packageinstaller.UninstallerActivity"), true, userId)
                    }
                }
            }
            return
        }

        for (action in ACTIONS) {
            val probeIntent = Intent(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                data = "package:com.example.placeholder".toUri()
            }

            // Load-bearing, not informational: the candidate set is stored alongside the
            // preference, and PackageManagerService drops a preference whose set no longer
            // matches the activities that currently handle the intent. An empty set therefore
            // registers a preference that is stale the moment it is written — accepted without
            // error, and with no effect. Which is the failure the success-without-effect bug reports.
            val competitors = queryIntentActivities(iPackageManager, probeIntent, userId)
            check(competitors.isNotEmpty()) {
                "queryIntentActivities returned nothing for $action — cannot build the candidate " +
                    "set a preferred activity is matched against"
            }
            android.util.Log.i("DefaultUninstallerLogic", "action=$action competitors count=${competitors.size}")
            val names = mutableListOf<ComponentName>()
            for (info in competitors) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                if (pkg != component.packageName && pkg != "android") {
                    // Best effort: another app keeping its own preference does not stop ours
                    // from being registered.
                    runCatching {
                        clearPackagePreferredActivities(iPackageManager, pkg, userId, hasSystemLevelPermission)
                    }.onFailure { Timber.w(it, "Could not clear competing preference for %s", pkg) }
                    if (hasSystemLevelPermission && pkg.contains("packageinstaller")) {
                        runCatching {
                            android.util.Log.i("DefaultUninstallerLogic", "disabling system uninstaller $pkg/$cls")
                            setComponentEnabled(iPackageManager, ComponentName(pkg, cls), false, userId)
                        }.onFailure {
                            android.util.Log.w("DefaultUninstallerLogic", "failed to disable $pkg/$cls", it)
                            Timber.w(it, "Could not disable system uninstaller component %s/%s", pkg, cls)
                        }
                    }
                }
                names.add(ComponentName(pkg, cls))
            }

            val filter = IntentFilter().apply {
                addAction(action)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(SCHEME)
            }

            // Fixed constant, like the installer path's MATCH_CATEGORY_TYPE: this is the match
            // PackageManagerService computes for an intent with a package: scheme and no type,
            // and it is what the preferred-activity check is matched against.
            val match = IntentFilter.MATCH_CATEGORY_SCHEME or IntentFilter.MATCH_ADJUSTMENT_MASK

            android.util.Log.i("DefaultUninstallerLogic", "addPreferredActivity match=$match names=${names.size}")
            addPreferredActivity(iPackageManager, filter, match, names.toTypedArray(), component, userId)

            if (hasSystemLevelPermission) {
                runCatching {
                    android.util.Log.i("DefaultUninstallerLogic", "addPersistentPreferredActivity for uninstaller")
                    addPersistentPreferredActivity(iPackageManager, filter, component, userId)
                }.onFailure {
                    android.util.Log.w("DefaultUninstallerLogic", "addPersistentPreferredActivity failed", it)
                    Timber.w(it, "addPersistentPreferredActivity failed for uninstaller")
                }
            }
        }
    }

    private fun clearPackagePreferredActivities(
        iPackageManager: IPackageManager,
        packageName: String,
        userId: Int,
        hasSystemLevelPermission: Boolean,
    ) {
        val m = iPackageManager.javaClass.getMethod(
            "clearPackagePreferredActivities", String::class.java,
        )
        m.invoke(iPackageManager, packageName)

        if (hasSystemLevelPermission) {
            runCatching {
                val mPersistent = iPackageManager.javaClass.getMethod(
                    "clearPackagePersistentPreferredActivities",
                    String::class.java, Int::class.javaPrimitiveType,
                )
                mPersistent.invoke(iPackageManager, packageName, userId)
            }.onFailure { Timber.w(it, "clearPackagePersistentPreferredActivities($packageName) failed") }
        }
    }

    private fun queryIntentActivities(
        iPackageManager: IPackageManager,
        intent: Intent,
        userId: Int,
    ): List<ResolveInfo> {
        val flagsLong = PackageManager.MATCH_DEFAULT_ONLY.toLong()
        val flagsInt = PackageManager.MATCH_DEFAULT_ONLY
        val result = runCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, null, flagsLong, userId)
        }.recoverCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, null, flagsInt, userId)
        }.getOrThrow()

        return extractResolveInfoList(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractResolveInfoList(result: Any?): List<ResolveInfo> {
        if (result == null) return emptyList()
        if (result is List<*>) return result as List<ResolveInfo>
        val getList = result.javaClass.getMethod("getList")
        return getList.invoke(result) as List<ResolveInfo>
    }

    private fun addPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        match: Int,
        names: Array<ComponentName>,
        component: ComponentName,
        userId: Int,
    ) {
        MimePreferredActivity.register(
            iPackageManager, Build.VERSION.SDK_INT, filter, match, names, component, userId,
        )
    }

    private fun addPersistentPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        activity: ComponentName,
        userId: Int,
    ) {
        val m = iPackageManager.javaClass.getMethod(
            "addPersistentPreferredActivity",
            IntentFilter::class.java, ComponentName::class.java,
            Int::class.javaPrimitiveType,
        )
        m.invoke(iPackageManager, filter, activity, userId)
    }

    private fun setComponentEnabled(
        iPackageManager: IPackageManager,
        component: ComponentName,
        enabled: Boolean,
        userId: Int,
    ) {
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val m = iPackageManager.javaClass.getMethod(
            "setComponentEnabledSetting",
            ComponentName::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        m.invoke(iPackageManager, component, newState, PackageManager.DONT_KILL_APP, userId)
    }
}
