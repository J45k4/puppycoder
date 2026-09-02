package com.puppycoder.relay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puppycoder.relay.PuppyCoderApplication
import com.puppycoder.relay.data.ChatListItem
import com.puppycoder.relay.data.ChatMessage
import com.puppycoder.relay.data.AgentModel
import com.puppycoder.relay.data.ConnectionState
import com.puppycoder.relay.data.Conversation
import com.puppycoder.relay.data.ConversationState
import com.puppycoder.relay.data.DiscoveredAgentServer
import com.puppycoder.relay.data.DownloadedRemoteFile
import com.puppycoder.relay.data.RelayServer
import com.puppycoder.relay.data.RemoteResult
import com.puppycoder.relay.data.RemoteFileProgress
import com.puppycoder.relay.data.SshIdentity
import com.puppycoder.relay.data.SshConnection
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.SshTunnelTest
import com.puppycoder.relay.data.ToolActivity
import com.puppycoder.relay.data.TunnelRouteRule
import com.puppycoder.relay.update.AppUpdateState
import java.io.Closeable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PuppyCoderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PuppyCoderApplication).repository
    private val updateManager = (application as PuppyCoderApplication).updateManager
    private val chatListPreferences = application.getSharedPreferences("chat_list_preferences", 0)
    private val selectedChatId = MutableStateFlow<String?>(null)
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val _modelPicker = MutableStateFlow(ModelPickerState())
    private val _chatSync = MutableStateFlow(ChatSyncState())
    private val _historyPaging = MutableStateFlow(HistoryPagingState())
    private val _chatSearchQuery = MutableStateFlow("")
    private val _serverDiscovery = MutableStateFlow(ServerDiscoveryState())
    private val _tunnelTests = MutableStateFlow<Map<String, TunnelTestState>>(emptyMap())
    private val _sshConnectionTests = MutableStateFlow<Map<String, TunnelTestState>>(emptyMap())
    private val _remoteFileViewer = MutableStateFlow(RemoteFileViewerState())
    private val _writerClaim = MutableStateFlow(WriterClaimState())
    private val remoteFilePreviewCache = mutableMapOf<String, DownloadedRemoteFile>()
    private val _chatSortOrder = MutableStateFlow(
        runCatching {
            ChatSortOrder.valueOf(chatListPreferences.getString("sort_order", null).orEmpty())
        }.getOrDefault(ChatSortOrder.RECENT),
    )
    private val _chatGroupMode = MutableStateFlow(
        runCatching {
            ChatGroupMode.valueOf(chatListPreferences.getString("group_mode", null).orEmpty())
        }.getOrDefault(ChatGroupMode.NONE),
    )
    private val _collapsedChatGroups = MutableStateFlow(
        chatListPreferences.getStringSet("collapsed_groups", emptySet())?.toSet().orEmpty(),
    )
    private val historySyncMutex = Mutex()
    private var openConversationSubscription: Closeable? = null
    private var openConversationSubscriptionPendingFor: String? = null
    private var openConversationSubscriptionPendingGeneration = -1L
    private var conversationTrackingGeneration = 0L
    private var trackedConversationId: String? = null
    private var liveHistoryRefreshJob: Job? = null
    private var remoteFileDownloadJob: Job? = null
    private var liveHistoryRefreshPending = false
    private var chatScrollInProgress = false
    private var chatViewportAtLatest = true

    val notices = _notices.asSharedFlow()
    val appUpdate: StateFlow<AppUpdateState> = updateManager.state
    val modelPicker: StateFlow<ModelPickerState> = _modelPicker
    val chatSync: StateFlow<ChatSyncState> = _chatSync
    val historyPaging: StateFlow<HistoryPagingState> = _historyPaging
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery
    val serverDiscovery: StateFlow<ServerDiscoveryState> = _serverDiscovery
    val tunnelTests: StateFlow<Map<String, TunnelTestState>> = _tunnelTests
    val sshConnectionTests: StateFlow<Map<String, TunnelTestState>> = _sshConnectionTests
    val remoteFileViewer: StateFlow<RemoteFileViewerState> = _remoteFileViewer
    val writerClaim: StateFlow<WriterClaimState> = _writerClaim
    val chatSortOrder: StateFlow<ChatSortOrder> = _chatSortOrder
    val chatGroupMode: StateFlow<ChatGroupMode> = _chatGroupMode
    val collapsedChatGroups: StateFlow<Set<String>> = _collapsedChatGroups
    val chats: StateFlow<List<ChatListItem>> = _chatSearchQuery.flatMapLatest(repository::searchChats).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val computers: StateFlow<List<RelayServer>> = repository.computers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val tunnels: StateFlow<List<SshTunnelProfile>> = repository.tunnels.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val identities: StateFlow<List<SshIdentity>> = repository.identities.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val sshConnections: StateFlow<List<SshConnection>> = repository.sshConnections.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val selectedConversation: StateFlow<Conversation?> = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.conversation(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            selectedConversation.collect { conversation ->
                conversation?.let {
                    if (
                        it.id == trackedConversationId &&
                        it.remoteConversationId != null &&
                        openConversationSubscription == null
                    ) {
                        ensureOpenConversationSubscription(it.id)
                    }
                }
            }
        }
    }

    private val allMessages = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }
    val messages: StateFlow<List<ChatMessage>> = combine(
        allMessages,
        _historyPaging,
        ::visibleMessagesForHistory,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val allTools = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.tools(id)
    }
    val tools: StateFlow<List<ToolActivity>> = combine(allTools, messages, _historyPaging) { tools, messages, paging ->
        if (paging.initialLoading) return@combine tools
        val visibleMessageIds = messages.mapTo(hashSetOf(), ChatMessage::id)
        tools.filter { activity ->
            activity.messageId?.let(visibleMessageIds::contains)
                ?: (
                    paging.allHistoryLoaded || activity.createdAt >= paging.openedAt ||
                        paging.oldestLoadedAt?.let { activity.createdAt >= it } == true
                    )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openChat(id: String) {
        stopTrackingOpenConversation()
        _writerClaim.value = WriterClaimState()
        trackedConversationId = id
        _historyPaging.value = HistoryPagingState(
            conversationId = id,
            initialLoading = true,
            openedAt = System.currentTimeMillis(),
        )
        selectedChatId.value = id
        viewModelScope.launch {
            val cachedOldestAt = repository.oldestStoredMessageAt(id)
            ensureOpenConversationSubscription(id)
            when (val result = historySyncMutex.withLock { repository.syncConversationHistory(id) }) {
                is RemoteResult.Success -> {
                    if (_historyPaging.value.conversationId == id) {
                        _historyPaging.value = _historyPaging.value.withPage(
                            result.value,
                            initial = true,
                            cachedOldestAt = cachedOldestAt,
                        )
                    }
                }
                is RemoteResult.Error -> {
                    if (_historyPaging.value.conversationId == id) {
                        _historyPaging.value = _historyPaging.value.copy(
                            initialLoading = false,
                            allHistoryLoaded = true,
                        )
                    }
                    _notices.emit("Could not load server history: ${result.message}")
                }
            }
        }
    }

    fun loadOlderMessages() {
        val current = _historyPaging.value
        val id = selectedChatId.value ?: return
        val cursor = current.nextCursor ?: return
        if (current.conversationId != id || current.initialLoading || current.loadingOlder) return
        _historyPaging.value = current.copy(loadingOlder = true, olderLoadError = null)
        viewModelScope.launch {
            when (val result = historySyncMutex.withLock { repository.syncConversationHistory(id, cursor) }) {
                is RemoteResult.Success -> {
                    if (_historyPaging.value.conversationId == id) {
                        _historyPaging.value = _historyPaging.value.withPage(result.value, initial = false)
                    }
                }
                is RemoteResult.Error -> {
                    if (_historyPaging.value.conversationId == id) {
                        _historyPaging.value = _historyPaging.value.copy(
                            loadingOlder = false,
                            olderLoadError = result.message,
                        )
                    }
                    _notices.emit("Could not load older messages: ${result.message}")
                }
            }
        }
    }

    fun closeChat() {
        stopTrackingOpenConversation()
        selectedChatId.value = null
        _historyPaging.value = HistoryPagingState()
        _writerClaim.value = WriterClaimState()
    }

    private fun scheduleLiveHistoryRefresh(conversationId: String) {
        viewModelScope.launch {
            if (trackedConversationId != conversationId || selectedChatId.value != conversationId) return@launch
            if (chatScrollInProgress) {
                liveHistoryRefreshPending = true
                return@launch
            }
            if (selectedConversation.value?.state in setOf(ConversationState.SENDING, ConversationState.WORKING)) {
                return@launch
            }
            liveHistoryRefreshPending = true
            if (liveHistoryRefreshJob?.isActive == true) return@launch
            liveHistoryRefreshJob = viewModelScope.launch {
                delay(LIVE_HISTORY_REFRESH_INTERVAL_MS)
                while (liveHistoryRefreshPending && trackedConversationId == conversationId) {
                    liveHistoryRefreshPending = false
                    when (val result = historySyncMutex.withLock {
                        repository.syncConversationHistory(conversationId)
                    }) {
                        is RemoteResult.Success -> {
                            if (_historyPaging.value.conversationId == conversationId) {
                                _historyPaging.value = _historyPaging.value.withPage(
                                    result.value,
                                    initial = true,
                                    cachedOldestAt = _historyPaging.value.oldestLoadedAt,
                                )
                            }
                        }
                        is RemoteResult.Error -> Unit
                    }
                    if (liveHistoryRefreshPending) delay(LIVE_HISTORY_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    fun setChatScrollInProgress(inProgress: Boolean) {
        if (chatScrollInProgress == inProgress) return
        chatScrollInProgress = inProgress
        if (!inProgress && liveHistoryRefreshPending) {
            trackedConversationId?.let(::scheduleLiveHistoryRefresh)
        }
    }

    fun setChatViewportAtLatest(atLatest: Boolean) {
        if (chatViewportAtLatest == atLatest) return
        chatViewportAtLatest = atLatest
        if (atLatest && !chatScrollInProgress) {
            trackedConversationId?.let(::scheduleLiveHistoryRefresh)
        }
    }

    private suspend fun ensureOpenConversationSubscription(conversationId: String) {
        val generation = conversationTrackingGeneration
        if (
            trackedConversationId != conversationId ||
            selectedChatId.value != conversationId ||
            openConversationSubscription != null ||
            (
                openConversationSubscriptionPendingFor == conversationId &&
                    openConversationSubscriptionPendingGeneration == generation
                )
        ) {
            return
        }
        openConversationSubscriptionPendingFor = conversationId
        openConversationSubscriptionPendingGeneration = generation
        val result = repository.subscribeConversation(conversationId) {
            scheduleLiveHistoryRefresh(conversationId)
        }
        if (openConversationSubscriptionPendingGeneration == generation) {
            openConversationSubscriptionPendingFor = null
            openConversationSubscriptionPendingGeneration = -1L
        }
        when (result) {
            is RemoteResult.Success -> {
                if (_writerClaim.value.conversationId == conversationId) _writerClaim.value = WriterClaimState()
                if (
                    conversationTrackingGeneration == generation &&
                    trackedConversationId == conversationId &&
                    selectedChatId.value == conversationId
                ) {
                    openConversationSubscription = result.value
                } else {
                    result.value?.close()
                }
            }
            is RemoteResult.Error -> {
                if (
                    conversationTrackingGeneration == generation &&
                    trackedConversationId == conversationId &&
                    result.message.isActiveWriterConflict()
                ) {
                    _writerClaim.value = WriterClaimState(conversationId = conversationId, message = result.message)
                    openConversationSubscription = startOpenConversationPolling(conversationId, generation)
                } else if (conversationTrackingGeneration == generation && trackedConversationId == conversationId) {
                    _notices.emit("Live updates unavailable: ${result.message}")
                }
            }
        }
    }

    fun showWriterClaimDialog() {
        _writerClaim.update { if (it.conversationId != null) it.copy(dialogVisible = true) else it }
    }

    fun dismissWriterClaim() {
        _writerClaim.update { it.copy(dialogVisible = false) }
    }

    fun forceClaimWriter() {
        val conflict = _writerClaim.value
        val conversationId = conflict.conversationId ?: return
        if (conflict.claiming || selectedChatId.value != conversationId) return
        _writerClaim.value = conflict.copy(claiming = true)
        viewModelScope.launch {
            when (val result = repository.forceClaimConversation(conversationId)) {
                is RemoteResult.Success -> {
                    openConversationSubscription?.close()
                    openConversationSubscription = null
                    _writerClaim.value = WriterClaimState()
                    ensureOpenConversationSubscription(conversationId)
                }
                is RemoteResult.Error -> {
                    _writerClaim.value = _writerClaim.value.copy(claiming = false, message = result.message)
                }
            }
        }
    }

    private fun startOpenConversationPolling(conversationId: String, generation: Long): Closeable {
        val job = viewModelScope.launch {
            while (
                conversationTrackingGeneration == generation &&
                trackedConversationId == conversationId &&
                selectedChatId.value == conversationId
            ) {
                delay(
                    if (chatViewportAtLatest) {
                        LIVE_HISTORY_POLL_INTERVAL_MS
                    } else {
                        BACKGROUND_HISTORY_POLL_INTERVAL_MS
                    },
                )
                scheduleLiveHistoryRefresh(conversationId)
            }
        }
        return Closeable { job.cancel() }
    }

    private fun stopTrackingOpenConversation() {
        conversationTrackingGeneration += 1
        trackedConversationId = null
        openConversationSubscriptionPendingFor = null
        openConversationSubscriptionPendingGeneration = -1L
        openConversationSubscription?.close()
        openConversationSubscription = null
        liveHistoryRefreshJob?.cancel()
        liveHistoryRefreshJob = null
        liveHistoryRefreshPending = false
        chatScrollInProgress = false
        chatViewportAtLatest = true
    }

    override fun onCleared() {
        stopTrackingOpenConversation()
        super.onCleared()
    }

    fun setChatSearchQuery(query: String) {
        _chatSearchQuery.value = query
    }

    fun setChatSortOrder(sortOrder: ChatSortOrder) {
        _chatSortOrder.value = sortOrder
        chatListPreferences.edit().putString("sort_order", sortOrder.name).apply()
    }

    fun setChatGroupMode(groupMode: ChatGroupMode) {
        _chatGroupMode.value = groupMode
        chatListPreferences.edit().putString("group_mode", groupMode.name).apply()
    }

    fun toggleChatGroup(groupKey: String) {
        val collapsed = _collapsedChatGroups.value
        val updated = if (groupKey in collapsed) collapsed - groupKey else collapsed + groupKey
        _collapsedChatGroups.value = updated
        chatListPreferences.edit().putStringSet("collapsed_groups", updated).apply()
    }

    fun syncChats() {
        if (_chatSync.value.loading) return
        _chatSync.value = _chatSync.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.syncRemoteChats() }
                .onSuccess { report ->
                    _chatSync.value = ChatSyncState(
                        loading = false,
                        lastSyncAt = System.currentTimeMillis(),
                        conversationsSeen = report.conversationsSeen,
                        error = report.failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    )
                }
                .onFailure { error ->
                    _chatSync.value = ChatSyncState(
                        loading = false,
                        lastSyncAt = _chatSync.value.lastSyncAt,
                        error = error.message ?: "Could not sync chats",
                    )
                }
        }
    }

    fun loadModels(computerId: String, force: Boolean = false) {
        val current = _modelPicker.value
        if (!force && current.computerId == computerId && (current.loading || current.models.isNotEmpty())) return
        _modelPicker.value = ModelPickerState(computerId = computerId, loading = true)
        viewModelScope.launch {
            when (val result = repository.listModels(computerId)) {
                is RemoteResult.Success -> _modelPicker.value = ModelPickerState(
                    computerId = computerId,
                    models = result.value,
                    error = if (result.value.isEmpty()) "The server reported no available models" else null,
                )
                is RemoteResult.Error -> _modelPicker.value = ModelPickerState(
                    computerId = computerId,
                    error = result.message,
                )
            }
        }
    }

    fun selectModel(model: AgentModel?) {
        val conversationId = selectedChatId.value ?: return
        viewModelScope.launch {
            runCatching { repository.selectModel(conversationId, model) }
                .onFailure { _notices.emit(it.message ?: "Could not change model") }
        }
    }

    fun createChat(computerId: String, workspace: String, firstMessage: String) {
        viewModelScope.launch {
            runCatching {
                val id = repository.createConversation(computerId, workspace)
                openChat(id)
                firstMessage.takeIf(String::isNotBlank)?.let { repository.sendMessage(id, it) }
            }.onFailure { _notices.emit(it.message ?: "Could not create chat") }
        }
    }

    fun sendMessage(text: String, imageUris: List<String> = emptyList()) {
        val id = selectedChatId.value ?: return
        viewModelScope.launch {
            runCatching { repository.sendMessage(id, text, imageUris) }
                .onFailure { _notices.emit(it.message ?: "Could not queue message") }
        }
    }

    fun openRemoteFile(reference: String, allowOutsideWorkspace: Boolean = false) {
        val conversationId = selectedChatId.value ?: return
        remoteFileDownloadJob?.cancel()
        _remoteFileViewer.value = RemoteFileViewerState(loadingReference = reference)
        remoteFileDownloadJob = viewModelScope.launch {
            when (
                val result = repository.downloadRemoteFile(
                    conversationId,
                    reference,
                    allowOutsideWorkspace,
                ) { progress ->
                    if (_remoteFileViewer.value.loadingReference == reference) {
                        _remoteFileViewer.value = _remoteFileViewer.value.copy(
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                        )
                    }
                }
            ) {
                is RemoteResult.Success -> {
                    remoteFilePreviewCache["$conversationId:$reference:$allowOutsideWorkspace"] = result.value
                    _remoteFileViewer.value = RemoteFileViewerState(file = result.value)
                }
                is RemoteResult.Error -> _remoteFileViewer.value = RemoteFileViewerState(error = result.message)
            }
        }
    }

    suspend fun loadRemoteFile(
        conversationId: String,
        reference: String,
        allowOutsideWorkspace: Boolean,
        onProgress: (RemoteFileProgress) -> Unit = {},
    ): RemoteResult<DownloadedRemoteFile> {
        val cacheKey = "$conversationId:$reference:$allowOutsideWorkspace"
        remoteFilePreviewCache[cacheKey]?.takeIf { java.io.File(it.localPath).isFile }?.let {
            return RemoteResult.Success(it)
        }
        return repository.downloadRemoteFile(conversationId, reference, allowOutsideWorkspace, onProgress).also { result ->
            if (result is RemoteResult.Success) remoteFilePreviewCache[cacheKey] = result.value
        }
    }

    fun showRemoteFile(file: DownloadedRemoteFile) {
        remoteFileDownloadJob?.cancel()
        _remoteFileViewer.value = RemoteFileViewerState(file = file)
    }

    fun closeRemoteFile() {
        remoteFileDownloadJob?.cancel()
        remoteFileDownloadJob = null
        _remoteFileViewer.value = RemoteFileViewerState()
    }

    fun retryMessage(id: String) {
        viewModelScope.launch { repository.retryMessage(id) }
    }

    fun removeQueuedMessage(id: String) {
        viewModelScope.launch { repository.removeQueuedMessage(id) }
    }

    fun stop() {
        val id = selectedChatId.value ?: return
        viewModelScope.launch {
            if (repository.stop(id) is RemoteResult.Error) {
                _notices.emit("Could not stop the agent")
            }
        }
    }

    fun saveComputer(computer: RelayServer) {
        viewModelScope.launch {
            runCatching { repository.saveComputer(computer) }
                .onFailure { _notices.emit(it.message ?: "Could not save computer") }
        }
    }

    fun testComputer(id: String) {
        viewModelScope.launch {
            when (val result = repository.checkComputer(id)) {
                is RemoteResult.Success -> _notices.emit(
                    "Connected via ${result.value.routeLabel} · ${result.value.latencyMs} ms",
                )
                is RemoteResult.Error -> _notices.emit(result.message)
            }
        }
    }

    fun deleteComputer(id: String) {
        viewModelScope.launch {
            repository.deleteComputer(id).onFailure {
                _notices.emit("This computer is used by an existing chat")
            }
        }
    }

    fun saveTunnel(profile: SshTunnelProfile) {
        viewModelScope.launch {
            runCatching { repository.saveTunnel(profile) }
                .onFailure { _notices.emit(it.message ?: "Could not save SSH tunnel") }
        }
    }

    fun saveTunnelAndDiscover(profile: SshTunnelProfile) {
        _serverDiscovery.value = ServerDiscoveryState(
            tunnelId = profile.id,
            tunnelName = profile.name,
            loading = true,
        )
        viewModelScope.launch {
            runCatching { repository.saveTunnel(profile) }
                .onSuccess { runServerDiscovery(profile.id, profile.name) }
                .onFailure {
                    _serverDiscovery.value = _serverDiscovery.value.copy(
                        loading = false,
                        error = it.message ?: "Could not save SSH tunnel",
                    )
                }
        }
    }

    fun discoverServers(tunnelId: String) {
        val name = tunnels.value.firstOrNull { it.id == tunnelId }?.name ?: "SSH tunnel"
        _serverDiscovery.value = ServerDiscoveryState(tunnelId, name, loading = true)
        viewModelScope.launch { runServerDiscovery(tunnelId, name) }
    }

    private suspend fun runServerDiscovery(tunnelId: String, tunnelName: String) {
        when (val result = repository.discoverServers(tunnelId)) {
            is RemoteResult.Success -> _serverDiscovery.value = ServerDiscoveryState(
                tunnelId = tunnelId,
                tunnelName = tunnelName,
                results = result.value,
                addedCount = repository.saveDiscoveredServers(tunnelId, result.value),
            )
            is RemoteResult.Error -> _serverDiscovery.value = ServerDiscoveryState(
                tunnelId = tunnelId,
                tunnelName = tunnelName,
                error = result.message,
            )
        }
    }

    fun dismissServerDiscovery() {
        _serverDiscovery.value = ServerDiscoveryState()
    }

    fun addTunnelRoute(id: String, route: TunnelRouteRule) {
        viewModelScope.launch {
            runCatching { repository.addTunnelRoute(id, route) }
                .onSuccess { _notices.emit("SSH route added") }
                .onFailure { _notices.emit(it.message ?: "Could not add SSH route") }
        }
    }

    fun deleteTunnelRoute(id: String, route: TunnelRouteRule) {
        viewModelScope.launch {
            runCatching { repository.deleteTunnelRoute(id, route) }
                .onFailure { _notices.emit(it.message ?: "Could not delete SSH route") }
        }
    }

    fun testTunnel(id: String) {
        _tunnelTests.update { it + (id to TunnelTestState(id, loading = true)) }
        viewModelScope.launch {
            when (val result = repository.testTunnel(id)) {
                is RemoteResult.Success -> {
                    _tunnelTests.update { it + (id to TunnelTestState(id, loading = false, result = result.value)) }
                    _notices.emit(
                        buildString {
                            append(result.value.message)
                            result.value.target?.let { append(" · ").append(it) }
                            append(" · ").append(result.value.latencyMs).append(" ms")
                        },
                    )
                }
                is RemoteResult.Error -> {
                    _tunnelTests.update { it - id }
                    _notices.emit(result.message)
                }
            }
        }
    }

    fun testSshConnection(id: String) {
        _sshConnectionTests.update { it + (id to TunnelTestState(id, loading = true)) }
        viewModelScope.launch {
            when (val result = repository.testSshConnection(id)) {
                is RemoteResult.Success -> {
                    _sshConnectionTests.update {
                        it + (id to TunnelTestState(id, loading = false, result = result.value))
                    }
                    _notices.emit("${result.value.message} · ${result.value.latencyMs} ms")
                }
                is RemoteResult.Error -> {
                    _sshConnectionTests.update { it - id }
                    _notices.emit(result.message)
                }
            }
        }
    }

    fun deleteTunnel(id: String) {
        viewModelScope.launch { repository.deleteTunnel(id) }
    }

    fun saveIdentity(identity: SshIdentity) {
        viewModelScope.launch {
            runCatching { repository.saveIdentity(identity) }
                .onFailure { _notices.emit(it.message ?: "Could not save the identity") }
        }
    }

    fun deleteIdentity(id: String) {
        viewModelScope.launch {
            repository.deleteIdentity(id).onFailure {
                _notices.emit(it.message ?: "Could not delete the identity")
            }
        }
    }

    fun saveSshConnection(connection: SshConnection) {
        viewModelScope.launch {
            runCatching { repository.saveSshConnection(connection) }
                .onFailure { _notices.emit(it.message ?: "Could not save the SSH connection") }
        }
    }

    fun deleteSshConnection(id: String) {
        viewModelScope.launch {
            repository.deleteSshConnection(id).onFailure {
                _notices.emit(it.message ?: "Could not delete the SSH connection")
            }
        }
    }

    fun markComputerOffline(id: String) {
        computers.value.firstOrNull { it.id == id }?.let {
            saveComputer(it.copy(state = ConnectionState.OFFLINE))
        }
    }

    fun retryUpdateDownload() {
        updateManager.retryDownload()
    }

    fun installDownloadedUpdate() {
        updateManager.installDownloadedUpdate().onFailure { error ->
            _notices.tryEmit(error.message ?: "Could not open the Android installer")
        }
    }

    fun reportInstallPermissionDenied() {
        _notices.tryEmit("Installation permission is required to update PuppyCoder")
    }
}

data class ModelPickerState(
    val computerId: String? = null,
    val models: List<AgentModel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class ChatSyncState(
    val loading: Boolean = false,
    val lastSyncAt: Long? = null,
    val conversationsSeen: Int = 0,
    val error: String? = null,
)

data class HistoryPagingState(
    val conversationId: String? = null,
    val openedAt: Long = Long.MAX_VALUE,
    val initialLoading: Boolean = false,
    val loadingOlder: Boolean = false,
    val oldestLoadedAt: Long? = null,
    val nextCursor: String? = null,
    val allHistoryLoaded: Boolean = false,
    val olderPageVersion: Int = 0,
    val olderLoadError: String? = null,
) {
    val hasOlder: Boolean get() = nextCursor != null

    fun withPage(
        page: com.puppycoder.relay.data.HistorySyncPage,
        initial: Boolean,
        cachedOldestAt: Long? = null,
    ): HistoryPagingState {
        val oldest = listOfNotNull(oldestLoadedAt, page.oldestMessageAt, cachedOldestAt).minOrNull()
        return copy(
            initialLoading = false,
            loadingOlder = false,
            oldestLoadedAt = oldest,
            nextCursor = page.nextCursor,
            allHistoryLoaded = page.nextCursor == null,
            olderPageVersion = if (initial) olderPageVersion else olderPageVersion + 1,
            olderLoadError = null,
        )
    }
}

data class WriterClaimState(
    val conversationId: String? = null,
    val message: String? = null,
    val claiming: Boolean = false,
    val dialogVisible: Boolean = false,
)

internal fun visibleMessagesForHistory(
    messages: List<ChatMessage>,
    paging: HistoryPagingState,
): List<ChatMessage> = when {
    paging.conversationId == null || paging.initialLoading || paging.allHistoryLoaded -> messages
    paging.oldestLoadedAt == null -> messages.filter {
        it.remoteMessageId == null || it.createdAt >= paging.openedAt
    }
    else -> messages.filter {
        it.remoteMessageId == null || it.createdAt >= paging.openedAt || it.createdAt >= paging.oldestLoadedAt
    }
}

data class ServerDiscoveryState(
    val tunnelId: String? = null,
    val tunnelName: String = "",
    val loading: Boolean = false,
    val results: List<DiscoveredAgentServer> = emptyList(),
    val addedCount: Int = 0,
    val error: String? = null,
)

data class TunnelTestState(
    val tunnelId: String,
    val loading: Boolean = false,
    val result: SshTunnelTest? = null,
)

data class RemoteFileViewerState(
    val loadingReference: String? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
    val file: DownloadedRemoteFile? = null,
    val error: String? = null,
)

private const val LIVE_HISTORY_REFRESH_INTERVAL_MS = 500L
private const val LIVE_HISTORY_POLL_INTERVAL_MS = 2_000L
private const val BACKGROUND_HISTORY_POLL_INTERVAL_MS = 8_000L

private fun String.isActiveWriterConflict(): Boolean =
    contains("active writer", ignoreCase = true)
