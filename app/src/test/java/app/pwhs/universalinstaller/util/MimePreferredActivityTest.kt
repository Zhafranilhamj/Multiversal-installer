package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/** Signature contract tests: no Android runtime or Shizuku connection required. */
class MimePreferredActivityTest {
    @Suppress("UNUSED_PARAMETER")
    class PackageManagerProxy {
        fun addPreferredActivity(filter: IntentFilter, match: Int, names: Array<ComponentName>,
            component: ComponentName, userId: Int, removeExisting: Boolean) = Unit
        fun addPreferredActivity(filter: IntentFilter, match: Int, names: Array<ComponentName>,
            component: ComponentName, userId: Int) = Unit
        fun replacePreferredActivity(filter: IntentFilter, match: Int, names: Array<ComponentName>,
            component: ComponentName, userId: Int) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    class ReplacementOnlyProxy {
        fun replacePreferredActivity(filter: IntentFilter, match: Int, names: Array<ComponentName>,
            component: ComponentName, userId: Int) = Unit
    }

    @Test fun android12AndLaterUseMimeCompatibleSixArgumentApi() {
        for (sdk in listOf(31, 32, 33, 34, 35, 36)) {
            val method = MimePreferredActivity.methodFor(PackageManagerProxy::class.java, sdk)
            assertEquals("addPreferredActivity", method.name)
            assertEquals(6, method.parameterCount)
            assertEquals(Boolean::class.javaPrimitiveType, method.parameterTypes.last())
        }
    }

    @Test fun olderAndroidUsesFiveArgumentAddApiNotReplacement() {
        val method = MimePreferredActivity.methodFor(PackageManagerProxy::class.java, 30)
        assertEquals("addPreferredActivity", method.name)
        assertEquals(5, method.parameterCount)
    }

    @Test(expected = NoSuchMethodException::class)
    fun neverFallsBackToMimeIncompatibleReplacement() {
        MimePreferredActivity.methodFor(ReplacementOnlyProxy::class.java, 36)
    }
}
