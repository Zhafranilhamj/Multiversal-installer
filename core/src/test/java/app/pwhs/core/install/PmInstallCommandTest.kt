package app.pwhs.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PmInstallCommandTest {

    // ── The "Install for all users" target ─────────────────────────────

    @Test
    fun `all users emits --user all on a modern build`() {
        assertEquals("--user all", PmInstallCommand.userArgument(allUsers = true, sdkInt = 34))
    }

    @Test
    fun `all users never emits --user 0`() {
        // Regression: --user 0 is the *owner* user, not "all users". On a TV with a secondary
        // profile the old flag installed for the wrong user; on a single-user box it was a no-op.
        for (sdk in 24..40) {
            val argument = PmInstallCommand.userArgument(allUsers = true, sdkInt = sdk)
            assertTrue("sdk $sdk produced '$argument'", argument == null || argument == "--user all")
        }
    }

    @Test
    fun `pre-O builds omit the flag because their pm parses --user with parseInt`() {
        assertNull(PmInstallCommand.userArgument(allUsers = true, sdkInt = 24))
        assertNull(PmInstallCommand.userArgument(allUsers = true, sdkInt = 25))
        assertEquals("--user all", PmInstallCommand.userArgument(allUsers = true, sdkInt = 26))
    }

    @Test
    fun `the current user needs no flag at all`() {
        assertNull(PmInstallCommand.userArgument(allUsers = false, sdkInt = 34))
    }

    // ── install-create ─────────────────────────────────────────────────

    @Test
    fun `install-create with no options is bare`() {
        assertEquals(
            "pm install-create",
            PmInstallCommand.installCreate(
                replace = false, downgrade = false, grantPermissions = false,
                allowTest = false, allUsers = false, sdkInt = 34,
            ),
        )
    }

    @Test
    fun `install-create with every option keeps the documented flag order`() {
        assertEquals(
            "pm install-create -r -d -g -t --user all",
            PmInstallCommand.installCreate(
                replace = true, downgrade = true, grantPermissions = true,
                allowTest = true, allUsers = true, sdkInt = 34,
            ),
        )
    }

    @Test
    fun `install-create replaces an existing package by default`() {
        assertEquals(
            "pm install-create -r",
            PmInstallCommand.installCreate(
                replace = true, downgrade = false, grantPermissions = false,
                allowTest = false, allUsers = false, sdkInt = 34,
            ),
        )
    }

    // ── install-write / commit ─────────────────────────────────────────

    @Test
    fun `install-write passes the size, session, split name and path`() {
        assertEquals(
            "pm install-write -S 1024 42 base /data/local/tmp/base.apk",
            PmInstallCommand.installWrite("42", "base", 1024L, "/data/local/tmp/base.apk"),
        )
    }

    @Test
    fun `the stdin fallback cats the file into install-write`() {
        assertEquals(
            "cat /data/local/tmp/base.apk | pm install-write -S 1024 42 base -",
            PmInstallCommand.installWriteViaStdin("42", "base", 1024L, "/data/local/tmp/base.apk"),
        )
    }

    @Test
    fun `install-commit targets the session`() {
        assertEquals("pm install-commit 42", PmInstallCommand.installCommit("42"))
    }

    @Test
    fun `spaces in a path are escaped for the shell`() {
        assertEquals(
            "/data/my\\ apps/base.apk",
            PmInstallCommand.escapePath("/data/my apps/base.apk"),
        )
        assertEquals(
            "pm install-write -S 1 42 base /data/my\\ apps/base.apk",
            PmInstallCommand.installWrite("42", "base", 1L, "/data/my apps/base.apk"),
        )
    }

    @Test
    fun `the symbolic-user threshold matches Android O`() {
        assertEquals(26, PmInstallCommand.MIN_SDK_FOR_SYMBOLIC_USER)
    }
}
