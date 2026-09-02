package com.puppycoder.relay.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
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
import com.puppycoder.relay.data.DownloadedRemoteFile
import com.puppycoder.relay.data.MessageImage
import com.puppycoder.relay.data.MessageRole
import com.puppycoder.relay.data.RelayServer
import com.puppycoder.relay.data.RemoteResult
import com.puppycoder.relay.data.ServerKind
import com.puppycoder.relay.data.ServerRouteMode
import com.puppycoder.relay.data.SshIdentity
import com.puppycoder.relay.data.SshIdentityKind
import com.puppycoder.relay.data.SshConnection
import com.puppycoder.relay.data.SshTunnelConfig
import com.puppycoder.relay.data.SshTunnelHopTest
import com.puppycoder.relay.data.SshTunnelProfile
import com.puppycoder.relay.data.SshTunnelTest
import com.puppycoder.relay.data.ToolActivity
import com.puppycoder.relay.data.ToolActivityState
import com.puppycoder.relay.data.TunnelRouteRule
import com.puppycoder.relay.data.remoteFileReferenceFromActivity
import com.puppycoder.relay.update.AppUpdateState
import com.puppycoder.relay.ui.theme.RelayAmber
import com.puppycoder.relay.ui.theme.RelayGreen
import com.puppycoder.relay.ui.theme.RelayRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainScreen {
    CHATS,
    SETTINGS,
    IDENTITIES,
    CONNECTIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayApp(viewModel: PuppyCoderViewModel = viewModel()) {
    val chats by viewModel.chats.collectAsState()
    val computers by viewModel.computers.collectAsState()
    val tunnels by viewModel.tunnels.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val sshConnections by viewModel.sshConnections.collectAsState()
    val conversation by viewModel.selectedConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val tools by viewModel.tools.collectAsState()
    val modelPicker by viewModel.modelPicker.collectAsState()
    val chatSync by viewModel.chatSync.collectAsState()
    val chatSearchQuery by viewModel.chatSearchQuery.collectAsState()
    val historyPaging by viewModel.historyPaging.collectAsState()
    val writerClaim by viewModel.writerClaim.collectAsState()
    val serverDiscovery by viewModel.serverDiscovery.collectAsState()
    val tunnelTests by viewModel.tunnelTests.collectAsState()
    val sshConnectionTests by viewModel.sshConnectionTests.collectAsState()
    val chatSortOrder by viewModel.chatSortOrder.collectAsState()
    val chatGroupMode by viewModel.chatGroupMode.collectAsState()
    val collapsedChatGroups by viewModel.collapsedChatGroups.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val remoteFileViewer by viewModel.remoteFileViewer.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(MainScreen.CHATS) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }
    var showAddComputer by rememberSaveable { mutableStateOf(false) }
    var computerDraft by remember { mutableStateOf<RelayServer?>(null) }
    var showAddTunnel by rememberSaveable { mutableStateOf(false) }
    var tunnelDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var showIdentityEditor by rememberSaveable { mutableStateOf(false) }
    var identityDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSshConnectionEditor by rememberSaveable { mutableStateOf(false) }
    var sshConnectionDraftId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val openTunnelEditor: (SshTunnelProfile?) -> Unit = { draft ->
        tunnelDraftId = draft?.id
        showAddTunnel = true
    }
    val startNewChat = {
        when (computers.size) {
            0 -> openTunnelEditor(null)
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
    BackHandler(enabled = conversation == null && screen == MainScreen.IDENTITIES) {
        screen = MainScreen.SETTINGS
    }
    BackHandler(enabled = conversation == null && screen == MainScreen.CONNECTIONS) {
        screen = MainScreen.SETTINGS
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
            historyPaging = historyPaging,
            writerClaim = writerClaim,
            onLoadOlder = viewModel::loadOlderMessages,
            onShowWriterClaim = viewModel::showWriterClaimDialog,
            onDismissWriterClaim = viewModel::dismissWriterClaim,
            onForceClaimWriter = viewModel::forceClaimWriter,
            onScrollStateChanged = viewModel::setChatScrollInProgress,
            onViewportAtLatestChanged = viewModel::setChatViewportAtLatest,
            onOpenRemoteFile = { reference, allowOutsideWorkspace ->
                viewModel.openRemoteFile(reference, allowOutsideWorkspace)
            },
            onLoadRemoteFile = { reference, allowOutsideWorkspace ->
                viewModel.loadRemoteFile(
                    checkNotNull(conversation).id,
                    reference,
                    allowOutsideWorkspace,
                )
            },
            onShowRemoteFile = viewModel::showRemoteFile,
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
                        showingSettings = screen != MainScreen.CHATS,
                        settingsCloseDescription = if (screen in setOf(MainScreen.IDENTITIES, MainScreen.CONNECTIONS)) "Back to computers" else "Back to chats",
                        onSortClick = { showChatArrangement = true },
                        onSearchClick = { searchActive = true },
                        onSettingsClick = {
                            searchActive = false
                            viewModel.setChatSearchQuery("")
                            screen = when (screen) {
                                MainScreen.CHATS -> MainScreen.SETTINGS
                                MainScreen.IDENTITIES -> MainScreen.SETTINGS
                                MainScreen.CONNECTIONS -> MainScreen.SETTINGS
                                MainScreen.SETTINGS -> MainScreen.CHATS
                            }
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
                        when (screen) {
                            MainScreen.CHATS -> startNewChat()
                            MainScreen.IDENTITIES -> {
                                identityDraftId = null
                                showIdentityEditor = true
                            }
                            MainScreen.CONNECTIONS -> {
                                sshConnectionDraftId = null
                                showSshConnectionEditor = true
                            }
                            MainScreen.SETTINGS -> openTunnelEditor(null)
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(
                            when (screen) {
                                MainScreen.CHATS -> "New chat"
                                MainScreen.IDENTITIES -> "Add identity"
                                MainScreen.CONNECTIONS -> "Add SSH connection"
                                MainScreen.SETTINGS -> "Add computer"
                            },
                        )
                    },
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
                    identityCount = identities.size,
                    sshConnectionCount = sshConnections.size,
                    tunnelTests = tunnelTests,
                    modifier = Modifier.padding(padding),
                    onTest = viewModel::testComputer,
                    onEdit = { openAddComputer(it) },
                    onDelete = viewModel::deleteComputer,
                    onTestTunnel = viewModel::testTunnel,
                    onDiscoverTunnel = viewModel::discoverServers,
                    onEditTunnel = { openTunnelEditor(it) },
                    onDeleteTunnel = viewModel::deleteTunnel,
                    onAddRoute = { addRouteTunnelId = it },
                    onDeleteRoute = viewModel::deleteTunnelRoute,
                    onAddTunnel = { openTunnelEditor(null) },
                    onAdd = { openTunnelEditor(null) },
                    onOpenIdentities = { screen = MainScreen.IDENTITIES },
                    onOpenConnections = { screen = MainScreen.CONNECTIONS },
                )
                MainScreen.IDENTITIES -> IdentitiesScreen(
                    identities = identities,
                    modifier = Modifier.padding(padding),
                    onEdit = { identity ->
                        identityDraftId = identity.id
                        showIdentityEditor = true
                    },
                    onDelete = viewModel::deleteIdentity,
                    onAdd = {
                        identityDraftId = null
                        showIdentityEditor = true
                    },
                )
                MainScreen.CONNECTIONS -> SshConnectionsScreen(
                    connections = sshConnections,
                    tests = sshConnectionTests,
                    modifier = Modifier.padding(padding),
                    onTest = viewModel::testSshConnection,
                    onEdit = { connection ->
                        sshConnectionDraftId = connection.id
                        showSshConnectionEditor = true
                    },
                    onDelete = viewModel::deleteSshConnection,
                    onAdd = {
                        sshConnectionDraftId = null
                        showSshConnectionEditor = true
                    },
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
                openTunnelEditor(null)
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
        val editingTunnel = tunnelDraftId != null
        val initial = tunnelDraftId?.let { id -> tunnels.firstOrNull { it.id == id } }
        AddTunnelSheet(
            initial = initial,
            connections = sshConnections,
            onAddConnection = {
                sshConnectionDraftId = null
                showSshConnectionEditor = true
            },
            onDismiss = {
                showAddTunnel = false
                tunnelDraftId = null
            },
            onSave = {
                showAddTunnel = false
                tunnelDraftId = null
                if (editingTunnel) viewModel.saveTunnel(it) else viewModel.saveTunnelAndDiscover(it)
            },
        )
    }

    if (showIdentityEditor) {
        val initialIdentity = identityDraftId?.let { id -> identities.firstOrNull { it.id == id } }
        AddIdentitySheet(
            initial = initialIdentity,
            onDismiss = {
                showIdentityEditor = false
                identityDraftId = null
            },
            onSave = {
                showIdentityEditor = false
                identityDraftId = null
                viewModel.saveIdentity(it)
            },
        )
    }

    if (showSshConnectionEditor) {
        val initialConnection = sshConnectionDraftId?.let { id -> sshConnections.firstOrNull { it.id == id } }
        AddSshConnectionSheet(
            initial = initialConnection,
            identities = identities,
            onDismiss = {
                showSshConnectionEditor = false
                sshConnectionDraftId = null
            },
            onSave = {
                showSshConnectionEditor = false
                sshConnectionDraftId = null
                viewModel.saveSshConnection(it)
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

    if (
        remoteFileViewer.loadingReference != null ||
        remoteFileViewer.file != null ||
        remoteFileViewer.error != null
    ) {
        RemoteFileViewerDialog(remoteFileViewer, viewModel::closeRemoteFile)
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
    settingsCloseDescription: String = "Back to chats",
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
                    contentDescription = if (showingSettings) settingsCloseDescription else "Settings",
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
    onSend: (String, List<String>) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onStop: () -> Unit,
    modelPicker: ModelPickerState,
    onLoadModels: (String, Boolean) -> Unit,
    onSelectModel: (AgentModel?) -> Unit,
    historyPaging: HistoryPagingState,
    writerClaim: WriterClaimState,
    onLoadOlder: () -> Unit,
    onShowWriterClaim: () -> Unit,
    onDismissWriterClaim: () -> Unit,
    onForceClaimWriter: () -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    onViewportAtLatestChanged: (Boolean) -> Unit,
    onOpenRemoteFile: (String, Boolean) -> Unit,
    onLoadRemoteFile: suspend (String, Boolean) -> RemoteResult<DownloadedRemoteFile>,
    onShowRemoteFile: (DownloadedRemoteFile) -> Unit,
    snackbar: SnackbarHostState,
    appUpdate: AppUpdateState,
    onUpdateAction: () -> Unit,
) {
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var imageUris by remember(conversation.id) { mutableStateOf<List<Uri>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { selected ->
        imageUris = (imageUris + selected).distinct().take(4)
    }
    val listState = rememberLazyListState()
    val computerName = computer?.name ?: "Computer"
    val busy = conversation.state == ConversationState.WORKING || conversation.state == ConversationState.SENDING
    var showModelPicker by rememberSaveable(conversation.id) { mutableStateOf(false) }
    val arrangedMessages = remember(messages, tools) { arrangeMessageActivities(messages, tools) }
    var initialPositioned by remember(conversation.id) { mutableStateOf(false) }
    var observedOlderPageVersion by remember(conversation.id) {
        mutableStateOf(historyPaging.olderPageVersion)
    }
    val historyHeaderCount = if (historyPaging.hasOlder || historyPaging.loadingOlder) 1 else 0
    val emptyStateCount = if (arrangedMessages.groups.isEmpty() && arrangedMessages.unboundActivities.isEmpty()) 1 else 0
    val typingIndicatorCount = if (
        conversation.state == ConversationState.WORKING &&
        messages.lastOrNull()?.deliveryState != DeliveryState.STREAMING
    ) 1 else 0
    val listItemCount = historyHeaderCount + arrangedMessages.groups.size +
        (if (arrangedMessages.unboundActivities.isEmpty()) 0 else 1) + emptyStateCount + typingIndicatorCount
    val atHistoryStart by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 160
        }
    }
    val atHistoryEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount == 0 ||
                (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        }
    }

    if (writerClaim.conversationId == conversation.id && writerClaim.dialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissWriterClaim,
            title = { Text("Another client is writing") },
            text = {
                Text(
                    "Live updates are being handled by another client. Claiming the writer may interrupt it. " +
                        (writerClaim.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""),
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissWriterClaim, enabled = !writerClaim.claiming) {
                    Text("Keep watching")
                }
            },
            confirmButton = {
                TextButton(onClick = onForceClaimWriter, enabled = !writerClaim.claiming) {
                    Text(if (writerClaim.claiming) "Claiming..." else "Claim writer")
                }
            },
        )
    }

    LaunchedEffect(listState.isScrollInProgress) {
        onScrollStateChanged(listState.isScrollInProgress)
        if (!listState.isScrollInProgress) onViewportAtLatestChanged(atHistoryEnd)
    }
    DisposableEffect(conversation.id) {
        onDispose {
            onScrollStateChanged(false)
            onViewportAtLatestChanged(true)
        }
    }

    LaunchedEffect(
        historyPaging.initialLoading,
        listItemCount,
    ) {
        if (!historyPaging.initialLoading && !initialPositioned) {
            if (listItemCount > 0) listState.scrollToItem(listItemCount - 1)
            initialPositioned = true
        }
    }
    LaunchedEffect(
        messages.lastOrNull()?.id,
        messages.lastOrNull()?.body,
        tools.size,
        tools.lastOrNull()?.updatedAt,
        historyPaging.loadingOlder,
        historyPaging.olderPageVersion,
    ) {
        if (!initialPositioned || historyPaging.loadingOlder) return@LaunchedEffect
        if (observedOlderPageVersion != historyPaging.olderPageVersion) {
            observedOlderPageVersion = historyPaging.olderPageVersion
            return@LaunchedEffect
        }
        val layout = listState.layoutInfo
        val wasNearBottom = layout.totalItemsCount == 0 ||
            (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        if (listItemCount > 0 && wasNearBottom) listState.animateScrollToItem(listItemCount - 1)
    }
    LaunchedEffect(
        atHistoryStart,
        initialPositioned,
        historyPaging.hasOlder,
        historyPaging.loadingOlder,
        historyPaging.initialLoading,
    ) {
        if (
            atHistoryStart && initialPositioned && historyPaging.hasOlder && historyPaging.olderLoadError == null &&
            !historyPaging.loadingOlder && !historyPaging.initialLoading
        ) {
            onLoadOlder()
        }
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
                        if (writerClaim.conversationId == conversation.id) {
                            IconButton(onClick = onShowWriterClaim) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Read-only: another client is writing. Tap to claim the writer.",
                                    tint = RelayAmber,
                                )
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
                imageUris = imageUris,
                onValueChange = { draft = it },
                onAttach = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = { uri -> imageUris = imageUris - uri },
                onSend = {
                    val outgoing = draft.trim()
                    if (outgoing.isNotEmpty() || imageUris.isNotEmpty()) {
                        val outgoingImages = imageUris.map(Uri::toString)
                        draft = ""
                        imageUris = emptyList()
                        onSend(outgoing, outgoingImages)
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
            if (historyPaging.hasOlder || historyPaging.loadingOlder) {
                item(key = "older-history") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (historyPaging.loadingOlder) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading older messages…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            TextButton(onClick = onLoadOlder) {
                                Text(if (historyPaging.olderLoadError == null) "Load older messages" else "Retry older messages")
                            }
                        }
                    }
                }
            }
            if (arrangedMessages.groups.isEmpty() && arrangedMessages.unboundActivities.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (historyPaging.initialLoading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                if (historyPaging.initialLoading) "Loading messages from $computerName…" else "New chat on $computerName",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (historyPaging.initialLoading) "You can start typing while the history loads."
                                else "Messages are saved here and sent in order, even after the app restarts.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(arrangedMessages.groups, key = { it.message.id }) { group ->
                val message = group.message
                MessageBubble(
                    message = message,
                    activities = group.activities,
                    computerName = computerName,
                    onRetry = { onRetry(message.id) },
                    onRemove = { onRemove(message.id) },
                    onOpenRemoteFile = onOpenRemoteFile,
                    onLoadRemoteFile = onLoadRemoteFile,
                    onShowRemoteFile = onShowRemoteFile,
                )
            }
            if (arrangedMessages.unboundActivities.isNotEmpty()) {
                item(key = "unbound-thinking") {
                    ThinkingBlock(
                        activities = arrangedMessages.unboundActivities,
                        groupKey = "${conversation.id}-unbound",
                        onOpenRemoteFile = onOpenRemoteFile,
                        onLoadRemoteFile = onLoadRemoteFile,
                        onShowRemoteFile = onShowRemoteFile,
                    )
                }
            }
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
    activities: List<ToolActivity>,
    computerName: String,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenRemoteFile: (String, Boolean) -> Unit,
    onLoadRemoteFile: suspend (String, Boolean) -> RemoteResult<DownloadedRemoteFile>,
    onShowRemoteFile: (DownloadedRemoteFile) -> Unit,
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
            if (outgoing) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    if (message.images.isNotEmpty()) {
                        MessageImageRow(message.images)
                        if (message.body.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    if (message.body.isNotBlank()) {
                        MarkdownMessage(
                            text = message.body,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            onOpenRemoteFile = { onOpenRemoteFile(it, false) },
                        )
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    if (activities.isNotEmpty()) {
                        ThinkingBlock(
                            activities = activities,
                            groupKey = message.id,
                            onOpenRemoteFile = onOpenRemoteFile,
                            onLoadRemoteFile = onLoadRemoteFile,
                            onShowRemoteFile = onShowRemoteFile,
                        )
                        if (message.body.isNotBlank()) Spacer(Modifier.height(10.dp))
                    }
                    if (message.body.isNotBlank()) {
                        MarkdownMessage(
                            text = message.body,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            onOpenRemoteFile = { onOpenRemoteFile(it, false) },
                        )
                    }
                }
            }
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
private fun ThinkingBlock(
    activities: List<ToolActivity>,
    groupKey: String,
    modifier: Modifier = Modifier,
    onOpenRemoteFile: (String, Boolean) -> Unit,
    onLoadRemoteFile: suspend (String, Boolean) -> RemoteResult<DownloadedRemoteFile>,
    onShowRemoteFile: (DownloadedRemoteFile) -> Unit,
) {
    val active = activities.any { it.state == ToolActivityState.RUNNING }
    val failed = activities.any { it.state == ToolActivityState.FAILED }
    val containedScrollConnection = remember(groupKey) {
        object : NestedScrollConnection {
            private var childConsumedInGesture = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y != 0f) childConsumedInGesture = true
                return if (childConsumedInGesture) Offset(0f, available.y) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val containRemainder = childConsumedInGesture
                childConsumedInGesture = false
                return if (containRemainder) available else Velocity.Zero
            }
        }
    }
    var expanded by rememberSaveable(groupKey) { mutableStateOf(active) }
    LaunchedEffect(active) { expanded = active }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (failed) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (failed) RelayRed else RelayGreen,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active) "Thinking…" else "Thinking",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (activities.size == 1) "1 activity" else "${activities.size} activities",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse thinking" else "Expand thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider()
                LazyColumn(
                    Modifier
                        .heightIn(max = 360.dp)
                        .nestedScroll(containedScrollConnection)
                        .padding(horizontal = 11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(activities, key = { _, activity -> activity.id }) { index, activity ->
                        ThinkingActivityRow(
                            activity = activity,
                            onOpenRemoteFile = onOpenRemoteFile,
                            onLoadRemoteFile = onLoadRemoteFile,
                            onShowRemoteFile = onShowRemoteFile,
                        )
                        if (index != activities.lastIndex) HorizontalDivider(Modifier.padding(start = 26.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingActivityRow(
    activity: ToolActivity,
    onOpenRemoteFile: (String, Boolean) -> Unit,
    onLoadRemoteFile: suspend (String, Boolean) -> RemoteResult<DownloadedRemoteFile>,
    onShowRemoteFile: (DownloadedRemoteFile) -> Unit,
) {
    val running = activity.state == ToolActivityState.RUNNING
    val remoteReference = remember(activity.title, activity.detail) {
        remoteFileReferenceFromActivity(activity.title, activity.detail)
    }
    val downloaded by produceState<RemoteResult<DownloadedRemoteFile>?>(
        initialValue = null,
        activity.id,
        activity.updatedAt,
        remoteReference,
    ) {
        if (remoteReference != null && !running) {
            value = onLoadRemoteFile(remoteReference, true)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        if (running) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
        } else {
            Icon(
                if (activity.state == ToolActivityState.FAILED) Icons.Default.Close else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (activity.state == ToolActivityState.FAILED) RelayRed else RelayGreen,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            if (activity.detail.isNotBlank()) {
                Text(
                    activity.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val result = downloaded) {
                null -> if (remoteReference != null && !running) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("Loading file…", style = MaterialTheme.typography.labelSmall)
                    }
                }
                is RemoteResult.Success -> {
                    if (result.value.isImage) {
                        RemoteImagePreview(result.value, onShowRemoteFile)
                    } else {
                        TextButton(onClick = { onShowRemoteFile(result.value) }) { Text("Open file") }
                    }
                }
                is RemoteResult.Error -> if (remoteReference != null) {
                    TextButton(onClick = { onOpenRemoteFile(remoteReference, true) }) { Text("Open file") }
                }
            }
        }
    }
}

@Composable
private fun RemoteImagePreview(
    file: DownloadedRemoteFile,
    onOpen: (DownloadedRemoteFile) -> Unit,
) {
    val preview by produceState<ImageBitmap?>(null, file.localPath) {
        value = withContext(Dispatchers.IO) { decodeFilePreview(file.localPath) }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 240.dp)
            .padding(top = 7.dp)
            .clickable { onOpen(file) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (preview != null) {
            Image(
                bitmap = checkNotNull(preview),
                contentDescription = "Open ${file.displayName}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Open ${file.displayName}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RemoteFileViewerDialog(
    state: RemoteFileViewerState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val file = state.file
    var openError by remember(file?.localPath) { mutableStateOf<String?>(null) }
    val image by produceState<ImageBitmap?>(null, file?.localPath, file?.isImage) {
        value = if (file?.isImage == true) {
            withContext(Dispatchers.IO) { decodeFilePreview(file.localPath) }
        } else {
            null
        }
    }
    val textPreview by produceState<TextFilePreview?>(null, file?.localPath, file?.isText) {
        value = if (file?.isText == true) {
            withContext(Dispatchers.IO) { readTextFilePreview(file.localPath) }
        } else {
            null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    state.loadingReference != null -> "Downloading file"
                    file != null -> file.displayName
                    else -> "Could not open file"
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when {
                    state.loadingReference != null -> {
                        val total = state.totalBytes
                        val fraction = total
                            ?.takeIf { it > 0 }
                            ?.let { (state.bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            buildString {
                                append(formatFileSize(state.bytesDownloaded))
                                if (total != null) {
                                    append(" of ")
                                    append(formatFileSize(total))
                                    append(" · ")
                                    append(((state.bytesDownloaded * 100) / total.coerceAtLeast(1)).coerceIn(0, 100))
                                    append('%')
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.loadingReference,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    state.error != null -> Text(state.error)
                    file != null && file.isImage -> {
                        if (image != null) {
                            Image(
                                bitmap = checkNotNull(image),
                                contentDescription = file.displayName,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text("This image format cannot be previewed here. You can open it in another app.")
                        }
                    }
                    file != null && file.isText -> {
                        val preview = textPreview
                        if (preview == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            SelectionContainer {
                                Text(
                                    preview.content + if (preview.truncated) "\n\n… Preview truncated" else "",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 520.dp)
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState()),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    file != null -> Text(
                        "${file.mimeType}\n${formatFileSize(file.sizeBytes)}\n\nUse another app to view this file.",
                    )
                }
                if (file != null) {
                    Text(
                        file.remotePath,
                        modifier = Modifier.padding(top = 10.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                openError?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp), color = RelayRed)
                }
            }
        },
        confirmButton = {
            if (file != null) {
                TextButton(
                    onClick = {
                        openError = launchDownloadedFile(context, file).fold(
                            onSuccess = { null },
                            onFailure = { it.message ?: "No app can open this file" },
                        )
                    },
                ) { Text("Open in another app") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private data class TextFilePreview(val content: String, val truncated: Boolean)

private fun readTextFilePreview(path: String): TextFilePreview {
    val output = StringBuilder()
    var truncated = false
    File(path).bufferedReader().use { reader ->
        val buffer = CharArray(8 * 1024)
        while (output.length < MAX_TEXT_PREVIEW_CHARS) {
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_TEXT_PREVIEW_CHARS - output.length))
            if (count < 0) return@use
            output.append(buffer, 0, count)
        }
        truncated = reader.read() >= 0
    }
    return TextFilePreview(output.toString(), truncated)
}

private fun launchDownloadedFile(context: Context, file: DownloadedRemoteFile): Result<Unit> = runCatching {
    val localFile = File(file.localPath)
    require(localFile.isFile) { "The cached file is no longer available" }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", localFile)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Open ${file.displayName}"))
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
private fun MessageImageRow(images: List<MessageImage>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        images.forEach { image ->
            val preview by produceState<ImageBitmap?>(null, image.filePath) {
                value = withContext(Dispatchers.IO) { decodeFilePreview(image.filePath) }
            }
            Surface(
                modifier = Modifier
                    .width(if (images.size == 1) 180.dp else 116.dp)
                    .height(if (images.size == 1) 130.dp else 96.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                if (preview != null) {
                    Image(
                        bitmap = checkNotNull(preview),
                        contentDescription = image.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Image", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftImagePreview(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeUriPreview(context, uri) }
    }
    Box(Modifier.size(76.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (preview != null) {
                Image(
                    bitmap = checkNotNull(preview),
                    contentDescription = "Attached image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove image", modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    imageUris: List<Uri>,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = value.isNotBlank() || imageUris.isNotEmpty()
    Surface(shadowElevation = 4.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (imageUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    imageUris.forEach { uri -> DraftImagePreview(uri) { onRemoveImage(uri) } }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = imageUris.size < 4) {
                    Icon(Icons.Default.Add, contentDescription = "Attach images")
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message the agent") },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                )
                Spacer(Modifier.width(7.dp))
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.background(
                        if (canSend) RelayGreen else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun decodeUriPreview(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val options = BitmapFactory.Options().apply { inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight) }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
    }
}.getOrNull()

private fun decodeFilePreview(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight) },
    )?.asImageBitmap()
}.getOrNull()

private fun previewSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > 512) sample *= 2
    return sample
}

@Composable
private fun ComputersScreen(
    computers: List<RelayServer>,
    tunnels: List<SshTunnelProfile>,
    identityCount: Int,
    sshConnectionCount: Int,
    tunnelTests: Map<String, TunnelTestState>,
    modifier: Modifier,
    onTest: (String) -> Unit,
    onEdit: (RelayServer) -> Unit,
    onDelete: (String) -> Unit,
    onTestTunnel: (String) -> Unit,
    onDiscoverTunnel: (String) -> Unit,
    onEditTunnel: (SshTunnelProfile) -> Unit,
    onDeleteTunnel: (String) -> Unit,
    onAddRoute: (String) -> Unit,
    onDeleteRoute: (String, TunnelRouteRule) -> Unit,
    onAddTunnel: () -> Unit,
    onAdd: () -> Unit,
    onOpenIdentities: () -> Unit,
    onOpenConnections: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("Credentials, connections, and agent routes", "Computers") }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsPanel(
                    title = "SSH connections",
                    detail = "$sshConnectionCount saved address${if (sshConnectionCount == 1) "" else "es"} and host keys",
                    onClick = onOpenConnections,
                )
                SettingsPanel(
                    title = "Identities",
                    detail = "$identityCount saved password${if (identityCount == 1) "" else "s"} or private keys",
                    onClick = onOpenIdentities,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Agent routes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose saved SSH connections to build each route", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onAddTunnel) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add route")
                }
            }
        }
        if (tunnels.isEmpty()) {
            item {
                Text(
                    "Add an agent route, then select the SSH connections it should use.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(tunnels, key = SshTunnelProfile::id) { tunnel ->
            TunnelCard(
                tunnel = tunnel,
                testState = tunnelTests[tunnel.id],
                onTest = { onTestTunnel(tunnel.id) },
                onDiscover = { onDiscoverTunnel(tunnel.id) },
                onEdit = { onEditTunnel(tunnel) },
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
private fun SettingsPanel(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = "Open $title", modifier = Modifier.size(18.dp), tint = RelayGreen)
        }
    }
}

@Composable
private fun TunnelCard(
    tunnel: SshTunnelProfile,
    testState: TunnelTestState?,
    onTest: () -> Unit,
    onDiscover: () -> Unit,
    onEdit: () -> Unit,
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
                    Text(
                        tunnel.chainSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tunnel.hops.size > 1) {
                    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "${tunnel.hops.size} hops",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
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
                OutlinedButton(onClick = onTest, enabled = testState?.loading != true) {
                    if (testState?.loading == true) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test tunnel")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics { contentDescription = "Edit SSH computer ${tunnel.name}" },
                ) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = RelayRed) }
            }
            testState?.result?.let { result -> TunnelHopResults(result) }
        }
    }
}

@Composable
private fun TunnelHopResults(result: SshTunnelTest) {
    if (result.hops.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text("Last test", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    result.hops.forEach { hop ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (hop.ok) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (hop.ok) RelayGreen else RelayRed,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Hop ${hop.hopIndex + 1} · ${hop.label}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (hop.ok) "${hop.latencyMs} ms" else "failed",
                style = MaterialTheme.typography.labelSmall,
                color = if (hop.ok) MaterialTheme.colorScheme.onSurfaceVariant else RelayRed,
            )
        }
        hop.error?.let { error ->
            Text(
                error,
                modifier = Modifier.padding(start = 22.dp),
                style = MaterialTheme.typography.bodySmall,
                color = RelayRed,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SshTunnelProfile.chainSummary(): String = if (hops.size == 1) {
    "${ssh.username}@${ssh.host}:${ssh.port}"
} else {
    hops.joinToString(" → ") { hop ->
        val who = hop.identityName.ifBlank { hop.username.ifBlank { "?" } }
        "$who@${hop.host}"
    }
}

@Composable
private fun IdentitiesScreen(
    identities: List<SshIdentity>,
    modifier: Modifier,
    onEdit: (SshIdentity) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("Reusable SSH credentials", "Identities") }
        if (identities.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("No identities saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "An identity is a username with a password or private key. Hops can reuse it instead of repeating credentials.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAdd) { Text("Add identity") }
                }
            }
        }
        items(identities, key = SshIdentity::id) { identity ->
            IdentityCard(
                identity = identity,
                onEdit = { onEdit(identity) },
                onDelete = { onDelete(identity.id) },
            )
        }
    }
}

@Composable
private fun IdentityCard(
    identity: SshIdentity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        identity.kind.initials,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        color = RelayAmber,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(identity.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${identity.kind.label} · ${identity.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = RelayRed) }
            }
        }
    }
}

@Composable
private fun SshConnectionsScreen(
    connections: List<SshConnection>,
    tests: Map<String, TunnelTestState>,
    modifier: Modifier,
    onTest: (String) -> Unit,
    onEdit: (SshConnection) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("Reusable jump hosts", "SSH connections") }
        if (connections.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("No SSH connections saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Save an address and identity once, then select it in any SSH route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAdd) { Text("Add SSH connection") }
                }
            }
        }
        items(connections, key = SshConnection::id) { connection ->
            val testState = tests[connection.id]
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
                            Text(connection.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${connection.ssh.host}:${connection.ssh.port} · " +
                                    connection.ssh.identityName.ifBlank { connection.ssh.username },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    testState?.result?.let { result ->
                        Text(
                            "Connected · ${result.latencyMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelayGreen,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onTest(connection.id) },
                            enabled = testState?.loading != true,
                        ) {
                            if (testState?.loading == true) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing")
                            } else {
                                Text("Test connection")
                            }
                        }
                        TextButton(onClick = { onEdit(connection) }) { Text("Edit") }
                        TextButton(onClick = { onDelete(connection.id) }) { Text("Delete", color = RelayRed) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIdentitySheet(
    initial: SshIdentity? = null,
    onDismiss: () -> Unit,
    onSave: (SshIdentity) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.kind ?: SshIdentityKind.PASSWORD) }
    var username by rememberSaveable(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable(initial?.id) { mutableStateOf(initial?.password.orEmpty()) }
    var privateKey by rememberSaveable(initial?.id) { mutableStateOf(initial?.privateKey.orEmpty()) }
    var privateKeyPassphrase by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.privateKeyPassphrase.orEmpty())
    }
    val credentialValid = when (kind) {
        SshIdentityKind.PASSWORD -> password.isNotBlank()
        SshIdentityKind.PRIVATE_KEY -> privateKey.isNotBlank()
    }
    val valid = name.isNotBlank() && username.isNotBlank() && credentialValid

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .semantics { contentDescription = "Identity editor fields" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        ) {
            item {
                SheetTitle(if (initial == null) "Add identity" else "Edit identity", onDismiss)
                Text(
                    "Saved credentials that SSH hops can share. Editing here updates every hop using this identity.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(name, { name = it }, "Identity name", "Work laptop key")
                Text("Sign-in method", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SshIdentityKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                AppTextField(username, { username = it }, "SSH username", "puppy")
                if (kind == SshIdentityKind.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        label = { Text("SSH password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                } else {
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
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSave(
                            SshIdentity(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                kind = kind,
                                username = username.trim(),
                                password = if (kind == SshIdentityKind.PASSWORD) password else "",
                                privateKey = if (kind == SshIdentityKind.PRIVATE_KEY) privateKey else "",
                                privateKeyPassphrase = privateKeyPassphrase,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(if (initial == null) "Save identity" else "Save changes") }
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
                SheetTitle(if (initial == null) "Add agent service" else "Edit agent service", onDismiss)
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
                ) { Text(if (initial == null) "Add agent service" else "Save changes") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTunnelSheet(
    initial: SshTunnelProfile? = null,
    connections: List<SshConnection> = emptyList(),
    onAddConnection: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (SshTunnelProfile) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var hops by rememberSaveable(initial?.id, stateSaver = hopListSaver) {
        mutableStateOf(initial?.hops?.map(HopDraft::fromConfig) ?: listOf(HopDraft()))
    }
    var expandedHop by rememberSaveable(initial?.id) { mutableStateOf<Int?>(0) }
    var choosingHop by rememberSaveable(initial?.id) { mutableStateOf<Int?>(null) }
    var targets by rememberSaveable(initial?.id) {
        mutableStateOf(
            initial?.routes?.joinToString("\n") { it.displayNameForEditor() }
                ?: "127.0.0.1:4310\n127.0.0.1:4096",
        )
    }
    var priority by rememberSaveable(initial?.id) { mutableStateOf((initial?.priority ?: 100).toString()) }
    val parsedRoutes = remember(targets) { targets.parseTunnelRoutes() }
    val knownConnectionIds = remember(connections) { connections.map(SshConnection::id).toSet() }
    val hopsValid = hops.all { it.isValid(knownConnectionIds) }
    val valid = name.isNotBlank() && hopsValid && hops.isNotEmpty() &&
        priority.toIntOrNull() != null && parsedRoutes.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .semantics { contentDescription = "SSH computer editor fields" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        ) {
            item {
                SheetTitle(if (initial == null) "Add computer" else "Edit computer", onDismiss)
                Text(
                    if (initial == null) {
                        "Connect over SSH and PuppyCoder will automatically find Codex and OpenCode services."
                    } else {
                        "Update this computer's SSH connection, jump hosts, routes, and priority."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(name, { name = it }, "Computer name", "Home workstation")
                Spacer(Modifier.height(12.dp))
            }
            item(key = "chain") {
                TunnelChainDiagram(
                    hops = hops,
                    selectedIndex = expandedHop,
                    onHopSelected = { index -> expandedHop = if (expandedHop == index) null else index },
                )
                Spacer(Modifier.height(6.dp))
            }
            itemsIndexed(hops, key = { index, _ -> "hop-$index" }) { index, hop ->
                HopEditorCard(
                    hop = hop,
                    index = index,
                    total = hops.size,
                    connections = connections,
                    expanded = expandedHop == index,
                    onToggle = { expandedHop = if (expandedHop == index) null else index },
                    onChange = { updated ->
                        hops = hops.toMutableList().also { it[index] = updated }
                    },
                    onChooseConnection = { choosingHop = index },
                    onMove = { target ->
                        if (target in hops.indices && target != index) {
                            hops = hops.toMutableList().also { it.add(target, it.removeAt(index)) }
                            expandedHop = target
                        }
                    },
                    onRemove = {
                        if (hops.size > 1) {
                            hops = hops.filterIndexed { position, _ -> position != index }
                            expandedHop = expandedHop?.coerceIn(0, hops.lastIndex)
                        }
                    },
                )
            }
            item(key = "hop-actions") {
                TextButton(
                    onClick = {
                        val targetIndex = hops.lastIndex
                        hops = hops.toMutableList().also { it.add(targetIndex, HopDraft()) }
                        expandedHop = targetIndex
                    },
                    modifier = Modifier.semantics { contentDescription = "Add jump host" },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add jump host")
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
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                hops = hops.map(HopDraft::toConfig),
                                routes = parsedRoutes,
                                priority = checkNotNull(priority.toIntOrNull()),
                                enabled = initial?.enabled ?: true,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(if (initial == null) "Connect and discover" else "Save changes") }
            }
        }
    }
    choosingHop?.let { index ->
        ChooseSshConnectionSheet(
            connections = connections,
            onDismiss = { choosingHop = null },
            onChoose = { connection ->
                hops = hops.toMutableList().also { list -> list[index] = HopDraft.fromConnection(connection) }
                choosingHop = null
            },
            onAddConnection = {
                choosingHop = null
                onAddConnection()
            },
        )
    }
}

private data class HopDraft(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
    val hostKeyFingerprint: String = "",
    val identityId: String? = null,
    val connectionId: String? = null,
    val connectionName: String = "",
) {
    fun isValid(knownConnectionIds: Set<String> = emptySet()): Boolean = connectionId in knownConnectionIds

    fun summary(identityName: String? = null): String = when {
        host.isBlank() -> "Not configured yet"
        connectionName.isNotBlank() -> connectionName
        identityName != null -> "$identityName@${host}:${port.ifBlank { "22" }}"
        else -> "${username.ifBlank { "user" }}@${host}:${port.ifBlank { "22" }}"
    }

    fun toConfig() = SshTunnelConfig(
        host = host.trim(),
        port = checkNotNull(port.toIntOrNull()),
        username = username.trim(),
        password = password,
        privateKey = privateKey,
        privateKeyPassphrase = privateKeyPassphrase,
        hostKeyFingerprint = hostKeyFingerprint.trim(),
        identityId = identityId,
        connectionId = connectionId,
        connectionName = connectionName,
    )

    fun flatten(): List<String> = listOf(
        host,
        port,
        username,
        password,
        privateKey,
        privateKeyPassphrase,
        hostKeyFingerprint,
        identityId.orEmpty(),
        connectionId.orEmpty(),
        connectionName,
    )

    companion object {
        const val FIELD_COUNT = 10

        fun fromConfig(config: SshTunnelConfig) = HopDraft(
            host = config.host,
            port = config.port.toString(),
            username = config.username,
            password = config.password,
            privateKey = config.privateKey,
            privateKeyPassphrase = config.privateKeyPassphrase,
            hostKeyFingerprint = config.hostKeyFingerprint,
            identityId = config.identityId,
            connectionId = config.connectionId,
            connectionName = config.connectionName,
        )

        fun fromConnection(connection: SshConnection) = fromConfig(connection.ssh)

        fun restore(fields: List<String>) = HopDraft(
            host = fields.getOrElse(0) { "" },
            port = fields.getOrElse(1) { "22" },
            username = fields.getOrElse(2) { "" },
            password = fields.getOrElse(3) { "" },
            privateKey = fields.getOrElse(4) { "" },
            privateKeyPassphrase = fields.getOrElse(5) { "" },
            hostKeyFingerprint = fields.getOrElse(6) { "" },
            identityId = fields.getOrNull(7)?.takeIf(String::isNotBlank),
            connectionId = fields.getOrNull(8)?.takeIf(String::isNotBlank),
            connectionName = fields.getOrElse(9) { "" },
        )
    }
}

private val hopListSaver = listSaver<List<HopDraft>, String>(
    save = { list -> list.flatMap(HopDraft::flatten) },
    restore = { flat -> flat.chunked(HopDraft.FIELD_COUNT).map { HopDraft.restore(it) } },
)

@Composable
private fun TunnelChainDiagram(
    hops: List<HopDraft>,
    selectedIndex: Int?,
    onHopSelected: (Int) -> Unit,
) {
    Column {
        Text("Route", style = MaterialTheme.typography.labelLarge)
        Text(
            "Traffic jumps through each SSH machine in order before reaching agent services.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Phone", style = MaterialTheme.typography.labelMedium)
                }
            }
            hops.forEachIndexed { index, hop ->
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                val selected = selectedIndex == index
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .semantics { contentDescription = "Route hop ${index + 1} ${hop.host}" }
                        .clickable { onHopSelected(index) },
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .16f)) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            hop.host.ifBlank { "Hop ${index + 1}" },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = if (hop.host.isBlank()) RelayAmber else Color.Unspecified,
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(13.dp), tint = RelayGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Target", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HopEditorCard(
    hop: HopDraft,
    index: Int,
    total: Int,
    connections: List<SshConnection>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (HopDraft) -> Unit,
    onChooseConnection: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val title = when {
        total == 1 -> "SSH machine"
        index == 0 -> "Hop 1 · entry"
        index == total - 1 -> "Hop ${index + 1} · final"
        else -> "Hop ${index + 1}"
    }
    val selectedConnection = connections.firstOrNull { it.id == hop.connectionId }
    val summary = selectedConnection?.let { "${it.name} · ${it.ssh.host}:${it.ssh.port}" } ?: "Choose an SSH connection"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "$title $summary" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selectedConnection == null) {
                    Box(Modifier.size(8.dp).background(RelayAmber, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                if (selectedConnection != null) {
                    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "SSH",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    if (total > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Move up")
                            }
                            TextButton(onClick = { onMove(index + 1) }, enabled = index < total - 1) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Move down")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onRemove) { Text("Remove", color = RelayRed) }
                        }
                    }
                    Button(onClick = onChooseConnection, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedConnection == null) "Choose SSH connection" else "Change SSH connection")
                    }
                    selectedConnection?.let { connection ->
                        Spacer(Modifier.height(8.dp))
                        Text(connection.ssh.host + ":" + connection.ssh.port, style = MaterialTheme.typography.bodySmall)
                        Text(
                            connection.ssh.identityName.ifBlank { connection.ssh.username },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseSshConnectionSheet(
    connections: List<SshConnection>,
    onDismiss: () -> Unit,
    onChoose: (SshConnection) -> Unit,
    onAddConnection: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SheetTitle("Choose SSH connection", onDismiss)
                Text(
                    "Jump hosts are reusable connections. Editing one updates every route that uses it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onAddConnection, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add SSH connection")
                }
            }
            if (connections.isEmpty()) {
                item {
                    Text("No SSH connections saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(connections, key = SshConnection::id) { connection ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(connection) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(connection.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${connection.ssh.host}:${connection.ssh.port} · " +
                                connection.ssh.identityName.ifBlank { connection.ssh.username },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSshConnectionSheet(
    initial: SshConnection? = null,
    identities: List<SshIdentity>,
    onDismiss: () -> Unit,
    onSave: (SshConnection) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var host by rememberSaveable(initial?.id) { mutableStateOf(initial?.ssh?.host.orEmpty()) }
    var port by rememberSaveable(initial?.id) { mutableStateOf((initial?.ssh?.port ?: 22).toString()) }
    var fingerprint by rememberSaveable(initial?.id) { mutableStateOf(initial?.ssh?.hostKeyFingerprint.orEmpty()) }
    var identityId by rememberSaveable(initial?.id) { mutableStateOf(initial?.ssh?.identityId) }
    val selectedIdentity = identities.firstOrNull { it.id == identityId }
    val valid = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull() in 1..65535 && selectedIdentity != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        ) {
            item {
                SheetTitle(if (initial == null) "Add SSH connection" else "Edit SSH connection", onDismiss)
                Text("An address and an identity. Tunnel paths select these saved connections as jump hosts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                AppTextField(name, { name = it }, "Connection name", "Office gateway")
                AppTextField(host, { host = it }, "SSH host", "gateway.example.com")
                AppTextField(port, { port = it.filter(Char::isDigit) }, "SSH port", "22")
                Text("Identity", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    identities.forEach { identity ->
                        FilterChip(
                            selected = identityId == identity.id,
                            onClick = { identityId = identity.id },
                            label = { Text(identity.name) },
                        )
                    }
                }
                if (identities.isEmpty()) {
                    Text("Add an identity first to use this connection.", style = MaterialTheme.typography.bodySmall, color = RelayAmber)
                } else {
                    selectedIdentity?.let { identity ->
                        Text("${identity.username} · ${identity.kind.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AppTextField(fingerprint, { fingerprint = it }, "Host-key SHA-256 fingerprint", "SHA256:…")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSave(
                            SshConnection(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                ssh = SshTunnelConfig(
                                    host = host.trim(),
                                    port = checkNotNull(port.toIntOrNull()),
                                    identityId = checkNotNull(identityId),
                                    hostKeyFingerprint = fingerprint.trim(),
                                ),
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(if (initial == null) "Save SSH connection" else "Save changes") }
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

private fun TunnelRouteRule.displayNameForEditor(): String = hostPattern + (port?.let { ":$it" } ?: "")

private const val MAX_TEXT_PREVIEW_CHARS = 500_000
