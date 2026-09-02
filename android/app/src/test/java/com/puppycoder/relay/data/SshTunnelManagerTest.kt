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

    @Test
    fun tunnelRequiresAtLeastOneHop() {
        assertThrows(IllegalArgumentException::class.java) {
            SshTunnelProfile(
                id = "empty",
                name = "empty",
                hops = emptyList(),
                routes = listOf(TunnelRouteRule("*", null)),
            )
        }
    }

    @Test
    fun sshAccessorPointsAtTheFinalHop() {
        val tunnel = SshTunnelProfile(
            id = "chain",
            name = "chain",
            hops = listOf(
                SshTunnelConfig(host = "gateway.example", username = "entry", password = "secret"),
                SshTunnelConfig(host = "bastion.internal", username = "puppy", password = "secret"),
            ),
            routes = listOf(TunnelRouteRule("*", null)),
        )

        assertEquals("bastion.internal", tunnel.ssh.host)
    }

    @Test
    fun hopLabelFormatsUserHostAndPort() {
        val hop = SshTunnelConfig(host = "bastion.internal", port = 2222, username = "puppy", password = "secret")

        assertEquals("puppy@bastion.internal:2222", hop.label())
    }

    @Test
    fun identityBackedHopsResolveCredentialsFromTheIdentity() {
        val keyIdentity = SshIdentity(
            id = "key-1",
            name = "Work laptop key",
            kind = SshIdentityKind.PRIVATE_KEY,
            username = "puppy",
            privateKey = "KEY-DATA",
            privateKeyPassphrase = "key-pass",
        )
        val passwordIdentity = SshIdentity(
            id = "pass-1",
            name = "Gateway password",
            kind = SshIdentityKind.PASSWORD,
            username = "entry",
            password = "gate-pass",
        )
        val tunnel = SshTunnelProfile(
            id = "chain",
            name = "chain",
            hops = listOf(
                SshTunnelConfig(
                    host = "gateway.example",
                    identityId = "pass-1",
                    hostKeyFingerprint = "SHA256:aaa",
                ),
                SshTunnelConfig(
                    host = "bastion.internal",
                    identityId = "key-1",
                    hostKeyFingerprint = "SHA256:bbb",
                ),
            ),
            routes = listOf(TunnelRouteRule("*", null)),
        )

        val resolved = tunnel.resolved(listOf(passwordIdentity, keyIdentity))

        assertEquals("entry", resolved.hops[0].username)
        assertEquals("gate-pass", resolved.hops[0].password)
        assertEquals("", resolved.hops[0].privateKey)
        assertEquals("Gateway password", resolved.hops[0].identityName)
        assertEquals("puppy", resolved.hops[1].username)
        assertEquals("KEY-DATA", resolved.hops[1].privateKey)
        assertEquals("", resolved.hops[1].password)
        assertEquals("key-pass", resolved.hops[1].privateKeyPassphrase)
        assertEquals("SHA256:aaa", resolved.hops[0].hostKeyFingerprint)
        assertEquals("pass-1", resolved.hops[0].identityId)
    }

    @Test
    fun missingIdentityLeavesHopUnresolved() {
        val tunnel = SshTunnelProfile(
            id = "chain",
            name = "chain",
            hops = listOf(SshTunnelConfig(host = "gateway.example", identityId = "gone")),
            routes = listOf(TunnelRouteRule("*", null)),
        )

        val resolved = tunnel.resolved(emptyList())

        assertEquals("", resolved.hops.single().username)
        assertEquals("gone", resolved.hops.single().identityId)
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
        hops = listOf(SshTunnelConfig(host = "gateway.example", username = "puppy", password = "secret")),
        routes = listOf(TunnelRouteRule(pattern, port)),
        priority = priority,
    )
}
