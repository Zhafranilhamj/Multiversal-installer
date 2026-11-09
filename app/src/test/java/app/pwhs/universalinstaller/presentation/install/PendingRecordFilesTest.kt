package app.pwhs.universalinstaller.presentation.install

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingRecordFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun survivesNewStoreInstanceAndCanBeConsumed() {
        val dir = temporary.newFolder()
        PendingRecordFiles(dir).write("pending-123", "payload")
        val restarted = PendingRecordFiles(dir)
        assertEquals("payload", restarted.read("pending-123"))
        restarted.remove("pending-123")
        assertNull(restarted.read("pending-123"))
    }

    @Test fun expiresOldRecords() {
        val dir = temporary.newFolder()
        val store = PendingRecordFiles(dir)
        store.write("old", "payload")
        assertTrue(File(dir, "old.json").setLastModified(System.currentTimeMillis() - 3_600_001))
        assertNull(store.read("old"))
    }

    @Test fun replacementLeavesNoTemporaryFile() {
        val dir = temporary.newFolder()
        val store = PendingRecordFiles(dir)
        store.write("same", "before")
        store.write("same", "after")
        assertEquals("after", store.read("same"))
        assertEquals(listOf("same.json"), dir.listFiles()!!.map { it.name })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversal() {
        PendingRecordFiles(temporary.newFolder()).read("../outside")
    }
}
