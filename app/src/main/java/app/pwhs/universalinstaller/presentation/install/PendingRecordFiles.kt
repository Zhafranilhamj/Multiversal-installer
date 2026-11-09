package app.pwhs.universalinstaller.presentation.install

import java.io.File

/** Private cache records: bounded lifetime, atomic replacement, no caller-controlled paths. */
internal class PendingRecordFiles(private val directory: File) {
    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}")))
        return File(directory, "$id.json")
    }

    @Synchronized fun write(id: String, text: String) {
        directory.mkdirs()
        val target = file(id)
        val temp = File(directory, "$id.tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(temp.renameTo(target)) { "Cannot persist pending installation" }
        } finally { temp.delete() }
    }

    @Synchronized fun read(id: String): String? {
        val target = file(id)
        if (!target.exists()) return null
        if (System.currentTimeMillis() - target.lastModified() > 60 * 60 * 1000L) {
            target.delete()
            return null
        }
        return target.readText()
    }

    @Synchronized fun remove(id: String) { file(id).delete() }
}
