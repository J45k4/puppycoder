package com.puppycoder.relay.data

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

internal data class ResolvedEndpoint(
    val url: String,
    val routeLabel: String,
)

internal class SshTunnelManager {
    private val gateways = mutableMapOf<String, GatewayHandle>()

    @Synchronized
    fun route(server: RelayServer, profiles: List<SshTunnelProfile>): ResolvedEndpoint {
        if (server.routeMode == ServerRouteMode.DIRECT) {
            return ResolvedEndpoint(server.endpoint, "Direct")
        }

        val target = parseTunnelTarget(server.endpoint)
        val candidates = selectTunnelProfiles(server, profiles, target)
        val failures = mutableListOf<String>()
        candidates.forEach { profile ->
            runCatching { routeThrough(profile, target, server.endpoint) }
                .onSuccess { return it }
                .onFailure { failures += "${profile.name}: ${it.message ?: "connection failed"}" }
        }

        if (server.allowDirectFallback && server.routeMode == ServerRouteMode.AUTOMATIC) {
            return ResolvedEndpoint(server.endpoint, "Direct fallback")
        }

        val message = when {
            failures.isNotEmpty() -> "No SSH route succeeded. ${failures.joinToString("; ")}"
            server.routeMode == ServerRouteMode.TUNNEL -> "The selected SSH tunnel is unavailable"
            else -> "No SSH tunnel route matches ${target.host}:${target.port}"
        }
        error(message)
    }

    @Synchronized
    fun closeProfile(profileId: String) {
        gateways.remove(profileId)?.session?.disconnect()
    }

    @Synchronized
    fun test(profile: SshTunnelProfile, endpoints: List<String>): SshTunnelTest {
        val started = System.nanoTime()
        val gateway = gatewayFor(profile)
        val target = findTunnelTestTarget(profile, endpoints)

        if (target == null) {
            return SshTunnelTest(
                message = "SSH gateway connected; add a matching server or exact host and port to test forwarding",
                latencyMs = (System.nanoTime() - started) / 1_000_000,
            )
        }

        val channel = gateway.session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        try {
            channel.setHost(target.host)
            channel.setPort(target.port)
            channel.setOrgIPAddress(LOOPBACK)
            channel.setOrgPort(0)
            channel.connect(CONNECT_TIMEOUT_MS)
            return SshTunnelTest(
                message = "SSH gateway and forwarded target are reachable",
                latencyMs = (System.nanoTime() - started) / 1_000_000,
                target = "${target.host}:${target.port}",
            )
        } catch (error: Exception) {
            throw IllegalStateException(
                "SSH connected, but ${target.host}:${target.port} is unreachable from the gateway: " +
                    (error.message ?: "connection failed"),
                error,
            )
        } finally {
            channel.disconnect()
        }
    }

    @Synchronized
    fun close() {
        gateways.values.forEach { it.session.disconnect() }
        gateways.clear()
    }

    private fun routeThrough(
        profile: SshTunnelProfile,
        target: TunnelTarget,
        endpoint: String,
    ): ResolvedEndpoint {
        val gateway = gatewayFor(profile)

        val localPort = gateway.forwards.getOrPut(target) {
            gateway.session.setPortForwardingL(LOOPBACK, 0, target.host, target.port)
        }
        return ResolvedEndpoint(rewriteEndpoint(endpoint, localPort), "SSH · ${profile.name}")
    }

    private fun gatewayFor(profile: SshTunnelProfile): GatewayHandle {
        val key = GatewayKey(profile.ssh)
        val current = gateways[profile.id]
        if (current != null && current.key == key && current.session.isConnected) return current

        current?.session?.disconnect()
        return openGateway(profile, key).also { gateways[profile.id] = it }
    }

    private fun openGateway(profile: SshTunnelProfile, key: GatewayKey): GatewayHandle {
        val config = profile.ssh
        require(config.host.isNotBlank()) { "SSH host is required" }
        require(config.username.isNotBlank()) { "SSH username is required" }
        require(config.port in 1..65535) { "SSH port must be between 1 and 65535" }
        require(config.password.isNotBlank() || config.privateKey.isNotBlank()) {
            "An SSH password or private key is required"
        }

        val jsch = JSch().apply {
            hostKeyRepository = FingerprintHostKeyRepository(config.hostKeyFingerprint)
            if (config.privateKey.isNotBlank()) {
                addIdentity(
                    "puppycoder-${profile.id}",
                    config.privateKey.toByteArray(),
                    null,
                    config.privateKeyPassphrase.takeIf(String::isNotBlank)?.toByteArray(),
                )
            }
        }
        val session = jsch.getSession(config.username, config.host, config.port)
        try {
            config.password.takeIf(String::isNotBlank)?.let(session::setPassword)
            session.setConfig("StrictHostKeyChecking", "yes")
            session.serverAliveInterval = 15_000
            session.serverAliveCountMax = 3
            session.connect(CONNECT_TIMEOUT_MS)
            return GatewayHandle(key, session)
        } catch (error: Exception) {
            session.disconnect()
            val pinHint = if (config.hostKeyFingerprint.isNotBlank()) {
                " Check that the SSH host-key SHA-256 fingerprint is correct."
            } else {
                ""
            }
            throw IllegalStateException("SSH connection failed: ${error.message ?: "connection failed"}.$pinHint", error)
        }
    }

    private data class GatewayKey(val config: SshTunnelConfig)

    private data class GatewayHandle(
        val key: GatewayKey,
        val session: Session,
        val forwards: MutableMap<TunnelTarget, Int> = mutableMapOf(),
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val LOOPBACK = "127.0.0.1"
    }
}

internal fun selectTunnelProfiles(
    server: RelayServer,
    profiles: List<SshTunnelProfile>,
    target: TunnelTarget = parseTunnelTarget(server.endpoint),
): List<SshTunnelProfile> {
    if (server.routeMode == ServerRouteMode.DIRECT) return emptyList()
    if (server.routeMode == ServerRouteMode.TUNNEL) {
        return profiles.filter { it.enabled && it.id == server.tunnelProfileId }
    }

    return profiles.asSequence()
        .filter(SshTunnelProfile::enabled)
        .mapNotNull { profile ->
            profile.matchScore(target)?.let { score -> profile to score }
        }
        .sortedWith(
            compareByDescending<Pair<SshTunnelProfile, Int>> { it.second }
                .thenByDescending { it.first.priority }
                .thenBy { it.first.name.lowercase() },
        )
        .map(Pair<SshTunnelProfile, Int>::first)
        .toList()
}

private fun SshTunnelProfile.matchScore(target: TunnelTarget): Int? =
    routes.mapNotNull { it.matchScore(target) }.maxOrNull()

internal fun findTunnelTestTarget(
    profile: SshTunnelProfile,
    endpoints: List<String>,
): TunnelTarget? = endpoints.asSequence()
    .mapNotNull { endpoint -> runCatching { parseTunnelTarget(endpoint) }.getOrNull() }
    .filter { candidate -> profile.matchScore(candidate) != null }
    .maxByOrNull { candidate -> profile.matchScore(candidate) ?: Int.MIN_VALUE }
    ?: profile.routes.firstNotNullOfOrNull { rule ->
        rule.port?.let { port ->
            rule.hostPattern.takeUnless { '*' in it }?.let { host -> TunnelTarget(host, port) }
        }
    }

private fun TunnelRouteRule.matchScore(target: TunnelTarget): Int? {
    if (port != null && port != target.port) return null
    val pattern = hostPattern.trim().lowercase()
    val host = target.host.lowercase()
    val hostScore = when {
        pattern == "*" -> 10_000
        pattern.startsWith("*.") && host.endsWith(pattern.removePrefix("*")) -> 20_000 + pattern.length
        pattern == host -> 30_000 + pattern.length
        else -> return null
    }
    return hostScore + if (port != null) 5_000 else 0
}

internal data class TunnelTarget(val host: String, val port: Int)

internal data class ServerDiscoveryCandidate(
    val kind: ServerKind,
    val endpoint: String,
)

internal fun serverDiscoveryCandidates(profile: SshTunnelProfile): List<ServerDiscoveryCandidate> {
    val exactRoutes = profile.routes.filter { '*' !in it.hostPattern }
    val hosts = (listOf("127.0.0.1") + exactRoutes.map { it.hostPattern.trim() })
        .filter(String::isNotBlank)
        .distinct()
    return buildList {
        hosts.forEach { host ->
            val urlHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
            add(ServerDiscoveryCandidate(ServerKind.CODEX, "ws://$urlHost:4310"))
            add(ServerDiscoveryCandidate(ServerKind.OPENCODE, "http://$urlHost:4096"))
        }
        exactRoutes.filter { it.port != null && it.port !in setOf(4310, 4096) }.forEach { route ->
            val host = if (':' in route.hostPattern && !route.hostPattern.startsWith("[")) {
                "[${route.hostPattern}]"
            } else {
                route.hostPattern
            }
            add(ServerDiscoveryCandidate(ServerKind.CODEX, "ws://$host:${route.port}"))
            add(ServerDiscoveryCandidate(ServerKind.OPENCODE, "http://$host:${route.port}"))
        }
    }.distinct().take(12)
}

internal fun parseTunnelTarget(endpoint: String): TunnelTarget {
    val uri = runCatching { URI(endpoint) }.getOrElse { error("Invalid app-server endpoint") }
    val host = uri.host?.takeIf(String::isNotBlank) ?: error("App-server endpoint must include a host")
    val port = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("wss", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("ws", ignoreCase = true) -> 80
        else -> error("App-server endpoint must use http, https, ws, or wss")
    }
    return TunnelTarget(host, port)
}

internal fun rewriteEndpoint(endpoint: String, localPort: Int): String {
    val uri = URI(endpoint)
    return URI(
        uri.scheme,
        uri.userInfo,
        "127.0.0.1",
        localPort,
        uri.path,
        uri.query,
        uri.fragment,
    ).toASCIIString()
}

internal class FingerprintHostKeyRepository(expectedFingerprint: String) : HostKeyRepository {
    private val expected = expectedFingerprint.normalizedFingerprint()

    override fun check(host: String, key: ByteArray): Int {
        if (expected.isBlank()) return HostKeyRepository.OK
        val actual = MessageDigest.getInstance("SHA-256").digest(key)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(actual)
        return if (encoded.normalizedFingerprint() == expected) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "PuppyCoder pinned host key"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

private fun String.normalizedFingerprint(): String = trim()
    .removePrefix("SHA256:")
    .removePrefix("sha256:")
    .trimEnd('=')
