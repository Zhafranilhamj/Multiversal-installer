package app.pwhs.universalinstaller.domain.scanner

import app.pwhs.core.domain.model.VtStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectVirusTotalTransportTest {
    @Test fun missingKeyNeverMakesNetworkRequest() = runBlocking {
        val client = HttpClient(MockEngine { error("Must not send a request") })
        try { assertEquals(VtStatus.NO_API_KEY, DirectVirusTotal(client).lookup("a".repeat(64), "").status) }
        finally { client.close() }
    }
    @Test fun lookupUsesOnlyDirectEndpointAndPersonalKey() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("www.virustotal.com", request.url.host)
            assertEquals("https", request.url.protocol.name)
            assertEquals("/api/v3/files/" + "a".repeat(64), request.url.encodedPath)
            assertEquals("test-key", request.headers["x-apikey"])
            respond("""{"data":{"attributes":{"last_analysis_stats":{"malicious":1}}}}""", HttpStatusCode.OK)
        })
        try {
            val result = DirectVirusTotal(client).lookup("a".repeat(64), "test-key")
            assertEquals(VtStatus.MALICIOUS, result.status)
            assertEquals("VirusTotal", result.provider)
            assertEquals(1, requests)
        } finally { client.close() }
    }
    @Test fun rateLimitDoesNotFallbackOrRetry() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("", HttpStatusCode.TooManyRequests) })
        try {
            assertEquals(VtStatus.RATE_LIMITED, DirectVirusTotal(client).lookup("a".repeat(64), "test-key").status)
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
