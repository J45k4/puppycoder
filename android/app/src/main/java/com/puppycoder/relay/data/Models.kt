package com.puppycoder.relay.data

import java.util.UUID

enum class ServerKind(val label: String, val initials: String) {
    CODEX("Codex", "CX"),
    OPENCODE("OpenCode", "OC"),
}

enum class ConnectionState { ONLINE, CHECKING, OFFLINE }

enum class ServerRouteMode(val label: String) {
    AUTOMATIC("Automatic"),
    DIRECT("Direct"),
    TUNNEL("Specific tunnel"),
}

data class RelayServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: ServerKind,
    val endpoint: String,
    val workspace: String,
    val username: String = "",
    val password: String = "",
    val routeMode: ServerRouteMode = ServerRouteMode.AUTOMATIC,
    val tunnelProfileId: String? = null,
    val allowDirectFallback: Boolean = true,
    val state: ConnectionState = ConnectionState.ONLINE,
    val version: String = "Demo",
    val latencyMs: Long = 0,
    val isDemo: Boolean = false,
)

data class SshTunnelConfig(
    val host: String,
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
    val hostKeyFingerprint: String = "",
    val identityId: String? = null,
    val identityName: String = "",
    val connectionId: String? = null,
    val connectionName: String = "",
)

enum class SshIdentityKind(val label: String, val initials: String) {
    PASSWORD("Password", "PWD"),
    PRIVATE_KEY("Private key", "KEY"),
}

data class SshIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: SshIdentityKind = SshIdentityKind.PASSWORD,
    val username: String,
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
)

data class SshConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ssh: SshTunnelConfig,
)

data class TunnelRouteRule(
    val hostPattern: String,
    val port: Int? = null,
)

data class SshTunnelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hops: List<SshTunnelConfig>,
    val routes: List<TunnelRouteRule>,
    val priority: Int = 100,
    val enabled: Boolean = true,
) {
    init {
        require(hops.isNotEmpty()) { "A tunnel needs at least one SSH hop" }
    }

    val ssh: SshTunnelConfig get() = hops.last()
}

data class RemoteCheck(
    val version: String,
    val latencyMs: Long,
    val routeLabel: String,
)

data class SshTunnelTest(
    val message: String,
    val latencyMs: Long,
    val target: String? = null,
    val hops: List<SshTunnelHopTest> = emptyList(),
)

data class SshTunnelHopTest(
    val hopIndex: Int,
    val label: String,
    val latencyMs: Long,
    val ok: Boolean,
    val error: String? = null,
)

data class DiscoveredAgentServer(
    val kind: ServerKind,
    val endpoint: String,
    val suggestedName: String,
    val suggestedWorkspace: String,
    val version: String,
    val latencyMs: Long,
    val requiresAuthentication: Boolean = false,
)

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Error(val message: String) : RemoteResult<Nothing>
}
