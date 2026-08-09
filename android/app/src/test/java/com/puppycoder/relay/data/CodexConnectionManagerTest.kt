package com.puppycoder.relay.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CodexConnectionManagerTest {
    @Test
    fun multiplexesOutOfOrderResponsesOverOneInitializedSocket() = runBlocking {
        val factory = FakeWebSocketFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val requests = CopyOnWriteArrayList<Pair<FakeWebSocket, JSONObject>>()
        factory.onRequest = { socket, message ->
            if (message.optString("method") == "initialize") {
                socket.respond(message.getLong("id"), JSONObject())
            } else {
                requests += socket to message
                if (requests.size == 2) {
                    requests.reversed().forEach { (target, request) ->
                        target.respond(
                            request.getLong("id"),
                            JSONObject().put("method", request.getString("method")),
                        )
                    }
                }
            }
        }

        val connection = CodexConnection(factory, REQUEST, scope)
        try {
            val results = listOf("model/list", "thread/list").map { method ->
                async { connection.request(method, JSONObject()).getString("method") }
            }.awaitAll()

            assertEquals(listOf("model/list", "thread/list"), results)
            assertEquals(1, factory.sockets.size)
            assertEquals(
                1,
                factory.sockets.single().sent.count { it.optString("method") == "initialize" },
            )
        } finally {
            connection.close()
            scope.cancel()
            factory.close()
        }
    }

    @Test
    fun resumesThreadOnlyOnceWhileConnectionRemainsAttached() = runBlocking {
        val factory = FakeWebSocketFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        factory.onRequest = { socket, message ->
            val result = when (message.optString("method")) {
                "thread/resume" -> JSONObject().put(
                    "thread",
                    JSONObject().put("id", message.getJSONObject("params").getString("threadId")),
                )
                else -> JSONObject()
            }
            socket.respond(message.getLong("id"), result)
        }

        val connection = CodexConnection(factory, REQUEST, scope)
        try {
            connection.attachThread("thread-1", JSONObject().put("cwd", "/workspace"))
            connection.attachThread("thread-1", JSONObject().put("cwd", "/workspace"))

            assertEquals(
                1,
                factory.sockets.single().sent.count { it.optString("method") == "thread/resume" },
            )
        } finally {
            connection.close()
            scope.cancel()
            factory.close()
        }
    }

    @Test
    fun routesNotificationsAndReattachesAfterReconnect() = runBlocking {
        val factory = FakeWebSocketFactory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resumeCount = AtomicInteger()
        factory.onRequest = { socket, message ->
            val result = when (message.optString("method")) {
                "thread/resume" -> {
                    resumeCount.incrementAndGet()
                    JSONObject().put(
                        "thread",
                        JSONObject().put("id", message.getJSONObject("params").getString("threadId")),
                    )
                }
                "thread/read" -> JSONObject().put(
                    "thread",
                    JSONObject().put("turns", JSONArray()),
                )
                else -> JSONObject()
            }
            socket.respond(message.getLong("id"), result)
        }

        val connection = CodexConnection(factory, REQUEST, scope)
        val event = CompletableDeferred<String>()
        var turnId: String? = "turn-1"
        var subscription: Closeable? = null
        try {
            connection.attachThread("thread-1", JSONObject().put("cwd", "/workspace"))
            subscription = connection.observe("thread-1", { turnId }) {
                event.complete(it.getJSONObject("params").getString("delta"))
            }
            factory.sockets.single().emit(
                JSONObject()
                    .put("method", "item/agentMessage/delta")
                    .put(
                        "params",
                        JSONObject()
                            .put("threadId", "thread-1")
                            .put("turnId", "turn-1")
                            .put("delta", "hello"),
                    ),
            )
            assertEquals("hello", withTimeout(2_000) { event.await() })

            factory.sockets.single().fail(IOException("network changed"))
            withTimeout(4_000) {
                while (factory.sockets.size < 2 || resumeCount.get() < 2) delay(25)
            }
            assertEquals(2, factory.sockets.size)
            assertEquals(2, resumeCount.get())
            assertTrue(factory.sockets.all { socket ->
                socket.sent.count { it.optString("method") == "initialize" } == 1
            })
            turnId = null
        } finally {
            subscription?.close()
            connection.close()
            scope.cancel()
            factory.close()
        }
    }

    private companion object {
        val REQUEST: Request = Request.Builder().url("ws://127.0.0.1:4310").build()
    }
}

private class FakeWebSocketFactory : WebSocket.Factory, Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fake-codex-websocket").apply { isDaemon = true }
    }
    val sockets = CopyOnWriteArrayList<FakeWebSocket>()
    @Volatile var onRequest: (FakeWebSocket, JSONObject) -> Unit = { socket, message ->
        socket.respond(message.getLong("id"), JSONObject())
    }

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val socket = FakeWebSocket(request, listener, this)
        sockets += socket
        executor.execute {
            listener.onOpen(
                socket,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("Switching Protocols")
                    .build(),
            )
        }
        return socket
    }

    fun dispatch(block: () -> Unit) {
        executor.execute(block)
    }

    override fun close() {
        executor.shutdownNow()
    }
}

private class FakeWebSocket(
    private val originalRequest: Request,
    private val listener: WebSocketListener,
    private val factory: FakeWebSocketFactory,
) : WebSocket {
    val sent = CopyOnWriteArrayList<JSONObject>()
    @Volatile private var open = true

    override fun request(): Request = originalRequest

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        if (!open) return false
        val message = JSONObject(text)
        sent += message
        if (message.has("id")) factory.dispatch { factory.onRequest(this, message) }
        return true
    }

    override fun send(bytes: ByteString): Boolean = send(bytes.utf8())

    override fun close(code: Int, reason: String?): Boolean {
        if (!open) return false
        open = false
        factory.dispatch { listener.onClosed(this, code, reason.orEmpty()) }
        return true
    }

    override fun cancel() {
        fail(IOException("cancelled"))
    }

    fun respond(id: Long, result: JSONObject) {
        emit(JSONObject().put("id", id).put("result", result))
    }

    fun emit(message: JSONObject) {
        factory.dispatch { listener.onMessage(this, message.toString()) }
    }

    fun fail(error: Throwable) {
        if (!open) return
        open = false
        factory.dispatch { listener.onFailure(this, error, null) }
    }
}
