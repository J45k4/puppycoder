package com.puppycoder.relay.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puppycoder.relay.R
import com.puppycoder.relay.data.ChatListItem
import com.puppycoder.relay.data.ChatMessage
import com.puppycoder.relay.data.AgentModel
import com.puppycoder.relay.data.ConnectionState
import com.puppycoder.relay.data.Conversation
import com.puppycoder.relay.data.ConversationState
import com.puppycoder.relay.data.DeliveryState
import com.puppycoder.relay.data.DiscoveredAgentServer
import com.puppycoder.relay.data.MessageRole
import com.puppycoder.relay.data.RelayServer
import com.puppycoder.relay.data.ServerKind
import com.puppycoder.relay.data.ServerRouteMode
import com.puppycoder.relay.data.SshTunnelConfig
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.ToolActivity
import com.puppycoder.relay.data.ToolActivityState
import com.puppycoder.relay.data.TunnelRouteRule
import com.puppycoder.relay.update.AppUpdateState
import com.puppycoder.relay.ui.theme.RelayAmber
import com.puppycoder.relay.ui.theme.RelayGreen
import com.puppycoder.relay.ui.theme.RelayRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private enum class MainScreen {
    CHATS,
    SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayApp(viewModel: PuppyCoderViewModel = viewModel()) {
    val chats by viewModel.chats.collectAsState()
    val computers by viewModel.computers.collectAsState()
    val tunnels by viewModel.tunnels.collectAsState()
    val conversation by viewModel.selectedConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val modelPicker by viewModel.modelPicker.collectAsState()
    val chatSync by viewModel.chatSync.collectAsState()
    val chatSearchQuery by viewModel.chatSearchQuery.collectAsState()
    val historyLoadingChatId by viewModel.historyLoadingChatId.collectAsState()
    val serverDiscovery by viewModel.serverDiscovery.collectAsState()
    val chatSortOrder by viewModel.chatSortOrder.collectAsState()
    val chatGroupMode by viewModel.chatGroupMode.collectAsState()
    val collapsedChatGroups by viewModel.collapsedChatGroups.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(MainScreen.CHATS) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }
    var showAddComputer by rememberSaveable { mutableStateOf(false) }
    var computerDraft by remember { mutableStateOf<RelayServer?>(null) }
    var showAddTunnel by rememberSaveable { mutableStateOf(false) }
    var addRouteTunnelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChatArrangement by rememberSaveable { mutableStateOf(false) }
    var showUpdatePrompt by rememberSaveable { mutableStateOf(false) }
    var promptedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (context.packageManager.canRequestPackageInstalls()) {
            viewModel.installDownloadedUpdate()
        } else {
            viewModel.reportInstallPermissionDenied()
        }
    }
    val openAddComputer: (RelayServer?) -> Unit = { draft ->
        computerDraft = draft
        showAddComputer = true
    }
    val startNewChat = {
        when (computers.size) {
            0 -> showAddTunnel = true
            1 -> computers.single().let { viewModel.createChat(it.id, it.workspace, "") }
            else -> showNewChat = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(appUpdate) {
        val ready = appUpdate as? AppUpdateState.Ready ?: return@LaunchedEffect
        if (promptedUpdateVersion != ready.release.version) {
            promptedUpdateVersion = ready.release.version
            showUpdatePrompt = true
        }
    }
    LaunchedEffect(screen, conversation?.id, computers.map { it.id to it.endpoint }) {
        if (screen == MainScreen.CHATS && conversation == null && computers.isNotEmpty()) {
            viewModel.syncChats()
        }
    }
    BackHandler(enabled = conversation != null) { viewModel.closeChat() }
    BackHandler(enabled = conversation == null && screen == MainScreen.CHATS && searchActive) {
        searchActive = false
        viewModel.setChatSearchQuery("")
    }
    BackHandler(enabled = conversation == null && screen == MainScreen.SETTINGS) {
        screen = MainScreen.CHATS
    }

    if (conversation != null) {
        val computer = computers.firstOrNull { it.id == conversation?.computerId }
        ConversationScreen(
            conversation = checkNotNull(conversation),
            computer = computer,
            messages = messages,
            tools = tools,
            onBack = viewModel::closeChat,
            onSend = viewModel::sendMessage,
            onRetry = viewModel::retryMessage,
            onRemove = viewModel::removeQueuedMessage,
            onStop = viewModel::stop,
            modelPicker = modelPicker,
            onLoadModels = viewModel::loadModels,
            onSelectModel = viewModel::selectModel,
            historyLoading = historyLoadingChatId == conversation?.id,
            snackbar = snackbar,
            appUpdate = appUpdate,
            onUpdateAction = {
                when (appUpdate) {
                    is AppUpdateState.Ready -> showUpdatePrompt = true
                    is AppUpdateState.Failed -> viewModel.retryUpdateDownload()
                    else -> Unit
                }
            },
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    AppHeader(
                        showingSettings = screen == MainScreen.SETTINGS,
                        onSortClick = { showChatArrangement = true },
                        onSearchClick = { searchActive = true },
                        onSettingsClick = {
                            searchActive = false
                            viewModel.setChatSearchQuery("")
                            screen = if (screen == MainScreen.SETTINGS) MainScreen.CHATS else MainScreen.SETTINGS
                        },
                    )
                    UpdateBanner(
                        state = appUpdate,
                        onAction = {
                            when (appUpdate) {
                                is AppUpdateState.Ready -> showUpdatePrompt = true
                                is AppUpdateState.Failed -> viewModel.retryUpdateDownload()
                                else -> Unit
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (screen == MainScreen.CHATS) startNewChat() else showAddTunnel = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (screen == MainScreen.CHATS) "New chat" else "Add computer") },
                )
            },
        ) { padding ->
            when (screen) {
                MainScreen.CHATS -> ChatsScreen(
                    chats = chats,
                    modifier = Modifier.padding(padding),
                    onOpen = viewModel::openChat,
                    onNewChat = startNewChat,
                    syncState = chatSync,
                    searchQuery = chatSearchQuery,
                    onSearchQueryChange = viewModel::setChatSearchQuery,
                    searchActive = searchActive,
                    sortOrder = chatSortOrder,
                    groupMode = chatGroupMode,
                    collapsedGroups = collapsedChatGroups,
                    onToggleGroup = viewModel::toggleChatGroup,
                    onCloseSearch = {
                        searchActive = false
                        viewModel.setChatSearchQuery("")
                    },
                )
                MainScreen.SETTINGS -> ComputersScreen(
                    computers = computers,
                    tunnels = tunnels,
                    modifier = Modifier.padding(padding),
                    onTest = viewModel::testComputer,
                    onEdit = { openAddComputer(it) },
                    onDelete = viewModel::deleteComputer,
                    onTestTunnel = viewModel::testTunnel,
                    onDiscoverTunnel = viewModel::discoverServers,
                    onDeleteTunnel = viewModel::deleteTunnel,
                    onAddRoute = { addRouteTunnelId = it },
                    onDeleteRoute = viewModel::deleteTunnelRoute,
                    onAddTunnel = { showAddTunnel = true },
                    onAdd = { showAddTunnel = true },
                )
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            computers = computers,
            onDismiss = { showNewChat = false },
            onCreate = { computer ->
                showNewChat = false
                viewModel.createChat(computer.id, computer.workspace, "")
            },
            onAddComputer = {
                showNewChat = false
                showAddTunnel = true
            },
        )
    }

    if (showAddComputer) {
        AddComputerSheet(
            tunnels = tunnels,
            initial = computerDraft,
            onDismiss = {
                showAddComputer = false
                computerDraft = null
            },
            onSave = {
                showAddComputer = false
                computerDraft = null
                viewModel.saveComputer(it)
            },
        )
    }

    if (showAddTunnel) {
        AddTunnelSheet(
            onDismiss = { showAddTunnel = false },
            onSave = {
                showAddTunnel = false
                viewModel.saveTunnelAndDiscover(it)
            },
        )
    }

    if (serverDiscovery.tunnelId != null) {
        DiscoverServersSheet(
            state = serverDiscovery,
            onDismiss = viewModel::dismissServerDiscovery,
            onRetry = { serverDiscovery.tunnelId?.let(viewModel::discoverServers) },
        )
    }

    addRouteTunnelId?.let { tunnelId ->
        val tunnel = tunnels.firstOrNull { it.id == tunnelId }
        if (tunnel != null) {
            AddRouteSheet(
                tunnelName = tunnel.name,
                onDismiss = { addRouteTunnelId = null },
                onSave = { route ->
                    addRouteTunnelId = null
                    viewModel.addTunnelRoute(tunnelId, route)
                },
            )
        } else {
            LaunchedEffect(tunnelId) { addRouteTunnelId = null }
        }
    }

    if (showChatArrangement) {
        ChatArrangementSheet(
            sortOrder = chatSortOrder,
            groupMode = chatGroupMode,
            onSortOrderChange = viewModel::setChatSortOrder,
            onGroupModeChange = viewModel::setChatGroupMode,
            onDismiss = { showChatArrangement = false },
        )
    }

    val readyUpdate = appUpdate as? AppUpdateState.Ready
    if (showUpdatePrompt && readyUpdate != null) {
        AlertDialog(
            onDismissRequest = { showUpdatePrompt = false },
            title = { Text("Install PuppyCoder ${readyUpdate.release.version}?") },
            text = {
                Text("The update finished downloading and its SHA-256 checksum was verified. Android will ask you to approve the installation.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdatePrompt = false
                        if (context.packageManager.canRequestPackageInstalls()) {
                            viewModel.installDownloadedUpdate()
                        } else {
                            unknownSourcesLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        }
                    },
                ) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatePrompt = false }) { Text("Later") }
            },
        )
    }
}

@Composable
private fun UpdateBanner(state: AppUpdateState, onAction: () -> Unit) {
    val content = when (state) {
        is AppUpdateState.Downloading -> Triple(
            "Update ${state.release.version} available",
            state.progressPercent?.let { "Downloading · $it%" } ?: "Starting download…",
            false,
        )
        is AppUpdateState.Ready -> Triple(
            "Update ${state.release.version} ready",
            "Tap to install",
            true,
        )
        is AppUpdateState.Failed -> Triple(
            "Update ${state.release.version} available",
            "Download failed · Tap to retry",
            true,
        )
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(enabled = content.third, onClick = onAction),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state is AppUpdateState.Downloading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(content.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(content.second, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AppHeader(
    showingSettings: Boolean,
    onSortClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.puppycoder_icon_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "PuppyCoder",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (!showingSettings) {
                IconButton(onClick = onSortClick) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort and group chats")
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search chats")
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = if (showingSettings) Icons.Default.Close else Icons.Default.Settings,
                    contentDescription = if (showingSettings) "Back to chats" else "Settings",
                )
            }
        }
    }
}

@Composable
private fun ChatsScreen(
    chats: List<ChatListItem>,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    syncState: ChatSyncState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchActive: Boolean,
    sortOrder: ChatSortOrder,
    groupMode: ChatGroupMode,
    collapsedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val sections = remember(chats, sortOrder, groupMode) {
        arrangeChats(chats, sortOrder, groupMode)
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (searchActive) {
            val searchFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .focusRequester(searchFocusRequester),
                placeholder = { Text("Search titles and messages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onCloseSearch()
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                },
                singleLine = true,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            syncState.error?.let { error ->
                item {
                    Text(
                        error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        color = RelayRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (chats.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 70.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (syncState.loading && searchQuery.isBlank()) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(14.dp))
                            Text("Fetching chats from your computers…")
                        } else {
                            Text(
                                if (searchQuery.isBlank()) "No chats yet" else "No matching chats",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isBlank()) {
                                    "Remote Codex threads and OpenCode sessions will appear here."
                                } else {
                                    "No titles or downloaded messages match “${searchQuery.trim()}”."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(Modifier.height(18.dp))
                                Button(onClick = onNewChat) { Text("New chat") }
                            }
                        }
                    }
                }
            }
            sections.forEach { section ->
                val title = section.title
                if (title != null) {
                    val expanded = section.key !in collapsedGroups
                    item(key = "group:${section.key}") {
                        ChatGroupHeader(
                            title = title,
                            subtitle = section.subtitle,
                            count = section.chats.size,
                            expanded = expanded,
                            onToggle = { onToggleGroup(section.key) },
                        )
                    }
                    if (expanded) {
                        items(section.chats, key = { it.conversation.id }) { item ->
                            ChatRow(item, onClick = { onOpen(item.conversation.id) })
                            HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
                        }
                    }
                } else {
                    items(section.chats, key = { it.conversation.id }) { item ->
                        ChatRow(item, onClick = { onOpen(item.conversation.id) })
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatGroupHeader(
    title: String,
    subtitle: String? = null,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Collapse group" else "Expand group",
                    onClick = onToggle,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "${if (expanded) "Collapse" else "Expand"} $title group"
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾" else "▸", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(
                        it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatArrangementSheet(
    sortOrder: ChatSortOrder,
    groupMode: ChatGroupMode,
    onSortOrderChange: (ChatSortOrder) -> Unit,
    onGroupModeChange: (ChatGroupMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            SheetTitle("Sort and group", onDismiss)
            Text("Sort chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChatSortOrder.entries.forEach { option ->
                ArrangementOption(
                    label = option.label,
                    selected = sortOrder == option,
                    onClick = { onSortOrderChange(option) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Group chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChatGroupMode.entries.forEach { option ->
                ArrangementOption(
                    label = option.label,
                    selected = groupMode == option,
                    onClick = { onGroupModeChange(option) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ArrangementOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ChatRow(item: ChatListItem, onClick: () -> Unit) {
    val conversation = item.conversation
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComputerAvatar(item.computer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(conversation.updatedAt.chatTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComputerBadge(item.computer.name)
                Spacer(Modifier.width(8.dp))
                val preview = when (conversation.state) {
                    ConversationState.SENDING -> "Sending…"
                    ConversationState.WORKING -> "Agent working…"
                    ConversationState.FAILED -> "Needs attention"
                    else -> conversation.lastMessagePreview.ifBlank { "New chat" }
                }
                Text(
                    preview,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (conversation.state == ConversationState.FAILED) RelayRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (conversation.state == ConversationState.SENDING || conversation.state == ConversationState.WORKING) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun ConversationScreen(
    conversation: Conversation,
    computer: RelayServer?,
    messages: List<ChatMessage>,
    tools: List<ToolActivity>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onStop: () -> Unit,
    modelPicker: ModelPickerState,
    onLoadModels: (String, Boolean) -> Unit,
    onSelectModel: (AgentModel?) -> Unit,
    historyLoading: Boolean,
    snackbar: SnackbarHostState,
    appUpdate: AppUpdateState,
    onUpdateAction: () -> Unit,
) {
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val computerName = computer?.name ?: "Computer"
    val busy = conversation.state == ConversationState.WORKING || conversation.state == ConversationState.SENDING
    var showModelPicker by rememberSaveable(conversation.id) { mutableStateOf(false) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.body) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(computer?.id) {
        computer?.id?.let { onLoadModels(it, false) }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack(onBack),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                Surface(shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        computer?.let { ComputerAvatar(it, size = 38) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (busy) "Agent working on $computerName…" else "$computerName · ${conversation.workspace}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (busy) RelayGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier
                                    .clickable(enabled = !busy && computer != null) { showModelPicker = true }
                                    .padding(top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Model: ${conversation.modelDisplayName ?: "Server default"}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else RelayGreen,
                                )
                                if (!busy) Text(" ▾", color = RelayGreen, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (busy) {
                            TextButton(onClick = onStop) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                }
                UpdateBanner(appUpdate, onUpdateAction)
            }
        },
        bottomBar = {
            MessageComposer(
                value = draft,
                onValueChange = { draft = it },
                onSend = {
                    val outgoing = draft.trim()
                    if (outgoing.isNotEmpty()) {
                        draft = ""
                        onSend(outgoing)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (historyLoading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                if (historyLoading) "Loading messages from $computerName…" else "New chat on $computerName",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (historyLoading) "You can start typing while the history loads."
                                else "Messages are saved here and sent in order, even after the app restarts.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(messages, key = ChatMessage::id) { message ->
                MessageBubble(
                    message = message,
                    computerName = computerName,
                    onRetry = { onRetry(message.id) },
                    onRemove = { onRemove(message.id) },
                )
                tools.filter { it.messageId == message.id }.forEach { ToolCard(it) }
            }
            val unboundTools = tools.filter { tool -> messages.none { it.id == tool.messageId } }
            items(unboundTools, key = ToolActivity::id) { ToolCard(it) }
            if (conversation.state == ConversationState.WORKING && messages.lastOrNull()?.deliveryState != DeliveryState.STREAMING) {
                item { TypingIndicator(computerName) }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            selectedModelId = conversation.modelId,
            selectedProviderId = conversation.modelProviderId,
            state = modelPicker,
            onDismiss = { showModelPicker = false },
            onRefresh = { computer?.id?.let { onLoadModels(it, true) } },
            onSelect = { model ->
                showModelPicker = false
                onSelectModel(model)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    selectedModelId: String?,
    selectedProviderId: String?,
    state: ModelPickerState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (AgentModel?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleModels = remember(state.models, query) {
        val needle = query.trim()
        if (needle.isEmpty()) state.models else state.models.filter { model ->
            model.displayName.contains(needle, ignoreCase = true) ||
                model.modelId.contains(needle, ignoreCase = true) ||
                model.providerName?.contains(needle, ignoreCase = true) == true
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SheetTitle("Choose model", onDismiss)
            Text(
                "This choice is saved for this chat and applies to the next message.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search models") },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
            ) {
                item {
                    ModelOptionRow(
                        title = "Server default",
                        subtitle = "Use the model configured on this computer",
                        selected = selectedModelId == null,
                        onClick = { onSelect(null) },
                    )
                }
                if (state.loading) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Loading available models…")
                        }
                    }
                } else if (state.error != null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(state.error, color = RelayRed)
                            TextButton(onClick = onRefresh) { Text("Try again") }
                        }
                    }
                } else if (visibleModels.isEmpty()) {
                    item { Text("No models match your search.", modifier = Modifier.padding(18.dp)) }
                } else {
                    items(visibleModels, key = AgentModel::key) { model ->
                        ModelOptionRow(
                            title = model.displayName,
                            subtitle = buildString {
                                model.providerName?.let { append(it).append(" · ") }
                                append(model.modelId)
                                if (model.isDefault) append(" · default")
                            },
                            selected = selectedModelId == model.modelId && selectedProviderId == model.providerId,
                            onClick = { onSelect(model) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(
                subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = RelayGreen)
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    computerName: String,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val outgoing = message.role == MessageRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (outgoing) .86f else .92f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (outgoing) 18.dp else 4.dp,
                bottomEnd = if (outgoing) 4.dp else 18.dp,
            ),
            color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            MarkdownMessage(
                text = message.body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        if (outgoing) {
            DeliveryIndicator(message, computerName, onRetry, onRemove)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.createdAt.chatTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.deliveryState == DeliveryState.STREAMING) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Agent is replying…", style = MaterialTheme.typography.labelSmall, color = RelayGreen)
                }
                if (message.deliveryState == DeliveryState.STOPPED) {
                    Spacer(Modifier.width(6.dp))
                    Text("Stopped", style = MaterialTheme.typography.labelSmall, color = RelayAmber)
                }
            }
        }
    }
}

@Composable
private fun DeliveryIndicator(
    message: ChatMessage,
    computerName: String,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val color = when (message.deliveryState) {
        DeliveryState.FAILED, DeliveryState.DELIVERY_UNCERTAIN -> RelayRed
        DeliveryState.QUEUED -> RelayAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (message.deliveryState) {
            DeliveryState.SENDING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.8.dp)
            DeliveryState.DELIVERED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = RelayGreen)
            DeliveryState.QUEUED -> Text("◷", color = color, fontSize = 12.sp)
            DeliveryState.FAILED, DeliveryState.DELIVERY_UNCERTAIN -> Text("!", color = color, fontWeight = FontWeight.Bold)
            else -> Unit
        }
        Spacer(Modifier.width(5.dp))
        Text(
            when (message.deliveryState) {
                DeliveryState.QUEUED -> "Queued for $computerName"
                DeliveryState.SENDING -> "Sending to $computerName…"
                DeliveryState.DELIVERED -> "Sent · ${message.createdAt.chatTime()}"
                DeliveryState.FAILED -> message.errorMessage ?: "Not sent"
                DeliveryState.DELIVERY_UNCERTAIN -> "Delivery uncertain"
                DeliveryState.STOPPED -> "Stopped"
                DeliveryState.STREAMING -> "Sending to $computerName…"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (message.deliveryState == DeliveryState.FAILED || message.deliveryState == DeliveryState.DELIVERY_UNCERTAIN) {
            TextButton(onClick = onRetry, modifier = Modifier.height(32.dp)) { Text("Retry") }
        }
        if (message.deliveryState == DeliveryState.QUEUED) {
            TextButton(onClick = onRemove, modifier = Modifier.height(32.dp)) { Text("Remove") }
        }
    }
}

@Composable
private fun TypingIndicator(computerName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("$computerName is working…", style = MaterialTheme.typography.bodySmall, color = RelayGreen)
    }
}

@Composable
private fun ToolCard(tool: ToolActivity) {
    val running = tool.state == ToolActivityState.RUNNING
    Surface(
        modifier = Modifier.fillMaxWidth(.92f).padding(start = 4.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(
                if (tool.state == ToolActivityState.FAILED) Icons.Default.Close else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (tool.state == ToolActivityState.FAILED) RelayRed else RelayGreen,
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(tool.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                if (tool.detail.isNotBlank()) Text(tool.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message the agent") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Spacer(Modifier.width(7.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.background(
                    if (value.isNotBlank()) RelayGreen else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComputersScreen(
    computers: List<RelayServer>,
    tunnels: List<SshTunnelProfile>,
    modifier: Modifier,
    onTest: (String) -> Unit,
    onEdit: (RelayServer) -> Unit,
    onDelete: (String) -> Unit,
    onTestTunnel: (String) -> Unit,
    onDiscoverTunnel: (String) -> Unit,
    onDeleteTunnel: (String) -> Unit,
    onAddRoute: (String) -> Unit,
    onDeleteRoute: (String, TunnelRouteRule) -> Unit,
    onAddTunnel: () -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("SSH machines and their agents", "Computers") }
        if (tunnels.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("No SSH computers configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onAdd) { Text("Add SSH computer") }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SSH computers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Agent services are discovered automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onAddTunnel) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add")
                }
            }
        }
        if (tunnels.isEmpty()) {
            item {
                Text(
                    "Add a computer using its SSH address and PuppyCoder will look for Codex and OpenCode.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(tunnels, key = SshTunnelProfile::id) { tunnel ->
            TunnelCard(
                tunnel = tunnel,
                onTest = { onTestTunnel(tunnel.id) },
                onDiscover = { onDiscoverTunnel(tunnel.id) },
                onDelete = { onDeleteTunnel(tunnel.id) },
                onAddRoute = { onAddRoute(tunnel.id) },
                onDeleteRoute = { route -> onDeleteRoute(tunnel.id, route) },
            )
        }
        item {
            Text(
                "Agent services",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (computers.isEmpty()) {
            item {
                Text(
                    "No Codex or OpenCode services discovered yet.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(computers, key = RelayServer::id) { computer ->
            ComputerCard(
                computer,
                onTest = { onTest(computer.id) },
                onEdit = { onEdit(computer) },
                onDelete = { onDelete(computer.id) },
            )
        }
    }
}

@Composable
private fun TunnelCard(
    tunnel: SshTunnelProfile,
    onTest: () -> Unit,
    onDiscover: () -> Unit,
    onDelete: () -> Unit,
    onAddRoute: () -> Unit,
    onDeleteRoute: (TunnelRouteRule) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("SSH", modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = RelayAmber, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(tunnel.name, fontWeight = FontWeight.SemiBold)
                    Text("${tunnel.ssh.username}@${tunnel.ssh.host}:${tunnel.ssh.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Priority ${tunnel.priority}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            Text("Routes", style = MaterialTheme.typography.labelLarge)
            tunnel.routes.forEach { route ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        route.displayName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { onDeleteRoute(route) },
                        enabled = tunnel.routes.size > 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Delete route ${route.displayName()}", modifier = Modifier.size(16.dp))
                    }
                }
            }
            TextButton(onClick = onAddRoute) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add route")
            }
            Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Find agent services")
            }
            Spacer(Modifier.height(10.dp))
            Row {
                OutlinedButton(onClick = onTest) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test tunnel")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete", color = RelayRed) }
            }
        }
    }
}

@Composable
private fun ComputerCard(
    computer: RelayServer,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComputerAvatar(computer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(computer.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text("${computer.kind.label} · ${computer.endpoint}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                ConnectionDot(computer.state)
            }
            Spacer(Modifier.height(12.dp))
            Text("Default workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(computer.workspace.ifBlank { "Not set" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                when (computer.routeMode) {
                    ServerRouteMode.DIRECT -> "Direct connection"
                    ServerRouteMode.AUTOMATIC -> "Best available route"
                    ServerRouteMode.TUNNEL -> "SSH tunnel"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = onTest, enabled = computer.state != ConnectionState.CHECKING) {
                    if (computer.state == ConnectionState.CHECKING) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test connection")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = RelayRed) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    computers: List<RelayServer>,
    onDismiss: () -> Unit,
    onCreate: (RelayServer) -> Unit,
    onAddComputer: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetTitle("Choose computer", onDismiss)
            Text("Tap a computer to open the new chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (computers.isEmpty()) {
                Text("No computers are configured yet.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAddComputer, modifier = Modifier.fillMaxWidth()) { Text("Add computer") }
                return@Column
            }
            computers.forEach { computer ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCreate(computer) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ComputerAvatar(computer, 38)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(computer.name, fontWeight = FontWeight.SemiBold)
                            Text(computer.kind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Open", color = RelayGreen, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverServersSheet(
    state: ServerDiscoveryState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SheetTitle("Find app servers", onDismiss)
            Text(
                "Checking standard and configured targets through ${state.tunnelName}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking for Codex and OpenCode…")
                }
            }
            state.error?.let { error ->
                Text(error, color = RelayRed)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
            if (!state.loading && state.error == null && state.results.isEmpty()) {
                Text("No Codex or OpenCode app server responded through this tunnel.")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Check that the server is running on port 4310 or 4096, or add its custom host and port as a tunnel route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text("Scan again") }
            }
            if (!state.loading && state.results.isNotEmpty()) {
                Text(
                    if (state.addedCount > 0) {
                        "${state.addedCount} agent service${if (state.addedCount == 1) "" else "s"} added automatically."
                    } else {
                        "Agent services are already configured for this computer."
                    },
                    color = RelayGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.results.forEach { discovered ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = if (discovered.kind == ServerKind.CODEX) RelayGreen else RelayAmber) {
                            Text(
                                discovered.kind.initials,
                                modifier = Modifier.padding(9.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(discovered.kind.label, fontWeight = FontWeight.SemiBold)
                            Text(discovered.endpoint, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${discovered.version} · ${discovered.latencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (discovered.requiresAuthentication) RelayAmber else RelayGreen,
                            )
                        }
                        Icon(Icons.Default.Check, contentDescription = "Configured", tint = RelayGreen)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddComputerSheet(
    tunnels: List<SshTunnelProfile>,
    initial: RelayServer? = null,
    onDismiss: () -> Unit,
    onSave: (RelayServer) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.kind ?: ServerKind.CODEX) }
    var endpoint by rememberSaveable(initial?.id) { mutableStateOf(initial?.endpoint.orEmpty()) }
    var workspace by rememberSaveable(initial?.id) { mutableStateOf(initial?.workspace.orEmpty()) }
    var username by rememberSaveable(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable(initial?.id) { mutableStateOf(initial?.password.orEmpty()) }
    var routeMode by rememberSaveable(initial?.id) { mutableStateOf(initial?.routeMode ?: ServerRouteMode.AUTOMATIC) }
    var tunnelProfileId by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.tunnelProfileId ?: tunnels.firstOrNull()?.id)
    }
    var allowDirectFallback by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.allowDirectFallback ?: true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            item {
                SheetTitle("Add computer", onDismiss)
                Text("Agent type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerKind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppTextField(name, { name = it }, "Computer name", "e.g. Office workstation")
                AppTextField(
                    endpoint,
                    { endpoint = it },
                    "Server address",
                    if (kind == ServerKind.CODEX) "ws://192.168.1.20:4310" else "http://192.168.1.20:4096",
                )
                AppTextField(workspace, { workspace = it }, "Default workspace", "/home/me/project")
                Spacer(Modifier.height(8.dp))
                Text("Route", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = routeMode == ServerRouteMode.AUTOMATIC, onClick = { routeMode = ServerRouteMode.AUTOMATIC }, label = { Text("Best route") })
                    FilterChip(selected = routeMode == ServerRouteMode.DIRECT, onClick = { routeMode = ServerRouteMode.DIRECT }, label = { Text("Direct") })
                    if (tunnels.isNotEmpty()) {
                        FilterChip(selected = routeMode == ServerRouteMode.TUNNEL, onClick = { routeMode = ServerRouteMode.TUNNEL }, label = { Text("Tunnel") })
                    }
                }
                if (routeMode == ServerRouteMode.AUTOMATIC) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow direct fallback", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowDirectFallback, onCheckedChange = { allowDirectFallback = it })
                    }
                }
                if (routeMode == ServerRouteMode.TUNNEL) {
                    Text("Specific tunnel", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tunnels.forEach { tunnel ->
                            FilterChip(
                                selected = tunnelProfileId == tunnel.id,
                                onClick = { tunnelProfileId = tunnel.id },
                                label = { Text(tunnel.name) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Basic authentication (optional)", style = MaterialTheme.typography.labelLarge)
                AppTextField(username, { username = it }, "Username", "")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("Credentials are encrypted with Android Keystore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSave(
                            RelayServer(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                kind = kind,
                                endpoint = endpoint.trim(),
                                workspace = workspace.trim(),
                                username = username,
                                password = password,
                                routeMode = routeMode,
                                tunnelProfileId = if (routeMode == ServerRouteMode.TUNNEL) tunnelProfileId else null,
                                allowDirectFallback = allowDirectFallback,
                                state = initial?.state ?: ConnectionState.OFFLINE,
                                version = initial?.version ?: "Not tested",
                                latencyMs = initial?.latencyMs ?: 0,
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && endpoint.isNotBlank() && workspace.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Save computer") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTunnelSheet(onDismiss: () -> Unit, onSave: (SshTunnelProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var sshPort by rememberSaveable { mutableStateOf("22") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var privateKey by rememberSaveable { mutableStateOf("") }
    var privateKeyPassphrase by rememberSaveable { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var targets by rememberSaveable { mutableStateOf("127.0.0.1:4310\n127.0.0.1:4096") }
    var priority by rememberSaveable { mutableStateOf("100") }
    val parsedRoutes = remember(targets) { targets.parseTunnelRoutes() }
    val valid = name.isNotBlank() && host.isNotBlank() && username.isNotBlank() &&
        (password.isNotBlank() || privateKey.isNotBlank()) &&
        sshPort.toIntOrNull() in 1..65535 && priority.toIntOrNull() != null && parsedRoutes.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        ) {
            item {
                SheetTitle("Add computer", onDismiss)
                Text(
                    "Connect over SSH and PuppyCoder will automatically find Codex and OpenCode services.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(name, { name = it }, "Computer name", "Home workstation")
                AppTextField(host, { host = it }, "SSH host", "gateway.example.com")
                AppTextField(sshPort, { sshPort = it.filter(Char::isDigit) }, "SSH port", "22")
                AppTextField(username, { username = it }, "SSH username", "puppy")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    label = { Text("SSH password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("Or use a private key", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    label = { Text("OpenSSH / PEM private key") },
                    minLines = 2,
                    maxLines = 5,
                )
                OutlinedTextField(
                    value = privateKeyPassphrase,
                    onValueChange = { privateKeyPassphrase = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    label = { Text("Private-key passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                AppTextField(fingerprint, { fingerprint = it }, "Host-key SHA-256 fingerprint", "SHA256:…")
                if (fingerprint.isBlank()) {
                    Text(
                        "Without a fingerprint, the first connection cannot detect an impersonated SSH host.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RelayAmber,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targets,
                    onValueChange = { targets = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agent server targets") },
                    supportingText = { Text("Defaults find local Codex and OpenCode; add custom host:port targets here") },
                    minLines = 2,
                    maxLines = 5,
                )
                AppTextField(priority, { priority = it.filter { char -> char.isDigit() || char == '-' } }, "Priority", "100")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSave(
                            SshTunnelProfile(
                                name = name.trim(),
                                ssh = SshTunnelConfig(
                                    host = host.trim(),
                                    port = checkNotNull(sshPort.toIntOrNull()),
                                    username = username.trim(),
                                    password = password,
                                    privateKey = privateKey,
                                    privateKeyPassphrase = privateKeyPassphrase,
                                    hostKeyFingerprint = fingerprint.trim(),
                                ),
                                routes = parsedRoutes,
                                priority = checkNotNull(priority.toIntOrNull()),
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Connect and discover") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRouteSheet(
    tunnelName: String,
    onDismiss: () -> Unit,
    onSave: (TunnelRouteRule) -> Unit,
) {
    var hostPattern by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    val parsedPort = port.toIntOrNull()
    val valid = hostPattern.isValidRoutePattern() && (port.isBlank() || parsedPort in 1..65535)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).imePadding(),
        ) {
            SheetTitle("Add route", onDismiss)
            Text(
                "Use $tunnelName when a computer's server address matches this target.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            AppTextField(hostPattern, { hostPattern = it.trim() }, "Host or pattern", "192.168.1.20, *.internal, or *")
            AppTextField(port, { port = it.filter(Char::isDigit) }, "Port (optional)", "4310")
            Text(
                "Leave the port empty to match every port on this host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(TunnelRouteRule(hostPattern.trim(), parsedPort))
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Add route") }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
    )
}

@Composable
private fun ComputerAvatar(computer: RelayServer, size: Int = 46) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = RoundedCornerShape((size / 3).dp),
        color = if (computer.kind == ServerKind.CODEX) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(computer.kind.initials, fontWeight = FontWeight.Black, color = if (computer.kind == ServerKind.CODEX) RelayGreen else RelayAmber)
        }
    }
}

@Composable
private fun ComputerBadge(name: String) {
    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.ONLINE -> RelayGreen
        ConnectionState.CHECKING -> RelayAmber
        ConnectionState.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun SheetTitle(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp, color = RelayGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(18.dp).size(30.dp), tint = RelayGreen)
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun Long.chatTime(): String {
    val instant = Instant.ofEpochMilli(this)
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone)
    val today = Instant.now().atZone(zone).toLocalDate()
    return if (date.toLocalDate() == today) {
        date.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun String.parseTunnelRoutes(): List<TunnelRouteRule> =
    split('\n', ',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { value ->
            val separator = value.lastIndexOf(':')
            val port = if (separator > 0) value.substring(separator + 1).toIntOrNull() else null
            val host = if (port != null) value.substring(0, separator) else value
            TunnelRouteRule(hostPattern = host, port = port)
        }

private fun String.isValidRoutePattern(): Boolean {
    val value = trim()
    return value.isNotEmpty() && value.none(Char::isWhitespace) && ':' !in value &&
        (value == "*" || !value.startsWith("*") || value.startsWith("*."))
}

private fun TunnelRouteRule.displayName(): String = hostPattern + (port?.let { ":$it" } ?: " · any port")
