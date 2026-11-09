package app.pwhs.universalinstaller.domain.scanner

/** No default network destination: choosing a provider is explicit consent to hash lookup. */
enum class ScanProvider(val label: String) {
    VIRUSTOTAL("VirusTotal"), VIRUSDETEC("VirusDetec");
    companion object {
        fun from(value: String?): ScanProvider? = entries.firstOrNull { it.name == value }
        fun language(tag: String): String = if (tag.substringBefore('-').equals("vi", true)) "vi" else "en"
    }
}
