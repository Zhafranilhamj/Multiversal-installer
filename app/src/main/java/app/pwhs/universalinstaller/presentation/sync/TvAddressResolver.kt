package app.pwhs.universalinstaller.presentation.sync

/**
 * Parses the several shapes a TV address arrives in into one [TvAddress].
 *
 * "Send to TV" accepts a scanned QR (`http://192.168.1.5:8787/?token=…`), the 4-6 letter IP
 * mnemonic printed under the TV's QR code, a bare `ip:port`, and mDNS `.local` names. Getting
 * this wrong either fails to connect or — since the receiver now authenticates — connects and then
 * gets refused for a missing pairing token, so the token has to be pulled out of every shape.
 *
 * Pure JVM (the local IP and the NSD token lookup are injected) so the parsing can be unit-tested.
 */
object TvAddressResolver {

    /** The receiver's default port and the alternates it falls back to when that one is taken. */
    val CANDIDATE_PORTS = listOf(8787, 8788, 8789, 8790, 8791, 8792)

    /**
     * @param host address to connect to
     * @param port explicitly requested port, or the default when none was given
     * @param portWasExplicit whether [port] came from the input rather than the default
     * @param pairingToken the receiver's token, empty when it could not be determined
     */
    data class TvAddress(
        val host: String,
        val port: Int,
        val portWasExplicit: Boolean,
        val pairingToken: String,
    ) {
        /** Ports to probe, most likely first. */
        fun candidatePorts(): List<Int> =
            if (portWasExplicit) listOf(port) + CANDIDATE_PORTS.filter { it != port } else CANDIDATE_PORTS
    }

    /**
     * @param rawInput whatever the scanner or the manual dialog produced
     * @param localIp this device's LAN address, used to resolve a relative IP mnemonic
     * @param discoveredTokenFor returns the NSD-advertised token for a host, or empty
     * @return the parsed address, or null when the input is not a usable host
     */
    fun resolve(
        rawInput: String,
        localIp: String?,
        discoveredTokenFor: (String) -> String,
    ): TvAddress? {
        val normalized = rawInput.trim()
        if (normalized.isEmpty()) return null

        val scannedToken = queryToken(normalized)
        val withoutScheme = normalized
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val hostPart = withoutScheme.substringBefore("/").substringBefore("?")
        if (hostPart.isBlank()) return null

        val resolvedIp = if (hostPart.length in 4..6 && hostPart.all { it.isLetter() }) {
            app.pwhs.core.receiver.IpEncoder.decode(hostPart, localIp) ?: hostPart
        } else {
            hostPart
        }

        val portWasExplicit = resolvedIp.contains(":")
        val host = if (portWasExplicit) resolvedIp.substringBefore(":") else resolvedIp
        val port = if (portWasExplicit) {
            resolvedIp.substringAfter(":").toIntOrNull() ?: return null
        } else {
            CANDIDATE_PORTS.first()
        }

        if (!isUsableHost(host) || port !in 1..65535) return null

        // A scanned QR carries its own token. A typed address does not, but NSD advertises one for
        // every receiver on this network — reuse it so manual pairing still works.
        val token = scannedToken.ifBlank { discoveredTokenFor(host) }

        return TvAddress(
            host = host,
            port = port,
            portWasExplicit = portWasExplicit,
            pairingToken = token,
        )
    }

    /** Reads `token=` out of a query string without needing a scheme for `Uri` to parse. */
    private fun queryToken(input: String): String {
        val query = input.substringAfter('?', "")
        if (query.isEmpty()) return ""
        val raw = query.split('&')
            .firstOrNull { it.startsWith("${app.pwhs.core.security.PairingToken.QUERY_PARAM}=") }
            ?.substringAfter('=')
            ?: return ""
        return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    /**
     * Accepts an IPv4 literal, `localhost` and mDNS `.local` names — the three things a TV on the
     * LAN can be addressed by. Written out rather than using `android.util.Patterns.IP_ADDRESS` so
     * this class stays testable on the JVM.
     */
    private fun isUsableHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host == "localhost" || host.endsWith(".local")) return true
        val octets = host.split('.')
        if (octets.size != 4) return false
        return octets.all { octet ->
            octet.isNotEmpty() &&
                octet.length <= 3 &&
                octet.all { it.isDigit() } &&
                // No leading zeros: "192.168.001.5" is not what a user meant to type.
                (octet == "0" || !octet.startsWith("0")) &&
                octet.toInt() in 0..255
        }
    }
}
