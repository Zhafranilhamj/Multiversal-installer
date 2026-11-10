package app.pwhs.core.domain.model

/**
 * Upload ceilings shared by every scanning provider, so the UI, the VirusDetec client and the
 * direct VirusTotal client can never disagree about what "too large" means.
 */
object ScanLimits {
    /**
     * VirusTotal's `POST /files` endpoint only accepts files up to 32 MB. Anything bigger has to
     * go through `GET /files/upload_url` first and be posted to the returned one-time URL.
     */
    const val DIRECT_UPLOAD_MAX_BYTES: Long = 32L * 1024 * 1024

    /**
     * The absolute ceiling: 650 MB for VirusTotal's pre-signed upload URL, and the ceiling the
     * VirusDetec upload endpoint is documented to accept.
     */
    const val MAX_UPLOAD_BYTES: Long = 650L * 1024 * 1024

    /** True when [sizeBytes] needs the two-step `upload_url` flow instead of a direct POST. */
    fun needsUploadUrl(sizeBytes: Long): Boolean = sizeBytes > DIRECT_UPLOAD_MAX_BYTES

    /** True when [sizeBytes] exceeds every provider's limit. */
    fun exceedsMaximum(sizeBytes: Long): Boolean = sizeBytes > MAX_UPLOAD_BYTES

    /** Human readable megabytes, used in the "file too large" message. */
    fun describeMegabytes(sizeBytes: Long): String = "${sizeBytes / (1024 * 1024)} MB"
}
