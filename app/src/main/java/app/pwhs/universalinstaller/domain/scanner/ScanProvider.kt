package app.pwhs.universalinstaller.domain.scanner

/** No default network destination: choosing a provider is explicit consent to hash lookup. */
enum class ScanProvider(val label: String) {
    VIRUSTOTAL("VirusTotal"), VIRUSDETEC("VirusDetec");

    companion object {
        fun from(value: String?): ScanProvider? = entries.firstOrNull { it.name == value }

        /**
         * The provider a scan should actually use.
         *
         * An explicit choice in Settings always wins. Only when nothing has been chosen yet do we
         * fall back to VirusDetec, because it works without any key of the user's own; VirusTotal
         * needs a personal API key and would just answer "add your key" if picked blindly.
         *
         * This is what keeps the Security Scan button from silently doing nothing on a fresh
         * install: [from] still returns null for "no choice yet", so a scan can tell the
         * difference between "the user turned scanning off" and "the user never picked one".
         */
        fun resolve(stored: String?): ScanProvider = from(stored) ?: VIRUSDETEC

        fun language(tag: String): String = if (tag.substringBefore('-').equals("vi", true)) "vi" else "en"
    }
}
