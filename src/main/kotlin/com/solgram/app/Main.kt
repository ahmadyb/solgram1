package com.solgram.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.solgram.automation.CaLatest
import com.solgram.automation.LocalApiServer
import com.solgram.concurrency.SyncScheduler
import com.solgram.concurrency.TelegramActor
import com.solgram.db.DatabaseFactory
import com.solgram.diagnostics.Doctor
import com.solgram.domain.alerts.AlertEngine
import com.solgram.domain.detect.CaDetector
import com.solgram.domain.portfolio.PortfolioWatcher
import com.solgram.domain.price.PriceFeed
import com.solgram.domain.rules.ForwardRule
import com.solgram.domain.search.SearchFilter
import com.solgram.domain.search.SearchIndexer
import com.solgram.domain.signals.*
import com.solgram.domain.telegram.AuthState
import com.solgram.domain.telegram.Chat
import com.solgram.domain.telegram.Message
import com.solgram.domain.telegram.TdLibEngine
import com.solgram.singleton.SingleInstanceGuard
import com.solgram.ui.cafeed.CaFeedScreen
import com.solgram.ui.chat.ChatListScreen
import com.solgram.ui.chat.MessageListScreen
import com.solgram.ui.compare.CompareChannelsScreen
import com.solgram.ui.compare.ComparisonResult
import com.solgram.ui.rules.RulesScreen
import com.solgram.ui.search.SearchScreen
import com.solgram.ui.settings.SettingsScreen
import com.solgram.ui.signals.*
import com.solgram.ui.theme.BackdropType
import com.solgram.ui.theme.SolgramTheme
import com.solgram.ui.theme.SolgramThemeWrapper
import com.solgram.ui.watchlist.WatchlistScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Doctor.handleFlags(args)
    Doctor.startPhase("AppInit")

    // Command-line flags: --debug, --width/--height, --native-frame, --no-tray, --allow-multiple
    val debug = "--debug" in args
    val width = args.indexOf("--width").takeIf { it >= 0 }?.let { args.getOrNull(it+1)?.toIntOrNull() } ?: 1200
    val height = args.indexOf("--height").takeIf { it >= 0 }?.let { args.getOrNull(it+1)?.toIntOrNull() } ?: 800
    val nativeFrame = "--native-frame" in args
    val noTray = "--no-tray" in args
    val allowMultiple = "--allow-multiple" in args

    if (debug) System.setProperty("solgram.debug", "true")

    val appDir = File(System.getProperty("user.home"), "AppData/Roaming/Solgram").apply { mkdirs() }.toPath()
    val lockFile = File(appDir.toFile(), "solgram.lock")

    val guard = SingleInstanceGuard(lockFile)
    if (!allowMultiple) {
        val acquired = guard.tryAcquire { secondArgs ->
            println("Second instance args: $secondArgs")
            // Handle deep link etc.
        }
        if (!acquired) {
            println("Another instance already running, exiting")
            exitProcess(0)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        guard.release()
    })

    Doctor.endPhase("AppInit")
    Doctor.startPhase("Database")

    val dbFile = File(appDir.toFile(), "solgram.db")
    val driver = DatabaseFactory.createDriver(dbFile)
    val db = DatabaseFactory.createDatabase(driver)

    Doctor.endPhase("Database")
    Doctor.startPhase("TDLib")

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val telegramEngine = TdLibEngine(appDir, scope = scope)

    runBlocking {
        telegramEngine.connect()
    }

    Doctor.endPhase("TDLib")
    Doctor.startPhase("FirstUIPaint")

    // Domain engines
    val reputationEngine = ReputationEngine()
    val velocityEngine = VelocityAlertEngine()
    val leaderboardEngine = LeaderboardEngine()
    val priceFeed = PriceFeed(scope)
    val portfolioWatcher = PortfolioWatcher(scope)
    val searchIndexer = SearchIndexer()
    val syncScheduler = SyncScheduler(scope)
    val apiServer = LocalApiServer()
    val alertEngine = AlertEngine(telegramEngine, scope)

    // Mock data for UI
    val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
    val messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val activeChatId = MutableStateFlow<Long?>(null)

    scope.launch {
        val chatsResult = telegramEngine.getChats(100)
        if (chatsResult is com.solgram.domain.telegram.SolgramResult.Success) {
            chatsFlow.value = chatsResult.value
        }
    }

    // Start background services
    if (true) { // background sync default ON
        syncScheduler.start()
    }
    priceFeed.startPolling()
    portfolioWatcher.startPolling()

    Doctor.endPhase("FirstUIPaint")
    Doctor.recordStartup()

    application {
        var currentTheme by remember { mutableStateOf(SolgramTheme.ABYSSAL_SONAR) }
        var backdropType by remember { mutableStateOf(BackdropType.DRAGON_SCALE) }
        var intensity by remember { mutableStateOf(1.0f) }
        var scale by remember { mutableStateOf(1.0f) }
        var keyboardNav by remember { mutableStateOf(false) }
        var backgroundSync by remember { mutableStateOf(true) }
        var dataSaver by remember { mutableStateOf(false) }
        var apiEnabled by remember { mutableStateOf(false) }
        var bearerToken by remember { mutableStateOf(apiServer.getToken()) }
        var selectedScreen by remember { mutableStateOf("chats") }
        var searchQuery by remember { mutableStateOf("") }
        var showArchived by remember { mutableStateOf(false) }

        val windowState = rememberWindowState(width = width.dp, height = height.dp, placement = WindowPlacement.Floating)

        Window(
            onCloseRequest = {
                if (!noTray) {
                    // Hide to tray by default
                    windowState.isMinimized = true
                } else {
                    // Quit
                    scope.cancel()
                    guard.release()
                    exitApplication()
                }
            },
            title = "Solgram 2.0.0",
            state = windowState,
            undecorated = !nativeFrame
        ) {
            // Custom frameless window - self-drawn title bar, 8 resize regions, native maximize mirrored
            // For brevity, using default window chrome with option for native-frame

            SolgramThemeWrapper(theme = currentTheme) {
                MaterialTheme {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Sidebar
                        NavigationRail {
                            NavigationRailItem(selected = selectedScreen == "chats", onClick = { selectedScreen = "chats" }, icon = { Text("💬") }, label = { Text("Chats") })
                            NavigationRailItem(selected = selectedScreen == "signals", onClick = { selectedScreen = "signals" }, icon = { Text("📊") }, label = { Text("Signals") })
                            NavigationRailItem(selected = selectedScreen == "compare", onClick = { selectedScreen = "compare" }, icon = { Text("⚖️") }, label = { Text("Compare") })
                            NavigationRailItem(selected = selectedScreen == "cafeed", onClick = { selectedScreen = "cafeed" }, icon = { Text("📡") }, label = { Text("CA Feed") })
                            NavigationRailItem(selected = selectedScreen == "rules", onClick = { selectedScreen = "rules" }, icon = { Text("📜") }, label = { Text("Rules") })
                            NavigationRailItem(selected = selectedScreen == "watchlist", onClick = { selectedScreen = "watchlist" }, icon = { Text("👁️") }, label = { Text("Watchlist") })
                            NavigationRailItem(selected = selectedScreen == "search", onClick = { selectedScreen = "search" }, icon = { Text("🔍") }, label = { Text("Search") })
                            NavigationRailItem(selected = selectedScreen == "settings", onClick = { selectedScreen = "settings" }, icon = { Text("⚙️") }, label = { Text("Settings") })
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (selectedScreen) {
                                "chats" -> {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Box(modifier = Modifier.weight(0.3f)) {
                                            ChatListScreen(
                                                chats = chatsFlow,
                                                searchQuery = searchQuery,
                                                onSearchChange = { searchQuery = it },
                                                onChatSelected = { chatId ->
                                                    activeChatId.value = chatId
                                                    syncScheduler.recordChatOpened(chatId)
                                                    scope.launch {
                                                        val msgs = telegramEngine.getMessages(chatId, limit = 100)
                                                        if (msgs is com.solgram.domain.telegram.SolgramResult.Success) {
                                                            messagesFlow.value = msgs.value
                                                            searchIndexer.indexMessages(msgs.value)
                                                        }
                                                    }
                                                },
                                                showArchived = showArchived,
                                                onToggleArchived = { showArchived = !showArchived },
                                                theme = currentTheme
                                            )
                                        }
                                        Box(modifier = Modifier.weight(0.7f)) {
                                            MessageListScreen(
                                                messages = messagesFlow,
                                                activeChatId = activeChatId.value,
                                                onSend = { text ->
                                                    scope.launch {
                                                        activeChatId.value?.let { chatId ->
                                                            telegramEngine.sendMessage(chatId, text)
                                                            // Run CA detection
                                                            val detections = CaDetector.detect(text)
                                                            detections.forEach { det ->
                                                                val ca = CaLatest(det.chain.name, det.address, "You", System.currentTimeMillis()/1000, "now")
                                                                apiServer.broadcastDetection(ca)
                                                            }
                                                        }
                                                    }
                                                },
                                                onReact = { msgId, emoji -> scope.launch { activeChatId.value?.let { telegramEngine.addReaction(it, msgId, emoji) } } },
                                                onCopy = { /* clipboard */ },
                                                onTranslate = { /* translate */ },
                                                theme = currentTheme
                                            )
                                        }
                                    }
                                }
                                "signals" -> {
                                    SignalsDashboardScreen(
                                        viewMode = SignalsViewMode.BY_TOKEN,
                                        onViewModeChange = {},
                                        signalsByToken = emptyList(),
                                        signalsByCall = emptyList(),
                                        reputationEngine = reputationEngine,
                                        velocityAlerts = velocityEngine.alerts.collectAsState().value,
                                        leaderboard = emptyList(),
                                        sentimentSummaries = emptyMap(),
                                        theme = currentTheme
                                    )
                                }
                                "compare" -> {
                                    CompareChannelsScreen(
                                        ultimateChannel = null,
                                        matchChannels = emptyList(),
                                        results = emptyList(),
                                        combinedSuccessRate = 0.0,
                                        matchWindow = "24h",
                                        onMatchWindowChange = {},
                                        lookback = "7d",
                                        onLookbackChange = {},
                                        chainFilter = "All",
                                        onChainFilterChange = {},
                                        theme = currentTheme,
                                        onCompare = {}
                                    )
                                }
                                "cafeed" -> {
                                    CaFeedScreen(
                                        detections = apiServer.detections.collectAsState().value,
                                        chainFilter = "All",
                                        textFilter = "",
                                        onChainFilterChange = {},
                                        onTextFilterChange = {},
                                        onTrade = { chain, addr ->
                                            // Trade button opens URL in system browser - pure URL construction, no wallet integration
                                            val c = try { com.solgram.domain.detect.Chain.valueOf(chain) } catch (e: Exception) { com.solgram.domain.detect.Chain.SOLANA }
                                            val urls = com.solgram.domain.trade.TradeUrlBuilder.buildUrls(c, addr)
                                            urls.firstOrNull()?.let { com.solgram.domain.trade.TradeUrlBuilder.openUrl(it.url) }
                                        },
                                        theme = currentTheme
                                    )
                                }
                                "rules" -> {
                                    RulesScreen(rules = emptyList(), onAddRule = {}, onEditRule = {}, onDeleteRule = {}, onTestRule = { _, _ -> }, theme = currentTheme)
                                }
                                "watchlist" -> {
                                    WatchlistScreen(
                                        wallets = portfolioWatcher.wallets.collectAsState().value,
                                        crossRefs = emptyMap(),
                                        onAddWallet = { addr, chain -> portfolioWatcher.addWallet(addr, chain) },
                                        onRemoveWallet = { addr -> portfolioWatcher.removeWallet(addr) },
                                        theme = currentTheme
                                    )
                                }
                                "search" -> {
                                    var filter by remember { mutableStateOf(SearchFilter()) }
                                    SearchScreen(
                                        filter = filter,
                                        onFilterChange = { filter = it },
                                        results = searchIndexer.search(filter),
                                        onJumpToMessage = { chatId, msgId -> activeChatId.value = chatId },
                                        theme = currentTheme
                                    )
                                }
                                "settings" -> {
                                    SettingsScreen(
                                        currentTheme = currentTheme,
                                        onThemeChange = { currentTheme = it },
                                        backdropType = backdropType,
                                        onBackdropChange = { backdropType = it },
                                        intensity = intensity,
                                        onIntensityChange = { intensity = it },
                                        scale = scale,
                                        onScaleChange = { scale = it },
                                        keyboardNavEnabled = keyboardNav,
                                        onKeyboardNavToggle = { keyboardNav = it },
                                        backgroundSyncEnabled = backgroundSync,
                                        onBackgroundSyncToggle = { backgroundSync = it; syncScheduler.setEnabled(it) },
                                        dataSaver = dataSaver,
                                        onDataSaverToggle = { dataSaver = it },
                                        apiServerEnabled = apiEnabled,
                                        onApiServerToggle = {
                                            apiEnabled = it
                                            if (it) apiServer.start() else apiServer.stop()
                                        },
                                        bearerToken = bearerToken,
                                        onRegenerateToken = { bearerToken = apiServer.regenerateToken() },
                                        theme = currentTheme
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // System tray - closing window hides to tray by default, only Quit exits, menu: Show | Pause/Resume automation | Quit
        if (!noTray) {
            TrayManager(
                onShow = { windowState.isMinimized = false },
                onPauseResume = {
                    // Pause/resume automation
                    backgroundSync = !backgroundSync
                    syncScheduler.setEnabled(backgroundSync)
                },
                onQuit = {
                    scope.cancel()
                    guard.release()
                    exitApplication()
                }
            )
        }
    }
}
