package com.puppycoder.relay.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

private const val CODEX_PING_INTERVAL_SECONDS = 20L
private const val CODEX_INITIAL_RECONNECT_DELAY_MS = 500L
private const val CODEX_MAX_RECONNECT_DELAY_MS = 15_000L

/** Owns one long-lived, multiplexed app-server transport for each configured Codex server. */
internal class CodexConnectionManager(
    http: OkHttpClient,
    private val scope: CoroutineScope,
) : Closeable {
    private val socketHttp = http.newBuilder()
        .pingInterval(CODEX_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()
    private val connections = ConcurrentHashMap<String, ManagedConnection>()

    fun connection(serverId: String, request: Request): CodexConnection {
        val signature = buildString {
            append(request.url)
            append('\n')
            append(request.header("Authorization").orEmpty())
        }
        return synchronized(connections) {
            val current = connections[serverId]
            if (current != null && current.signature == signature) return@synchronized current.connection

            val replacement = ManagedConnection(
                signature,
                CodexConnection(socketHttp, request, scope),
            )
            connections[serverId] = replacement
            current?.connection?.close()
            replacement.connection
        }
    }

    override fun close() {
        connections.values.forEach { it.connection.close() }
        connections.clear()
    }

    private data class ManagedConnection(
        val signature: String,
        val connection: CodexConnection,
    )
}

/** A single JSON-RPC connection that routes responses by id and notifications by thread/turn id. */
internal class CodexConnection(
    private val webSocketFactory: WebSocket.Factory,
    private val request: Request,
    private val scope: CoroutineScope,
) : Closeable {
    private val stateLock = Any()
    private val closed = AtomicBoolean(false)
    private val nextRpcId = AtomicLong(1)
    private val generation = AtomicLong(0)
    private val reconnectAttempt = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val attachments = ConcurrentHashMap<String, ThreadAttachment>()
    private val attachmentLocks = ConcurrentHashMap<String, Mutex>()
    private val observers = CopyOnWriteArrayList<NotificationObserver>()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = CompletableDeferred<Unit>()
    private var reconnectJob: Job? = null

    init {
        synchronized(stateLock) { openSocketLocked() }
    }

    suspend fun request(method: String, params: JSONObject): JSONObject =
        requestWithGeneration(method, params).result

    private suspend fun requestWithGeneration(method: String, params: JSONObject): RpcResponse {
        val webSocket = awaitSocket()
        val requestGeneration = generation.get()
        val id = nextRpcId.getAndIncrement()
        val response = CompletableDeferred<JSONObject>()
        pending[id] = response
        val sent = webSocket.send(rpc(id, method, params).toString())
        if (!sent) {
            pending.remove(id)
            throw IOException("Codex connection is reconnecting")
        }
        return try {
            val message = response.await()
            val error = message.optJSONObject("error")
            if (error != null) {
                throw CodexRpcException(
                    error.optInt("code"),
                    error.optString("message", "Codex request failed"),
                )
            }
            RpcResponse(message.getJSONObject("result"), requestGeneration)
        } finally {
            pending.remove(id)
        }
    }

    suspend fun startThread(params: JSONObject): String {
        val response = requestWithGeneration("thread/start", params)
        val threadId = response.result.getJSONObject("thread").getString("id")
        attachments[threadId] = ThreadAttachment(copyJson(params), response.generation)
        return threadId
    }

    suspend fun attachThread(threadId: String, overrides: JSONObject) {
        val attachment = attachments.compute(threadId) { _, current ->
            (current ?: ThreadAttachment(copyJson(overrides), 0)).also {
                it.overrides = copyJson(overrides)
            }
        } ?: return
        val lock = attachmentLocks.getOrPut(threadId) { Mutex() }
        lock.withLock {
            val activeGeneration = generation.get()
            if (activeGeneration > 0 && attachment.generation == activeGeneration) return
            val params = copyJson(attachment.overrides).put("threadId", threadId)
            val response = requestWithGeneration("thread/resume", params)
            val resumedId = response.result.getJSONObject("thread").getString("id")
            check(resumedId == threadId) { "Codex resumed unexpected thread $resumedId" }
            attachment.generation = response.generation
        }
    }

    fun observe(
        threadId: String,
        turnId: () -> String?,
        onNotification: (JSONObject) -> Unit,
    ): Closeable {
        val observer = NotificationObserver(threadId, turnId, onNotification)
        observers += observer
        val released = AtomicBoolean(false)
        return Closeable {
            if (released.compareAndSet(false, true) && observers.remove(observer)) {
                scope.launch { unsubscribeThreadIfUnused(threadId) }
            }
        }
    }

    private suspend fun unsubscribeThreadIfUnused(threadId: String) {
        val lock = attachmentLocks.getOrPut(threadId) { Mutex() }
        lock.withLock {
            if (observers.any { it.threadId == threadId }) return
            val attachment = attachments[threadId] ?: return
            val activeGeneration = generation.get()
            if (socket == null || attachment.generation != activeGeneration) {
                attachment.generation = 0
                return
            }
            attachment.generation = 0
            runCatching {
                requestWithGeneration("thread/unsubscribe", JSONObject().put("threadId", threadId))
            }
        }
    }

    private suspend fun awaitSocket(): WebSocket {
        while (true) {
            val waiting = synchronized(stateLock) {
                if (closed.get()) throw IOException("Codex connection is closed")
                if (socket == null && reconnectJob == null) openSocketLocked()
                ready
            }
            waiting.await()
            socket?.let { return it }
        }
    }

    private fun openSocketLocked() {
        if (closed.get() || socket != null) return
        if (ready.isCompleted) ready = CompletableDeferred()
        lateinit var openedSocket: WebSocket
        val listener = object : WebSocketListener() {
            private val initializeId = nextRpcId.getAndIncrement()

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(webSocket)) return
                val initialize = JSONObject()
                    .put("id", initializeId)
                    .put("method", "initialize")
                    .put(
                        "params",
                        JSONObject()
                            .put(
                                "clientInfo",
                                JSONObject()
                                    .put("name", "puppycoder_android")
                                    .put("title", "PuppyCoder for Android")
                                    .put("version", PUPPYCODER_CLIENT_VERSION),
                            )
                            .put(
                                "capabilities",
                                JSONObject().put("experimentalApi", true),
                            ),
                    )
                if (!webSocket.send(initialize.toString())) {
                    disconnect(webSocket, IOException("Could not initialize Codex connection"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                runCatching {
                    val message = JSONObject(text)
                    val id = message.optLong("id", -1)
                    if (id == initializeId) {
                        message.optJSONObject("error")?.let {
                            throw CodexRpcException(
                                it.optInt("code"),
                                it.optString("message", "Codex initialization failed"),
                            )
                        }
                        check(webSocket.send(JSONObject().put("method", "initialized").put("params", JSONObject()).toString()))
                        generation.incrementAndGet()
                        reconnectAttempt.set(0)
                        ready.complete(Unit)
                        reattachObservedThreads()
                        return
                    }
                    if (id >= 0) {
                        pending.remove(id)?.complete(message)
                    } else {
                        dispatchNotification(message)
                    }
                }.onFailure { disconnect(webSocket, it) }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                disconnect(webSocket, error)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnect(webSocket, IOException("Codex connection closed ($code): $reason"))
            }
        }
        openedSocket = webSocketFactory.newWebSocket(request, listener)
        socket = openedSocket
    }

    private fun isCurrent(candidate: WebSocket): Boolean = socket === candidate && !closed.get()

    private fun disconnect(candidate: WebSocket, cause: Throwable) {
        synchronized(stateLock) {
            if (socket !== candidate) return
            socket = null
            val disconnectedReady = ready
            if (!disconnectedReady.isCompleted) disconnectedReady.completeExceptionally(cause)
            ready = CompletableDeferred()
            val failure = IOException(cause.message ?: "Codex connection lost", cause)
            pending.values.forEach { it.completeExceptionally(failure) }
            pending.clear()
            if (!closed.get()) scheduleReconnectLocked()
        }
    }

    private fun scheduleReconnectLocked() {
        if (reconnectJob != null) return
        val attempt = reconnectAttempt.getAndIncrement().coerceAtMost(10)
        val exponential = min(
            CODEX_MAX_RECONNECT_DELAY_MS,
            CODEX_INITIAL_RECONNECT_DELAY_MS * (1L shl attempt),
        )
        val jitteredDelay = Random.nextLong(exponential / 2, exponential + 1)
        reconnectJob = scope.launch {
            delay(jitteredDelay)
            synchronized(stateLock) {
                reconnectJob = null
                if (!closed.get() && socket == null) openSocketLocked()
            }
        }
    }

    private fun reattachObservedThreads() {
        val threadIds = observers.map { it.threadId }.distinct()
        threadIds.forEach { threadId ->
            val attachment = attachments[threadId] ?: return@forEach
            scope.launch {
                runCatching {
                    attachThread(threadId, attachment.overrides)
                    recoverTerminalTurns(threadId)
                }
            }
        }
    }

    private suspend fun recoverTerminalTurns(threadId: String) {
        val result = request(
            "thread/read",
            JSONObject().put("threadId", threadId).put("includeTurns", true),
        )
        val turns = result.optJSONObject("thread")?.optJSONArray("turns") ?: return
        for (index in 0 until turns.length()) {
            val turn = turns.optJSONObject(index) ?: continue
            if (turn.optString("status") !in setOf("completed", "interrupted", "failed")) continue
            dispatchNotification(
                JSONObject()
                    .put("method", "turn/completed")
                    .put("params", JSONObject().put("threadId", threadId).put("turn", turn)),
            )
        }
    }

    private fun dispatchNotification(message: JSONObject) {
        val params = message.optJSONObject("params") ?: JSONObject()
        val threadId = params.optString("threadId").ifBlank {
            params.optJSONObject("thread")?.optString("id").orEmpty()
        }
        val turnId = params.optString("turnId").ifBlank {
            params.optJSONObject("turn")?.optString("id").orEmpty()
        }
        observers.forEach { observer ->
            if (threadId.isNotBlank() && observer.threadId != threadId) return@forEach
            val observedTurn = observer.turnId()
            if (turnId.isNotBlank() && observedTurn != null && observedTurn != turnId) return@forEach
            runCatching { observer.onNotification(message) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(stateLock) {
            reconnectJob?.cancel()
            reconnectJob = null
            val failure = IOException("Codex connection is closed")
            if (!ready.isCompleted) ready.completeExceptionally(failure)
            pending.values.forEach { it.completeExceptionally(failure) }
            pending.clear()
            socket?.close(1000, "PuppyCoder closed")
            socket = null
        }
        observers.clear()
        attachments.clear()
        attachmentLocks.clear()
    }

    private fun rpc(id: Long, method: String, params: JSONObject): JSONObject = JSONObject()
        .put("id", id)
        .put("method", method)
        .put("params", params)

    private fun copyJson(value: JSONObject): JSONObject = JSONObject(value.toString())

    private data class ThreadAttachment(
        @Volatile var overrides: JSONObject,
        @Volatile var generation: Long,
    )

    private data class NotificationObserver(
        val threadId: String,
        val turnId: () -> String?,
        val onNotification: (JSONObject) -> Unit,
    )

    private data class RpcResponse(
        val result: JSONObject,
        val generation: Long,
    )
}

internal class CodexRpcException(
    val code: Int,
    message: String,
) : IOException(message)
