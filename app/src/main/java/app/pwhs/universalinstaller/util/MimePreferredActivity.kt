package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.IntentFilter
import java.lang.reflect.Method

/** APK filters contain a MIME type, which replacePreferredActivity explicitly rejects. */
internal object MimePreferredActivity {
    internal fun methodFor(proxyClass: Class<*>, sdkInt: Int): Method {
        val parameters = arrayOf(
            IntentFilter::class.java,
            Int::class.javaPrimitiveType!!,
            Array<ComponentName>::class.java,
            ComponentName::class.java,
            Int::class.javaPrimitiveType!!,
        )
        return if (sdkInt >= 31) {
            // Android 12+: removeExisting replaces only matching filters, preserving
            // this package's unrelated uninstaller defaults. No package-wide clear.
            proxyClass.getMethod("addPreferredActivity", *parameters, Boolean::class.javaPrimitiveType!!)
        } else {
            proxyClass.getMethod("addPreferredActivity", *parameters)
        }
    }

    fun register(
        proxy: Any,
        sdkInt: Int,
        filter: IntentFilter,
        match: Int,
        candidates: Array<ComponentName>,
        component: ComponentName,
        userId: Int,
    ) {
        val method = methodFor(proxy.javaClass, sdkInt)
        if (sdkInt >= 31) {
            method.invoke(proxy, filter, match, candidates, component, userId, true)
        } else {
            method.invoke(proxy, filter, match, candidates, component, userId)
        }
    }
}
