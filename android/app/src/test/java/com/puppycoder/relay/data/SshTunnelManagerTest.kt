package com.puppycoder.relay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SshTunnelManagerTest {
    @Test
    fun parsesExplicitWebSocketTarget() {
        assertEquals(TunnelTarget("127.0.0.1", 4310), parseTunnelTarget("ws://127.0.0.1:4310"))
    }

    @Test
    fun suppliesDefaultSecurePort() {
        assertEquals(TunnelTarget("app.internal", 443), parseTunnelTarget("https://app.internal/readyz"))
    }

    @Test
    fun rewritesOnlyAuthorityForLocalForward() {
        assertEquals(
            "http://127.0.0.1:49152/api?mode=health",
            rewriteEndpoint("http://app.internal:4096/api?mode=health", 49152),
        )
    }

    @Test
    fun automaticRoutingPrefersExactHostThenPort() {
        val server = server("ws://codex.prod.internal:4310")
        val wildcard = profile("wildcard", "*.prod.internal", null, priority = 500)
        val exact = profile("exact", "codex.prod.internal", null, priority = 1)
        val portSpecific = profile("port", "*.prod.internal", 4310, priority = 1)

        assertEquals(
            listOf("exact", "port", "wildcard"),
            selectTunnelProfiles(server, listOf(wildcard, exact, portSpecific)).map(SshTunnelProfile::id),
        )
    }

    @Test
    fun priorityBreaksEquallySpecificMatches() {
        val server = server("http://127.0.0.1:4096")
        val low = profile("low", "127.0.0.1", 4096, priority = 10)
        val high = profile("high", "127.0.0.1", 4096, priority = 200)

        assertEquals(listOf("high", "low"), selectTunnelProfiles(server, listOf(low, high)).map { it.id })
    }

    @Test
    fun automaticRoutingFallsBackToDirectWhenAllowed() {
        val resolved = SshTunnelManager().route(server("http://unmatched.example:4096"), emptyList())

        assertEquals("http://unmatched.example:4096", resolved.url)
        assertEquals("Direct fallback", resolved.routeLabel)
    }

    @Test
    fun automaticRoutingFailsClosedWithoutFallback() {
        val server = server("http://unmatched.example:4096").copy(allowDirectFallback = false)

        assertThrows(IllegalStateException::class.java) { SshTunnelManager().route(server, emptyList()) }
    }

    @Test
    fun tunnelTestUsesMatchingConfiguredServerAsTarget() {
        val profile = profile("gateway", "*.internal", null, priority = 100)

        assertEquals(
            TunnelTarget("codex.internal", 4310),
            findTunnelTestTarget(profile, listOf("ws://public.example:4310", "ws://codex.internal:4310")),
        )
    }

    @Test
    fun tunnelTestFallsBackToExactRuleWithPort() {
        val profile = profile("gateway", "127.0.0.1", 4096, priority = 100)

        assertEquals(TunnelTarget("127.0.0.1", 4096), findTunnelTestTarget(profile, emptyList()))
    }

    @Test
    fun hostKeyRepositoryAcceptsJschNullKeyTypeLookup() {
        assertEquals(0, FingerprintHostKeyRepository("").getHostKey("gateway.example", null).size)
    }

    @Test
    fun discoveryChecksStandardAndCustomAppServerTargets() {
        val tunnel = profile("gateway", "agent.internal", 5444, priority = 100)

        assertEquals(
            setOf(
                ServerDiscoveryCandidate(ServerKind.CODEX, "ws://127.0.0.1:4310"),
                ServerDiscoveryCandidate(ServerKind.OPENCODE, "http://127.0.0.1:4096"),
                ServerDiscoveryCandidate(ServerKind.CODEX, "ws://agent.internal:4310"),
                ServerDiscoveryCandidate(ServerKind.OPENCODE, "http://agent.internal:4096"),
                ServerDiscoveryCandidate(ServerKind.CODEX, "ws://agent.internal:5444"),
                ServerDiscoveryCandidate(ServerKind.OPENCODE, "http://agent.internal:5444"),
            ),
            serverDiscoveryCandidates(tunnel).toSet(),
        )
    }

    private fun server(endpoint: String) = RelayServer(
        name = "test",
        kind = ServerKind.OPENCODE,
        endpoint = endpoint,
        workspace = "/workspace",
    )

    private fun profile(
        id: String,
        pattern: String,
        port: Int?,
        priority: Int,
    ) = SshTunnelProfile(
        id = id,
        name = id,
        ssh = SshTunnelConfig(host = "gateway.example", username = "puppy", password = "secret"),
        routes = listOf(TunnelRouteRule(pattern, port)),
        priority = priority,
    )
}
