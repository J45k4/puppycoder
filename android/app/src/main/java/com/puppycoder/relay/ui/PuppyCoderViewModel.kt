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
import com.puppycoder.relay.data.DiscoveredAgentServer
import com.puppycoder.relay.data.RelayServer
import com.puppycoder.relay.data.RemoteResult
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.ToolActivity
import com.puppycoder.relay.data.TunnelRouteRule
import com.puppycoder.relay.update.AppUpdateState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PuppyCoderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PuppyCoderApplication).repository
    private val updateManager = (application as PuppyCoderApplication).updateManager
    private val chatListPreferences = application.getSharedPreferences("chat_list_preferences", 0)
    private val selectedChatId = MutableStateFlow<String?>(null)
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val _modelPicker = MutableStateFlow(ModelPickerState())
    private val _chatSync = MutableStateFlow(ChatSyncState())
    private val _historyLoadingChatId = MutableStateFlow<String?>(null)
    private val _chatSearchQuery = MutableStateFlow("")
    private val _serverDiscovery = MutableStateFlow(ServerDiscoveryState())
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

    val notices = _notices.asSharedFlow()
    val appUpdate: StateFlow<AppUpdateState> = updateManager.state
    val modelPicker: StateFlow<ModelPickerState> = _modelPicker
    val chatSync: StateFlow<ChatSyncState> = _chatSync
    val historyLoadingChatId: StateFlow<String?> = _historyLoadingChatId
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery
    val serverDiscovery: StateFlow<ServerDiscoveryState> = _serverDiscovery
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
    val selectedConversation: StateFlow<Conversation?> = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.conversation(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<ChatMessage>> = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tools: StateFlow<List<ToolActivity>> = selectedChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.tools(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openChat(id: String) {
        selectedChatId.value = id
        _historyLoadingChatId.value = id
        viewModelScope.launch {
            when (val result = repository.syncConversationHistory(id)) {
                is RemoteResult.Success -> Unit
                is RemoteResult.Error -> _notices.emit("Could not load server history: ${result.message}")
            }
            if (_historyLoadingChatId.value == id) _historyLoadingChatId.value = null
        }
    }

    fun closeChat() {
        selectedChatId.value = null
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
                selectedChatId.value = id
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
        viewModelScope.launch {
            when (val result = repository.testTunnel(id)) {
                is RemoteResult.Success -> _notices.emit(
                    buildString {
                        append(result.value.message)
                        result.value.target?.let { append(" · ").append(it) }
                        append(" · ").append(result.value.latencyMs).append(" ms")
                    },
                )
                is RemoteResult.Error -> _notices.emit(result.message)
            }
        }
    }

    fun deleteTunnel(id: String) {
        viewModelScope.launch { repository.deleteTunnel(id) }
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

data class ServerDiscoveryState(
    val tunnelId: String? = null,
    val tunnelName: String = "",
    val loading: Boolean = false,
    val results: List<DiscoveredAgentServer> = emptyList(),
    val addedCount: Int = 0,
    val error: String? = null,
)
