package com.puppycoder.relay.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    @Synchronized
    fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > IV_SIZE) { "Invalid encrypted value" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, packed.copyOfRange(0, IV_SIZE)),
        )
        return cipher.doFinal(packed.copyOfRange(IV_SIZE, packed.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "puppycoder_connection_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

internal fun ComputerEntity.toModel(secretStore: SecretStore) = RelayServer(
    id = id,
    name = name,
    kind = ServerKind.valueOf(kind),
    endpoint = endpoint,
    workspace = workspace,
    username = username,
    password = runCatching { secretStore.decrypt(encryptedPassword) }.getOrDefault(""),
    routeMode = ServerRouteMode.valueOf(routeMode),
    tunnelProfileId = tunnelProfileId,
    allowDirectFallback = allowDirectFallback,
    state = ConnectionState.valueOf(connectionState),
    version = version,
    latencyMs = latencyMs,
    isDemo = isDemo,
)

internal fun RelayServer.toEntity(secretStore: SecretStore) = ComputerEntity(
    id = id,
    name = name,
    kind = kind.name,
    endpoint = endpoint,
    workspace = workspace,
    username = username,
    encryptedPassword = secretStore.encrypt(password),
    routeMode = routeMode.name,
    tunnelProfileId = tunnelProfileId,
    allowDirectFallback = allowDirectFallback,
    connectionState = state.name,
    version = version,
    latencyMs = latencyMs,
    isDemo = isDemo,
)

internal fun TunnelWithRoutes.toModel(secretStore: SecretStore) = SshTunnelProfile(
    id = profile.id,
    name = profile.name,
    hops = hops
        .sortedBy(TunnelHopEntity::hopIndex)
        .map { hop ->
            SshTunnelConfig(
                host = hop.host,
                port = hop.port,
                username = hop.username,
                password = runCatching { secretStore.decrypt(hop.encryptedPassword) }.getOrDefault(""),
                privateKey = runCatching { secretStore.decrypt(hop.encryptedPrivateKey) }.getOrDefault(""),
                privateKeyPassphrase = runCatching {
                    secretStore.decrypt(hop.encryptedPrivateKeyPassphrase)
                }.getOrDefault(""),
                hostKeyFingerprint = hop.hostKeyFingerprint,
                identityId = hop.identityId,
                connectionId = hop.connectionId,
            )
        },
    routes = routes.sortedBy(TunnelRouteEntity::id).map { TunnelRouteRule(it.hostPattern, it.port) },
    priority = profile.priority,
    enabled = profile.enabled,
)

internal fun SshTunnelProfile.toEntities(secretStore: SecretStore): Triple<TunnelProfileEntity, List<TunnelHopEntity>, List<TunnelRouteEntity>> {
    val profile = TunnelProfileEntity(
        id = id,
        name = name,
        priority = priority,
        enabled = enabled,
    )
    val hopEntities = hops.mapIndexed { index, hop ->
        TunnelHopEntity(
            id = "$id-hop$index",
            profileId = id,
            hopIndex = index,
            host = hop.host,
            port = hop.port,
            username = hop.username,
            encryptedPassword = secretStore.encrypt(hop.password),
            encryptedPrivateKey = secretStore.encrypt(hop.privateKey),
            encryptedPrivateKeyPassphrase = secretStore.encrypt(hop.privateKeyPassphrase),
            hostKeyFingerprint = hop.hostKeyFingerprint,
            identityId = hop.identityId,
            connectionId = hop.connectionId,
        )
    }
    val routeEntities = routes.mapIndexed { index, route ->
        TunnelRouteEntity(
            id = "$id-$index",
            profileId = id,
            hostPattern = route.hostPattern,
            port = route.port,
        )
    }
    return Triple(profile, hopEntities, routeEntities)
}

internal fun TunnelIdentityEntity.toModel(secretStore: SecretStore) = SshIdentity(
    id = id,
    name = name,
    kind = SshIdentityKind.valueOf(kind),
    username = username,
    password = runCatching { secretStore.decrypt(encryptedPassword) }.getOrDefault(""),
    privateKey = runCatching { secretStore.decrypt(encryptedPrivateKey) }.getOrDefault(""),
    privateKeyPassphrase = runCatching { secretStore.decrypt(encryptedPrivateKeyPassphrase) }.getOrDefault(""),
)

internal fun SshIdentity.toEntity(secretStore: SecretStore) = TunnelIdentityEntity(
    id = id,
    name = name,
    kind = kind.name,
    username = username,
    encryptedPassword = secretStore.encrypt(password),
    encryptedPrivateKey = secretStore.encrypt(privateKey),
    encryptedPrivateKeyPassphrase = secretStore.encrypt(privateKeyPassphrase),
)

internal fun SshConnectionEntity.toModel(secretStore: SecretStore) = SshConnection(
    id = id,
    name = name,
    ssh = SshTunnelConfig(
        host = host,
        port = port,
        username = username,
        password = runCatching { secretStore.decrypt(encryptedPassword) }.getOrDefault(""),
        privateKey = runCatching { secretStore.decrypt(encryptedPrivateKey) }.getOrDefault(""),
        privateKeyPassphrase = runCatching { secretStore.decrypt(encryptedPrivateKeyPassphrase) }.getOrDefault(""),
        hostKeyFingerprint = hostKeyFingerprint,
        identityId = identityId,
        connectionId = id,
        connectionName = name,
    ),
)

internal fun SshConnection.toEntity(secretStore: SecretStore) = SshConnectionEntity(
    id = id,
    name = name,
    host = ssh.host,
    port = ssh.port,
    username = ssh.username,
    encryptedPassword = secretStore.encrypt(ssh.password),
    encryptedPrivateKey = secretStore.encrypt(ssh.privateKey),
    encryptedPrivateKeyPassphrase = secretStore.encrypt(ssh.privateKeyPassphrase),
    hostKeyFingerprint = ssh.hostKeyFingerprint,
    identityId = ssh.identityId,
)
