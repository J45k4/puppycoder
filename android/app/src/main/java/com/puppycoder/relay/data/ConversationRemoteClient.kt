package com.puppycoder.relay.data

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal const val PUPPYCODER_CLIENT_VERSION = "0.3.8"

class ConversationRemoteClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) : AgentConversationClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sshTunnels = SshTunnelManager()
    private val codexConnections = CodexConnectionManager(http, scope)
    private val activeRuns = ConcurrentHashMap<String, ActiveRun>()
    @Volatile private var tunnelProfiles: List<SshTunnelProfile> = emptyList()

    override fun setTunnelProfiles(profiles: List<SshTunnelProfile>) {
        tunnelProfiles = profiles.toList()
    }

    override suspend fun send(
        computer: RelayServer,
        request: SendMessageRequest,
        onEvent: (AgentConversationEvent) -> Unit,
    ) {
        val endpoint = withContext(Dispatchers.IO) {
            runCatching { sshTunnels.route(computer, tunnelProfiles) }
        }.getOrElse {
            onEvent(AgentConversationEvent.Failed(it.conversationMessage(), deliveryWasAccepted = false))
            return
        }
        when (computer.kind) {
            ServerKind.CODEX -> sendCodex(computer, endpoint.url, request, onEvent)
            ServerKind.OPENCODE -> sendOpenCode(computer, endpoint.url, request, onEvent)
        }
    }

    override suspend fun stop(computer: RelayServer, conversationId: String): RemoteResult<Unit> {
        val active = activeRuns[conversationId]
            ?: return RemoteResult.Error("There is no active response to stop")
        return when (active) {
            is ActiveRun.Codex -> {
                val turnId = active.turnId
                    ?: return RemoteResult.Error("Codex has not accepted this message yet")
                runCatching {
                    active.connection.request(
                        "turn/interrupt",
                        JSONObject().put("threadId", active.threadId).put("turnId", turnId),
                    )
                    RemoteResult.Success(Unit)
                }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
            }
            is ActiveRun.OpenCode -> withContext(Dispatchers.IO) {
                val endpoint = runCatching { sshTunnels.route(computer, tunnelProfiles) }
                    .getOrElse { return@withContext RemoteResult.Error(it.conversationMessage()) }
                val request = computer.authorizedRequest(
                    endpoint.url.conversationHttpBase() + "/session/${active.sessionId}/abort",
                ).post("{}".toRequestBody(jsonType)).build()
                runCatching {
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
                    }
                    active.stop()
                    RemoteResult.Success(Unit)
                }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
            }
        }
    }

    override suspend fun check(computer: RelayServer): RemoteResult<RemoteCheck> = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val endpoint = runCatching { sshTunnels.route(computer, tunnelProfiles) }
            .getOrElse { return@withContext RemoteResult.Error(it.conversationMessage()) }
        val url = when (computer.kind) {
            ServerKind.OPENCODE -> endpoint.url.conversationHttpBase() + "/global/health"
            ServerKind.CODEX -> endpoint.url.conversationHttpBase() + "/readyz"
        }
        runCatching {
            http.newCall(computer.authorizedRequest(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
                val version = if (computer.kind == ServerKind.OPENCODE) {
                    JSONObject(response.body?.string().orEmpty()).optString("version", "Online")
                } else {
                    "Ready"
                }
                RemoteResult.Success(
                    RemoteCheck(version, (System.nanoTime() - started) / 1_000_000, endpoint.routeLabel),
                )
            }
        }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    override suspend fun listModels(computer: RelayServer): RemoteResult<List<AgentModel>> {
        val endpoint = withContext(Dispatchers.IO) {
            runCatching { sshTunnels.route(computer, tunnelProfiles) }
        }.getOrElse { return RemoteResult.Error(it.conversationMessage()) }
        return when (computer.kind) {
            ServerKind.CODEX -> listCodexModels(computer, endpoint.url)
            ServerKind.OPENCODE -> listOpenCodeModels(computer, endpoint.url)
        }
    }

    private suspend fun listCodexModels(
        computer: RelayServer,
        endpoint: String,
    ): RemoteResult<List<AgentModel>> {
        val models = linkedMapOf<String, AgentModel>()
        var cursor: String? = null
        do {
            val params = JSONObject().put("includeHidden", false).put("limit", 100)
            cursor?.let { params.put("cursor", it) }
            when (val page = codexRpcRequest(computer, endpoint, "model/list", params)) {
                is RemoteResult.Error -> return page
                is RemoteResult.Success -> {
                    val data = page.value.optJSONArray("data") ?: JSONArray()
                    for (index in 0 until data.length()) {
                        val item = data.getJSONObject(index)
                        val modelId = item.optString("model", item.optString("id"))
                        if (modelId.isBlank() || item.optBoolean("hidden")) continue
                        models[modelId] = AgentModel(
                            modelId = modelId,
                            displayName = item.optString("displayName", modelId),
                            description = item.optString("description"),
                            isDefault = item.optBoolean("isDefault"),
                        )
                    }
                    cursor = if (page.value.isNull("nextCursor")) {
                        null
                    } else {
                        page.value.optString("nextCursor").takeIf(String::isNotBlank)
                    }
                }
            }
        } while (cursor != null)
        return RemoteResult.Success(
            models.values.sortedWith(
                compareByDescending<AgentModel>(AgentModel::isDefault)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, AgentModel::displayName),
            ),
        )
    }

    private suspend fun listOpenCodeModels(
        computer: RelayServer,
        endpoint: String,
    ): RemoteResult<List<AgentModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = endpoint.conversationHttpBase() +
                "/provider?directory=${computer.workspace.conversationUrlEncode()}"
            val body = http.newCall(computer.authorizedRequest(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("Could not load OpenCode models: HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
            val connected = body.optJSONArray("connected")?.let { array ->
                (0 until array.length()).map(array::getString).toSet()
            }.orEmpty()
            val defaults = body.optJSONObject("default") ?: JSONObject()
            val result = mutableListOf<AgentModel>()
            val providers = body.optJSONArray("all") ?: JSONArray()
            for (providerIndex in 0 until providers.length()) {
                val provider = providers.getJSONObject(providerIndex)
                val providerId = provider.optString("id")
                if (providerId.isBlank() || providerId !in connected) continue
                val providerName = provider.optString("name", providerId)
                val models = provider.optJSONObject("models") ?: continue
                val keys = models.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val model = models.optJSONObject(key) ?: continue
                    if (model.optString("status") == "deprecated") continue
                    val modelId = model.optString("id", key)
                    result += AgentModel(
                        modelId = modelId,
                        displayName = model.optString("name", modelId),
                        providerId = providerId,
                        providerName = providerName,
                        isDefault = defaults.optString(providerId) == modelId,
                    )
                }
            }
            RemoteResult.Success(
                result.distinctBy(AgentModel::key).sortedWith(
                    compareByDescending<AgentModel>(AgentModel::isDefault)
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.providerName.orEmpty() }
                        .thenBy(String.CASE_INSENSITIVE_ORDER, AgentModel::displayName),
                ),
            )
        }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    override suspend fun listConversations(
        computer: RelayServer,
    ): RemoteResult<List<RemoteConversationSummary>> {
        val endpoint = withContext(Dispatchers.IO) {
            runCatching { sshTunnels.route(computer, tunnelProfiles) }
        }.getOrElse { return RemoteResult.Error(it.conversationMessage()) }
        return when (computer.kind) {
            ServerKind.CODEX -> listCodexConversations(computer, endpoint.url)
            ServerKind.OPENCODE -> listOpenCodeConversations(computer, endpoint.url)
        }
    }

    override suspend fun loadConversation(
        computer: RelayServer,
        remoteConversationId: String,
    ): RemoteResult<List<RemoteChatMessage>> {
        val endpoint = withContext(Dispatchers.IO) {
            runCatching { sshTunnels.route(computer, tunnelProfiles) }
        }.getOrElse { return RemoteResult.Error(it.conversationMessage()) }
        return when (computer.kind) {
            ServerKind.CODEX -> loadCodexConversation(computer, endpoint.url, remoteConversationId)
            ServerKind.OPENCODE -> loadOpenCodeConversation(computer, endpoint.url, remoteConversationId)
        }
    }

    private suspend fun listCodexConversations(
        computer: RelayServer,
        endpoint: String,
    ): RemoteResult<List<RemoteConversationSummary>> {
        val conversations = linkedMapOf<String, RemoteConversationSummary>()
        for (archived in listOf(false, true)) {
            var cursor: String? = null
            do {
                val params = JSONObject()
                    .put("archived", archived)
                    .put("limit", 100)
                    .put("sortKey", "updated_at")
                    .put("sortDirection", "desc")
                cursor?.let { params.put("cursor", it) }
                when (val page = codexRpcRequest(computer, endpoint, "thread/list", params)) {
                    is RemoteResult.Error -> return RemoteResult.Error(page.message)
                    is RemoteResult.Success -> {
                        val data = page.value.optJSONArray("data") ?: JSONArray()
                        for (index in 0 until data.length()) {
                            val thread = data.getJSONObject(index)
                            val remoteId = thread.optString("id")
                            if (remoteId.isBlank()) continue
                            val preview = thread.optString("preview").trim()
                            val explicitName = if (thread.isNull("name")) "" else thread.optString("name").trim()
                            val title = explicitName.ifBlank { preview.take(80) }.ifBlank { "Codex chat" }
                            conversations[remoteId] = RemoteConversationSummary(
                                remoteId = remoteId,
                                title = title,
                                workspace = thread.optString("cwd", computer.workspace),
                                preview = preview,
                                modelProviderId = thread.optString("modelProvider").takeIf(String::isNotBlank),
                                createdAt = thread.optLong("createdAt") * 1_000L,
                                updatedAt = thread.optLong("updatedAt") * 1_000L,
                            )
                        }
                        cursor = if (page.value.isNull("nextCursor")) {
                            null
                        } else {
                            page.value.optString("nextCursor").takeIf(String::isNotBlank)
                        }
                    }
                }
            } while (cursor != null)
        }
        return RemoteResult.Success(conversations.values.sortedByDescending(RemoteConversationSummary::updatedAt))
    }

    private suspend fun listOpenCodeConversations(
        computer: RelayServer,
        endpoint: String,
    ): RemoteResult<List<RemoteConversationSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = endpoint.conversationHttpBase() +
                "/session?directory=${computer.workspace.conversationUrlEncode()}"
            val sessions = http.newCall(computer.authorizedRequest(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("Could not load OpenCode chats: HTTP ${response.code}")
                JSONArray(response.body?.string().orEmpty())
            }
            val result = buildList {
                for (index in 0 until sessions.length()) {
                    val session = sessions.getJSONObject(index)
                    if (!session.isNull("parentID")) continue
                    val remoteId = session.optString("id")
                    if (remoteId.isBlank()) continue
                    val time = session.optJSONObject("time") ?: JSONObject()
                    val model = session.optJSONObject("model")
                    val title = session.optString("title").trim().ifBlank { "OpenCode chat" }
                    add(
                        RemoteConversationSummary(
                            remoteId = remoteId,
                            title = title,
                            workspace = session.optString("directory", computer.workspace),
                            preview = title,
                            modelId = model?.optString("id")?.takeIf(String::isNotBlank),
                            modelProviderId = model?.optString("providerID")?.takeIf(String::isNotBlank),
                            createdAt = time.optLong("created", System.currentTimeMillis()),
                            updatedAt = time.optLong("updated", time.optLong("created", System.currentTimeMillis())),
                        ),
                    )
                }
            }
            RemoteResult.Success(result.sortedByDescending(RemoteConversationSummary::updatedAt))
        }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    private suspend fun loadCodexConversation(
        computer: RelayServer,
        endpoint: String,
        remoteConversationId: String,
    ): RemoteResult<List<RemoteChatMessage>> {
        val params = JSONObject().put("threadId", remoteConversationId).put("includeTurns", true)
        return when (val response = codexRpcRequest(computer, endpoint, "thread/read", params)) {
            is RemoteResult.Error -> response
            is RemoteResult.Success -> runCatching {
                val thread = response.value.getJSONObject("thread")
                val fallbackTime = thread.optLong("createdAt") * 1_000L
                val messages = buildList {
                    val turns = thread.optJSONArray("turns") ?: JSONArray()
                    for (turnIndex in 0 until turns.length()) {
                        val turn = turns.getJSONObject(turnIndex)
                        val turnTime = turn.optLong("startedAt").takeIf { it > 0 }?.times(1_000L)
                            ?: (fallbackTime + turnIndex * 1_000L)
                        val items = turn.optJSONArray("items") ?: JSONArray()
                        val finalAgentIndex = (0 until items.length())
                            .filter { items.optJSONObject(it)?.optString("type") == "agentMessage" }
                            .let { agentIndexes ->
                                agentIndexes.lastOrNull { index ->
                                    items.optJSONObject(index)?.optString("phase") == "final_answer"
                                } ?: agentIndexes.lastOrNull()
                            }
                        val activities = buildList {
                            for (itemIndex in 0 until items.length()) {
                                val item = items.optJSONObject(itemIndex) ?: continue
                                val type = item.optString("type")
                                val activity = when {
                                    type == "agentMessage" && itemIndex != finalAgentIndex -> {
                                        val text = item.optString("text").trim()
                                        if (text.isBlank()) null else RemoteChatActivity(
                                            remoteId = "agent-update:${item.optString("id", "$turnIndex:$itemIndex")}",
                                            title = "Agent update",
                                            detail = text,
                                            createdAt = turnTime + itemIndex,
                                            replacesMessageRemoteId = item.optString("id").takeIf(String::isNotBlank),
                                        )
                                    }
                                    type == "reasoning" -> RemoteChatActivity(
                                        remoteId = item.optString("id", "reasoning:$turnIndex:$itemIndex"),
                                        title = "Thinking",
                                        detail = item.reasoningSummary(),
                                        createdAt = turnTime + itemIndex,
                                    )
                                    type !in setOf("userMessage", "agentMessage", "hookPrompt") && type.isNotBlank() -> {
                                        val status = item.optString("status")
                                        RemoteChatActivity(
                                            remoteId = item.optString("id", "$type:$turnIndex:$itemIndex"),
                                            title = type.conversationHumanize(),
                                            detail = item.historicalActivityDetail(),
                                            failed = status in setOf("failed", "declined", "error"),
                                            createdAt = turnTime + itemIndex,
                                        )
                                    }
                                    else -> null
                                }
                                activity?.let(::add)
                            }
                        }
                        for (itemIndex in 0 until items.length()) {
                            val item = items.getJSONObject(itemIndex)
                            val body = when (item.optString("type")) {
                                "userMessage" -> {
                                    val content = item.optJSONArray("content") ?: JSONArray()
                                    buildList {
                                        for (contentIndex in 0 until content.length()) {
                                            val input = content.optJSONObject(contentIndex) ?: continue
                                            if (input.optString("type") == "text") add(input.optString("text"))
                                        }
                                    }.filter(String::isNotBlank).joinToString("\n")
                                }
                                "agentMessage" -> if (itemIndex == finalAgentIndex) item.optString("text") else ""
                                else -> ""
                            }.trim()
                            if (body.isBlank()) continue
                            val role = if (item.optString("type") == "userMessage") MessageRole.USER else MessageRole.ASSISTANT
                            val clientId = if (item.isNull("clientId")) "" else item.optString("clientId")
                            add(
                                RemoteChatMessage(
                                    remoteId = clientId.ifBlank { item.optString("id") }
                                        .ifBlank { "$remoteConversationId:$turnIndex:$itemIndex" },
                                    role = role,
                                    body = body,
                                    createdAt = turnTime + itemIndex,
                                    activities = if (itemIndex == finalAgentIndex) activities else emptyList(),
                                ),
                            )
                        }
                        if (finalAgentIndex == null && activities.isNotEmpty()) {
                            add(
                                RemoteChatMessage(
                                    remoteId = "$remoteConversationId:$turnIndex:activities",
                                    role = MessageRole.ASSISTANT,
                                    body = "",
                                    createdAt = turnTime + items.length(),
                                    activities = activities,
                                ),
                            )
                        }
                    }
                }
                RemoteResult.Success(messages)
            }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
        }
    }

    private suspend fun loadOpenCodeConversation(
        computer: RelayServer,
        endpoint: String,
        remoteConversationId: String,
    ): RemoteResult<List<RemoteChatMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = endpoint.conversationHttpBase() + "/session/$remoteConversationId/message" +
                "?directory=${computer.workspace.conversationUrlEncode()}"
            val history = http.newCall(computer.authorizedRequest(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) error("Could not load OpenCode history: HTTP ${response.code}")
                JSONArray(response.body?.string().orEmpty())
            }
            val messages = buildList {
                for (index in 0 until history.length()) {
                    val entry = history.getJSONObject(index)
                    val info = entry.optJSONObject("info") ?: continue
                    val role = when (info.optString("role")) {
                        "user" -> MessageRole.USER
                        "assistant" -> MessageRole.ASSISTANT
                        else -> continue
                    }
                    val parts = entry.optJSONArray("parts") ?: JSONArray()
                    val time = info.optJSONObject("time") ?: JSONObject()
                    val createdAt = time.optLong("created", System.currentTimeMillis())
                    val textPartIndexes = (0 until parts.length()).filter { index ->
                        val part = parts.optJSONObject(index)
                        part?.optString("type") == "text" && part.optString("text").isNotBlank()
                    }
                    val finalTextPartIndex = textPartIndexes.lastOrNull()
                    val body = if (role == MessageRole.USER) {
                        textPartIndexes.joinToString("\n") { index ->
                            parts.getJSONObject(index).optString("text")
                        }.trim()
                    } else {
                        finalTextPartIndex?.let { parts.getJSONObject(it).optString("text").trim() }.orEmpty()
                    }
                    val activities = if (role == MessageRole.ASSISTANT) {
                        buildList {
                            for (partIndex in 0 until parts.length()) {
                                val part = parts.optJSONObject(partIndex) ?: continue
                                val type = part.optString("type")
                                val partTime = part.optJSONObject("time")?.optLong("start", createdAt + partIndex)
                                    ?: (createdAt + partIndex)
                                val activity = when {
                                    type == "text" && partIndex != finalTextPartIndex -> {
                                        part.optString("text").trim().takeIf(String::isNotBlank)?.let { text ->
                                            RemoteChatActivity(
                                                remoteId = "agent-update:${part.optString("id", "$index:$partIndex")}",
                                                title = "Agent update",
                                                detail = text,
                                                createdAt = partTime,
                                            )
                                        }
                                    }
                                    type == "reasoning" -> RemoteChatActivity(
                                        remoteId = part.optString("id", "reasoning:$index:$partIndex"),
                                        title = "Thinking",
                                        detail = part.optString("text"),
                                        createdAt = partTime,
                                    )
                                    type == "tool" -> {
                                        val state = part.optJSONObject("state") ?: JSONObject()
                                        RemoteChatActivity(
                                            remoteId = part.optString("callID", part.optString("id", "tool:$index:$partIndex")),
                                            title = part.optString("tool", "Tool").conversationHumanize(),
                                            detail = state.openCodeToolDetail(),
                                            failed = state.optString("status") == "error",
                                            createdAt = partTime,
                                        )
                                    }
                                    else -> null
                                }
                                activity?.let(::add)
                            }
                        }
                    } else {
                        emptyList()
                    }
                    if (body.isBlank() && activities.isEmpty()) continue
                    add(
                        RemoteChatMessage(
                            remoteId = info.optString("id").ifBlank { "$remoteConversationId:$index" },
                            role = role,
                            body = body,
                            createdAt = createdAt,
                            updatedAt = time.optLong("completed", createdAt),
                            activities = activities,
                        ),
                    )
                }
            }
            RemoteResult.Success(messages)
        }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    private suspend fun codexRpcRequest(
        computer: RelayServer,
        endpoint: String,
        method: String,
        params: JSONObject,
    ): RemoteResult<JSONObject> = runCatching {
        val connection = codexConnections.connection(
            computer.id,
            computer.authorizedRequest(endpoint.conversationWebSocketBase()).build(),
        )
        RemoteResult.Success(connection.request(method, params))
    }.getOrElse { RemoteResult.Error(it.conversationMessage()) }

    override suspend fun testTunnel(
        profile: SshTunnelProfile,
        computerEndpoints: List<String>,
    ): RemoteResult<SshTunnelTest> = withContext(Dispatchers.IO) {
        runCatching { RemoteResult.Success(sshTunnels.test(profile, computerEndpoints)) }
            .getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    override suspend fun discoverServers(
        profile: SshTunnelProfile,
    ): RemoteResult<List<DiscoveredAgentServer>> = withContext(Dispatchers.IO) {
        runCatching {
            sshTunnels.test(profile.copy(routes = listOf(TunnelRouteRule("*"))), emptyList())
            val discovered = coroutineScope {
                serverDiscoveryCandidates(profile).map { candidate ->
                    async {
                        val computer = RelayServer(
                            name = candidate.kind.label,
                            kind = candidate.kind,
                            endpoint = candidate.endpoint,
                            workspace = "/home/${profile.ssh.username}",
                            routeMode = ServerRouteMode.TUNNEL,
                            tunnelProfileId = profile.id,
                            allowDirectFallback = false,
                        )
                        val routed = runCatching { sshTunnels.route(computer, listOf(profile)) }.getOrNull()
                            ?: return@async null
                        val url = when (candidate.kind) {
                            ServerKind.CODEX -> routed.url.conversationHttpBase() + "/readyz"
                            ServerKind.OPENCODE -> routed.url.conversationHttpBase() + "/global/health"
                        }
                        val started = System.nanoTime()
                        runCatching {
                            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                                val requiresAuthentication = response.code == 401
                                if (!response.isSuccessful && !requiresAuthentication) return@use null
                                val version = when {
                                    requiresAuthentication -> "Authentication required"
                                    candidate.kind == ServerKind.CODEX -> "Ready"
                                    else -> JSONObject(response.body?.string().orEmpty()).optString("version", "Online")
                                }
                                DiscoveredAgentServer(
                                    kind = candidate.kind,
                                    endpoint = candidate.endpoint,
                                    suggestedName = "${profile.name} · ${candidate.kind.label}",
                                    suggestedWorkspace = "/home/${profile.ssh.username}",
                                    version = version,
                                    latencyMs = (System.nanoTime() - started) / 1_000_000,
                                    requiresAuthentication = requiresAuthentication,
                                )
                            }
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            RemoteResult.Success(discovered.distinctBy { it.kind to it.endpoint })
        }.getOrElse { RemoteResult.Error(it.conversationMessage()) }
    }

    override fun closeTunnelProfile(profileId: String) {
        sshTunnels.closeProfile(profileId)
    }

    private suspend fun sendCodex(
        computer: RelayServer,
        endpoint: String,
        request: SendMessageRequest,
        onEvent: (AgentConversationEvent) -> Unit,
    ) {
        val terminal = AtomicBoolean(false)
        val accepted = AtomicBoolean(false)
        val completion = CompletableDeferred<Unit>()
        val reasoningSummaries = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
        var turnId: String? = null
        val connection = codexConnections.connection(
            computer.id,
            computer.authorizedRequest(endpoint.conversationWebSocketBase()).build(),
        )

        fun finish(event: AgentConversationEvent) {
            if (!terminal.compareAndSet(false, true)) return
            onEvent(event)
            activeRuns.remove(request.conversationId)
            completion.complete(Unit)
        }

        val threadOverrides = JSONObject()
            .put("cwd", request.workspace)
            .put("approvalPolicy", "never")
            .put("sandbox", "workspace-write")
        val threadId = try {
            request.remoteConversationId?.also {
                connection.attachThread(it, threadOverrides)
            } ?: connection.startThread(threadOverrides)
        } catch (error: Throwable) {
            onEvent(AgentConversationEvent.Failed(error.conversationMessage(), deliveryWasAccepted = false))
            return
        }

        val subscription = connection.observe(threadId, { turnId }) { message ->
            runCatching {
                if (turnId == null) return@runCatching
                val params = message.optJSONObject("params")
                when (message.optString("method")) {
                    "item/agentMessage/delta" -> {
                        val delta = params?.optString("delta").orEmpty()
                        if (delta.isNotEmpty()) {
                            onEvent(
                                AgentConversationEvent.AssistantDelta(
                                    delta,
                                    params?.optString("itemId")?.takeIf(String::isNotBlank),
                                ),
                            )
                        }
                    }
                    "item/reasoning/summaryTextDelta" -> {
                        val itemId = params?.optString("itemId").orEmpty()
                        val delta = params?.optString("delta").orEmpty()
                        if (itemId.isNotBlank() && delta.isNotEmpty()) {
                            val summaryIndex = params?.optInt("summaryIndex") ?: 0
                            val parts = reasoningSummaries.getOrPut(itemId) { ConcurrentHashMap() }
                            parts.compute(summaryIndex) { _, current -> current.orEmpty() + delta }
                            onEvent(AgentConversationEvent.ThinkingDelta(itemId, delta))
                        }
                    }
                    "item/started" -> {
                        val item = params?.optJSONObject("item") ?: return@runCatching
                        val type = item.optString("type")
                        if (type != "agentMessage" && type.isNotBlank()) {
                            onEvent(
                                AgentConversationEvent.ToolStarted(
                                    item.optString("id", type),
                                    if (type == "reasoning") "Thinking" else type.conversationHumanize(),
                                    item.optString("command"),
                                ),
                            )
                        }
                    }
                    "item/completed" -> {
                        val item = params?.optJSONObject("item") ?: return@runCatching
                        val type = item.optString("type")
                        if (type != "agentMessage" && type.isNotBlank()) {
                            val itemId = item.optString("id", type)
                            val status = item.optString("status")
                            val detail = if (type == "reasoning") {
                                item.reasoningSummary().ifBlank {
                                    reasoningSummaries[itemId]
                                        ?.toSortedMap()
                                        ?.values
                                        ?.joinToString("\n\n")
                                        .orEmpty()
                                }
                            } else {
                                status
                            }
                            onEvent(
                                AgentConversationEvent.ToolCompleted(
                                    itemId,
                                    if (type == "reasoning") "Thinking" else type.conversationHumanize(),
                                    detail,
                                    status == "failed" || status == "declined",
                                ),
                            )
                        }
                    }
                    "turn/completed" -> {
                        val turn = params?.optJSONObject("turn")
                        when (turn?.optString("status")) {
                            "failed" -> finish(
                                AgentConversationEvent.Failed(
                                    turn.optJSONObject("error")?.optString("message", "Codex turn failed")
                                        ?: "Codex turn failed",
                                    accepted.get(),
                                ),
                            )
                            "interrupted" -> finish(AgentConversationEvent.Stopped)
                            else -> finish(AgentConversationEvent.Completed)
                        }
                    }
                }
            }.onFailure {
                finish(AgentConversationEvent.Failed(it.conversationMessage(), accepted.get()))
            }
        }

        try {
            val input = JSONArray()
            if (request.text.isNotBlank()) {
                input.put(JSONObject().put("type", "text").put("text", request.text))
            }
            request.images.forEach { image ->
                input.put(JSONObject().put("type", "image").put("url", image.dataUrl()))
            }
            val turnParams = JSONObject()
                .put("threadId", threadId)
                .put("clientUserMessageId", request.clientMessageId)
                .put("input", input)
            request.modelId?.let { turnParams.put("model", it) }
            val result = connection.request("turn/start", turnParams)
            turnId = result.getJSONObject("turn").getString("id")
            accepted.set(true)
            activeRuns[request.conversationId] = ActiveRun.Codex(connection, threadId, turnId)
            onEvent(AgentConversationEvent.Accepted(threadId, turnId, request.clientMessageId))
            onEvent(AgentConversationEvent.AgentWorking)
            completion.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            finish(AgentConversationEvent.Failed(error.conversationMessage(), accepted.get()))
        } finally {
            subscription.close()
            activeRuns.remove(request.conversationId)
        }
    }

    private suspend fun sendOpenCode(
        computer: RelayServer,
        endpoint: String,
        request: SendMessageRequest,
        onEvent: (AgentConversationEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val accepted = AtomicBoolean(false)
        val reasoningPartIds = ConcurrentHashMap.newKeySet<String>()
        val base = endpoint.conversationHttpBase()
        val sessionId = request.remoteConversationId ?: runCatching {
            val create = computer.authorizedRequest(
                "$base/session?directory=${request.workspace.conversationUrlEncode()}",
            ).post(
                JSONObject().put("title", request.text.ifBlank { "Image" }.take(80))
                    .toString().toRequestBody(jsonType),
            ).build()
            http.newCall(create).execute().use { response ->
                if (!response.isSuccessful) error("Could not create chat: HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty()).getString("id")
            }
        }.getOrElse {
            onEvent(AgentConversationEvent.Failed(it.conversationMessage(), false))
            return@withContext
        }

        suspendCancellableCoroutine { continuation ->
            val terminal = AtomicBoolean(false)
            lateinit var eventCall: Call

            fun finish(event: AgentConversationEvent) {
                if (!terminal.compareAndSet(false, true)) return
                onEvent(event)
                activeRuns.remove(request.conversationId)
                eventCall.cancel()
                if (continuation.isActive) continuation.resume(Unit)
            }

            val eventRequest = computer.authorizedRequest(
                "$base/event?directory=${request.workspace.conversationUrlEncode()}",
            ).get().build()
            eventCall = http.newCall(eventRequest)
            activeRuns[request.conversationId] = ActiveRun.OpenCode(sessionId, eventCall) {
                finish(AgentConversationEvent.Stopped)
            }
            eventCall.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!terminal.get()) {
                        finish(AgentConversationEvent.Failed(error.conversationMessage(), accepted.get()))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            finish(AgentConversationEvent.Failed("Event stream returned HTTP ${response.code}", false))
                            return
                        }
                        scope.launch {
                            // A freshly created/resumed OpenCode session may accept prompt_async
                            // before its instance stream is ready, then drop the user message.
                            delay(350)
                            val remoteMessageId = request.openCodeMessageId()
                            val parts = JSONArray()
                            if (request.text.isNotBlank()) {
                                parts.put(JSONObject().put("type", "text").put("text", request.text))
                            }
                            request.images.forEach { image ->
                                parts.put(
                                    JSONObject()
                                        .put("type", "file")
                                        .put("mime", image.mimeType)
                                        .put("url", image.dataUrl()),
                                )
                            }
                            val promptBody = JSONObject()
                                .put("messageID", remoteMessageId)
                                .put("parts", parts)
                            if (request.modelId != null && request.modelProviderId != null) {
                                promptBody.put(
                                    "model",
                                    JSONObject()
                                        .put("providerID", request.modelProviderId)
                                        .put("modelID", request.modelId),
                                )
                            }
                            val promptRequestBody = promptBody.toString()
                                .toRequestBody(jsonType)
                            val promptRequest = computer.authorizedRequest(
                                "$base/session/$sessionId/prompt_async?directory=${request.workspace.conversationUrlEncode()}",
                            ).post(promptRequestBody).build()
                            runCatching {
                                http.newCall(promptRequest).execute().use { promptResponse ->
                                    if (!promptResponse.isSuccessful) {
                                        error("Could not send message: HTTP ${promptResponse.code}")
                                    }
                                }
                                accepted.set(true)
                                onEvent(AgentConversationEvent.Accepted(sessionId, remoteMessageId = remoteMessageId))
                                onEvent(AgentConversationEvent.AgentWorking)
                            }.onFailure {
                                finish(AgentConversationEvent.Failed(it.conversationMessage(), false))
                            }
                        }

                        val source = response.body?.source()
                        while (!terminal.get() && source != null && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            runCatching { JSONObject(line.removePrefix("data:").trim()) }
                                .onSuccess { event ->
                                    handleOpenCodeEvent(
                                        event,
                                        sessionId,
                                        accepted.get(),
                                        reasoningPartIds,
                                        onEvent,
                                        ::finish,
                                    )
                                }
                        }
                        if (!terminal.get()) {
                            finish(AgentConversationEvent.Failed("OpenCode event stream closed", accepted.get()))
                        }
                    }
                }
            })
            continuation.invokeOnCancellation {
                terminal.set(true)
                activeRuns.remove(request.conversationId)
                eventCall.cancel()
            }
        }
    }

    private fun handleOpenCodeEvent(
        event: JSONObject,
        sessionId: String,
        accepted: Boolean,
        reasoningPartIds: MutableSet<String>,
        onEvent: (AgentConversationEvent) -> Unit,
        finish: (AgentConversationEvent) -> Unit,
    ) {
        val properties = event.optJSONObject("properties") ?: return
        if (properties.optString("sessionID") != sessionId) return
        when (event.optString("type")) {
            "message.part.delta" -> if (properties.optString("field") == "text") {
                val partId = properties.optString("partID")
                val delta = properties.optString("delta")
                if (partId in reasoningPartIds) {
                    onEvent(AgentConversationEvent.ThinkingDelta(partId, delta))
                } else {
                    onEvent(
                        AgentConversationEvent.AssistantDelta(
                            delta,
                            properties.optString("messageID").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            "message.part.updated" -> {
                val part = properties.optJSONObject("part") ?: return
                when (part.optString("type")) {
                    "reasoning" -> {
                        val id = part.optString("id")
                        if (id.isBlank()) return
                        reasoningPartIds += id
                        val detail = part.optString("text")
                        val completed = part.optJSONObject("time")?.let { time ->
                            time.has("end") && !time.isNull("end")
                        } == true
                        if (completed) {
                            onEvent(AgentConversationEvent.ToolCompleted(id, "Thinking", detail))
                        } else {
                            onEvent(AgentConversationEvent.ToolStarted(id, "Thinking"))
                        }
                    }
                    "tool" -> {
                        val id = part.optString("callID", part.optString("id"))
                        val title = part.optString("tool", "Tool").conversationHumanize()
                        val state = part.optJSONObject("state")
                        when (state?.optString("status")) {
                            "pending", "running" -> onEvent(AgentConversationEvent.ToolStarted(id, title))
                            "completed" -> onEvent(AgentConversationEvent.ToolCompleted(id, title))
                            "error" -> onEvent(
                                AgentConversationEvent.ToolCompleted(
                                    id,
                                    title,
                                    state.optString("error"),
                                    failed = true,
                                ),
                            )
                        }
                    }
                }
            }
            "session.idle" -> if (accepted) {
                reasoningPartIds.forEach { id ->
                    onEvent(AgentConversationEvent.ToolCompleted(id, "Thinking"))
                }
                // OpenCode publishes idle just before its prompt loop has fully unwound.
                // Let that cleanup finish before a FIFO outbox starts the next prompt.
                scope.launch {
                    delay(350)
                    finish(AgentConversationEvent.Completed)
                }
            }
            "session.error" -> finish(
                AgentConversationEvent.Failed(
                    properties.optJSONObject("error")?.optString("name", "OpenCode failed")
                        ?: "OpenCode failed",
                    accepted,
                ),
            )
        }
    }

    private fun JSONObject.reasoningSummary(): String {
        val summary = optJSONArray("summary") ?: return ""
        return buildList {
            for (index in 0 until summary.length()) {
                summary.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString("\n\n")
    }

    private fun JSONObject.historicalActivityDetail(): String {
        val primary = sequenceOf("command", "query", "path", "text", "status")
            .map(::optString)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
        val output = optString("aggregatedOutput")
        return listOf(primary, output)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n\n")
    }

    private fun JSONObject.openCodeToolDetail(): String {
        val error = optString("error")
        if (error.isNotBlank()) return error
        val output = optString("output")
        if (output.isNotBlank()) return output
        val input = opt("input") ?: return optString("status")
        return if (input is String) input else input.toString()
    }

    override fun close() {
        activeRuns.values.forEach {
            when (it) {
                is ActiveRun.Codex -> Unit
                is ActiveRun.OpenCode -> it.events.cancel()
            }
        }
        activeRuns.clear()
        codexConnections.close()
        sshTunnels.close()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun RelayServer.authorizedRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (username.isNotBlank() || password.isNotBlank()) {
            val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            builder.header("Authorization", "Basic $token")
        }
        return builder
    }

    private sealed interface ActiveRun {
        data class Codex(
            val connection: CodexConnection,
            val threadId: String,
            val turnId: String?,
        ) : ActiveRun
        data class OpenCode(
            val sessionId: String,
            val events: Call,
            val stop: () -> Unit,
        ) : ActiveRun
    }
}

private fun String.conversationHttpBase(): String = trimEnd('/')
    .replaceFirst("ws://", "http://")
    .replaceFirst("wss://", "https://")

private fun String.conversationWebSocketBase(): String = trimEnd('/')
    .replaceFirst("http://", "ws://")
    .replaceFirst("https://", "wss://")

private fun String.conversationUrlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")

private fun String.conversationHumanize(): String = replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase() }

private fun Throwable.conversationMessage(): String = message?.takeIf(String::isNotBlank) ?: "Connection failed"

private fun MessageImage.dataUrl(): String {
    val file = File(filePath)
    require(file.isFile) { "Attached image is no longer available: $fileName" }
    val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    return "data:$mimeType;base64,$encoded"
}

/** Matches OpenCode's ascending Identifier.create("msg") layout while remaining retry-stable. */
private fun SendMessageRequest.openCodeMessageId(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(clientMessageId.toByteArray())
    val counter = ((digest[0].toInt() and 0xff) shl 4 or (digest[1].toInt() and 0x0f)).coerceAtLeast(1)
    val encodedTime = (createdAt * 0x1000L + counter) and 0x0000ffffffffffffL
    val timeHex = encodedTime.toString(16).padStart(12, '0')
    val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    val randomPart = buildString(14) {
        repeat(14) { index -> append(alphabet[(digest[index + 2].toInt() and 0xff) % alphabet.length]) }
    }
    return "msg_$timeHex$randomPart"
}
