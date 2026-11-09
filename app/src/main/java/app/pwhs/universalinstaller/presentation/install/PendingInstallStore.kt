package app.pwhs.universalinstaller.presentation.install

import android.net.Uri
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.domain.model.ApkInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Installs that have been parsed but not yet confirmed, held at process scope while a
 * notification waits for the user's answer.
 *
 * Why the whole payload and not just the source URI: the URI an external app hands us carries a
 * one-shot read grant tied to the activity that received it. Once that activity finishes, the
 * grant is gone and the file cannot be re-read — so re-parsing when the user finally taps
 * Install is not an option. Parsing already copies every APK into our own cache
 * (`extractApkInfoAndCacheUris`), and those copies need no grant, so what we keep here is the
 * finished parse: cached split URIs plus the metadata the install and its notification need.
 *
 * A process-scoped object rather than a DI singleton, matching [IntentHandoff]. Entries are stored atomically in private cache for up to one hour and removed on consumption.
 * Cache eviction can still invalidate a notification.
 */
object PendingInstallStore {

    /**
     * @param apkUris cached copies, ready to install — never the caller's URIs.
     * @param originalUri the source, kept only for delete-after-install and OBB extraction.
     *   Unreadable once the receiving activity is gone; treat as a label, not a handle.
     * @param obbEntries OBB members found inside an archive during parse.
     * @param attachedObbs OBBs the user attached by hand before switching away.
     * @param isDowngrade the parse already compared versions; carried so the notification path
     *   can refuse to auto-install without consent.
     */
    data class Entry(
        val id: String,
        val apkUris: List<Uri>,
        val originalUri: Uri?,
        val fileName: String,
        val packageName: String,
        val appName: String,
        val iconPath: String?,
        val obbEntries: List<ObbEntry> = emptyList(),
        val attachedObbs: List<AttachedObb> = emptyList(),
        val isDowngrade: Boolean = false,
        /**
         * The finished parse, held so the dialog can be opened later showing exactly what was
         * parsed — split selection included. Re-parsing would lose the split entries for archive
         * formats (only the extracted members survive in cache, not the archive), and installing
         * a base APK while believing it was a split package is the kind of wrong this avoids.
         */
        val apkInfo: ApkInfo? = null,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private var records: PendingRecordFiles? = null

    fun initialize(context: android.content.Context) {
        records = PendingRecordFiles(java.io.File(context.cacheDir, "pending_installs"))
    }

    /** @return the stored entry, whose [Entry.id] keys both the notification and its actions. */
    fun put(
        apkUris: List<Uri>,
        originalUri: Uri?,
        fileName: String,
        packageName: String,
        appName: String,
        iconPath: String?,
        obbEntries: List<ObbEntry> = emptyList(),
        attachedObbs: List<AttachedObb> = emptyList(),
        isDowngrade: Boolean = false,
        apkInfo: ApkInfo? = null,
    ): Entry {
        val entry = Entry(
            id = java.util.UUID.randomUUID().toString(),
            apkUris = apkUris,
            originalUri = originalUri,
            fileName = fileName,
            packageName = packageName,
            appName = appName,
            iconPath = iconPath,
            obbEntries = obbEntries,
            attachedObbs = attachedObbs,
            isDowngrade = isDowngrade,
            apkInfo = apkInfo,
        )
        return put(entry)
    }

    @Synchronized fun put(entry: Entry): Entry {
        records?.write(entry.id, PendingInstallCodec.encode(entry))
        entries[entry.id] = entry
        return entry
    }

    @Synchronized fun get(id: String): Entry? = entries[id] ?: runCatching {
        records?.read(id)?.let(PendingInstallCodec::decode)?.takeIf { it.id == id }
    }.getOrNull()

    /** Takes the entry out — the install is starting, or the user said no. */
    @Synchronized fun consume(id: String): Entry? {
        val entry = get(id)
        entries.remove(id)
        runCatching { records?.remove(id) }
        return entry
    }

    /**
     * Stable per-entry request code so each pending install owns its own PendingIntents and a
     * second prompt cannot overwrite the first one's actions.
     */
    fun requestCode(id: String, action: String): Int = "$id/$action".hashCode()
}
