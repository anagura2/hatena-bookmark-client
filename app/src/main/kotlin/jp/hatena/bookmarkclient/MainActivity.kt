package jp.hatena.bookmarkclient

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private lateinit var oauthClient: HatenaOAuthClient
    private val readPreferences by lazy {
        getSharedPreferences("reading_history", MODE_PRIVATE)
    }
    private var oauthError by mutableStateOf<String?>(null)
    private var oauthLoading by mutableStateOf(false)
    private var oauthWaitingForVerifier by mutableStateOf(false)
    private var oauthLoggedIn by mutableStateOf(false)
    private var readUrls by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthClient = HatenaOAuthClient(
            context = this,
            consumerKey = BuildConfig.HATENA_CONSUMER_KEY,
            consumerSecret = BuildConfig.HATENA_CONSUMER_SECRET,
        )
        oauthLoggedIn = oauthClient.savedTokens() != null
        readUrls = readPreferences.getStringSet("urls", emptySet()).orEmpty()
        setContent {
            HatenaBookmarkTheme {
                HatenaBookmarkNavHost(
                    oauthClient = oauthClient,
                    oauthError = oauthError,
                    oauthLoading = oauthLoading,
                    oauthWaitingForVerifier = oauthWaitingForVerifier,
                    oauthLoggedIn = oauthLoggedIn,
                    onOAuthErrorDismiss = { oauthError = null },
                    onOAuthLogin = ::startOAuthLogin,
                    onVerifierSubmit = ::completeOAuthLogin,
                    onLogout = {
                        oauthClient.clearTokens()
                        oauthLoggedIn = false
                    },
                    readUrls = readUrls,
                    onEntryOpened = { url ->
                        val updated = readUrls + url
                        readPreferences.edit().putStringSet("urls", updated).apply()
                        readUrls = updated
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startOAuthLogin() {
        if (oauthLoading) return
        oauthError = null
        oauthLoading = true
        lifecycleScope.launch {
            try {
                val authorizationIntent = withContext(Dispatchers.IO) {
                    oauthClient.beginAuthorization()
                }
                startActivity(authorizationIntent)
                oauthWaitingForVerifier = true
            } catch (exception: Exception) {
                oauthError = exception.message ?: "OAuthログインを開始できませんでした"
            } finally {
                oauthLoading = false
            }
        }
    }

    private fun completeOAuthLogin(verifier: String) {
        oauthWaitingForVerifier = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { oauthClient.completeAuthorization(verifier) }
                oauthLoggedIn = true
            } catch (exception: Exception) {
                oauthError = exception.message ?: "OAuthログインに失敗しました"
            }
        }
    }
}

private const val HOME_ROUTE = "home"
private const val BOOKMARKS_ROUTE = "bookmarks"
private const val NOTIFICATIONS_ROUTE = "notifications"
private const val ENTRY_ROUTE = "entry"
private const val COMMENT_ROUTE = "comment"

private class RetainedWebViewStore {
    private val views = mutableMapOf<String, WebView>()

    fun obtain(context: android.content.Context, key: String): WebView =
        views.getOrPut(key) { WebView(context.applicationContext) }

    fun destroyAll() {
        views.values.forEach { view ->
            view.stopLoading()
            view.destroy()
        }
        views.clear()
    }
}

private class MyBookmarksStateHolder {
    var state by mutableStateOf<MyBookmarksState>(MyBookmarksState.Loading)
    var currentUserName by mutableStateOf("")
}

@Composable
private fun HatenaBookmarkNavHost(
    oauthClient: HatenaOAuthClient,
    oauthError: String?,
    oauthLoading: Boolean,
    oauthWaitingForVerifier: Boolean,
    oauthLoggedIn: Boolean,
    onOAuthErrorDismiss: () -> Unit,
    onOAuthLogin: () -> Unit,
    onVerifierSubmit: (String) -> Unit,
    onLogout: () -> Unit,
    readUrls: Set<String>,
    onEntryOpened: (String) -> Unit,
) {
    val navController = rememberNavController()
    val webViewStore = remember { RetainedWebViewStore() }
    val myBookmarksStateHolder = remember { MyBookmarksStateHolder() }
    DisposableEffect(Unit) {
        onDispose { webViewStore.destroyAll() }
    }
    val notificationPreferences = LocalContext.current.getSharedPreferences(
        "notification_state",
        0,
    )

    fun openEntry(entry: PopularEntry) {
        navController.navigate(
            "$ENTRY_ROUTE?url=${Uri.encode(entry.url)}" +
                "&title=${Uri.encode(entry.title)}" +
                "&domain=${Uri.encode(entry.domain)}" +
                "&bookmarkCount=${entry.bookmarkCount}" +
                "&commentCount=${entry.commentCount}",
        )
    }

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            PopularEntriesScreen(
                oauthClient = oauthClient,
                oauthError = oauthError,
                oauthLoading = oauthLoading,
                oauthWaitingForVerifier = oauthWaitingForVerifier,
                oauthLoggedIn = oauthLoggedIn,
                onOAuthErrorDismiss = onOAuthErrorDismiss,
                onOAuthLogin = onOAuthLogin,
                onVerifierSubmit = onVerifierSubmit,
                onLogout = onLogout,
                readUrls = readUrls,
                onEntryOpened = onEntryOpened,
                onOpenMyBookmarks = { navController.navigate(BOOKMARKS_ROUTE) },
                onOpenNotifications = { navController.navigate(NOTIFICATIONS_ROUTE) },
                onOpenEntry = ::openEntry,
            )
        }
        composable(BOOKMARKS_ROUTE) {
            MyBookmarksScreen(
                oauthClient = oauthClient,
                stateHolder = myBookmarksStateHolder,
                onBack = { navController.popBackStack() },
                onSelectEntry = ::openEntry,
                onSelectComment = {
                    navController.navigate("$COMMENT_ROUTE?uri=${Uri.encode(it)}")
                },
            )
        }
        composable(NOTIFICATIONS_ROUTE) {
            NotificationsScreen(
                oauthClient = oauthClient,
                onBack = { navController.popBackStack() },
                onNotificationClick = {
                    navController.navigate("$COMMENT_ROUTE?uri=${Uri.encode(it)}")
                },
                onOpened = { createdAt ->
                    notificationPreferences.edit()
                        .putLong("last_read_created", createdAt)
                        .apply()
                },
            )
        }
        composable(
            route = "$ENTRY_ROUTE?url={url}&title={title}&domain={domain}" +
                "&bookmarkCount={bookmarkCount}&commentCount={commentCount}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("domain") { type = NavType.StringType },
                navArgument("bookmarkCount") { type = NavType.IntType },
                navArgument("commentCount") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments ?: return@composable
            val initialEntry = PopularEntry(
                title = arguments.getString("title").orEmpty(),
                url = arguments.getString("url").orEmpty(),
                domain = arguments.getString("domain").orEmpty(),
                bookmarkCount = arguments.getInt("bookmarkCount"),
                commentCount = arguments.getInt("commentCount"),
                imageUrl = null,
            )
            var selectedEntry by remember(initialEntry.url) { mutableStateOf(initialEntry) }
            EntryWebViewScreen(
                entry = selectedEntry,
                oauthClient = oauthClient,
                webViewStore = webViewStore,
                onBack = { navController.popBackStack() },
                onSelectRelatedEntry = { related ->
                    selectedEntry = PopularEntry(
                        title = related.title,
                        url = related.articleUrl,
                        domain = Uri.parse(related.articleUrl).host ?: related.articleUrl,
                        bookmarkCount = related.bookmarkCount,
                        commentCount = 0,
                        imageUrl = null,
                    )
                },
            )
        }
        composable(
            route = "$COMMENT_ROUTE?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val commentUri = backStackEntry.arguments?.getString("uri").orEmpty()
            CommentWebViewScreen(
                commentUri = commentUri,
                webViewStore = webViewStore,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PopularEntriesScreen(
    oauthClient: HatenaOAuthClient,
    oauthError: String?,
    oauthLoading: Boolean,
    oauthWaitingForVerifier: Boolean,
    oauthLoggedIn: Boolean,
    onOAuthErrorDismiss: () -> Unit,
    onOAuthLogin: () -> Unit,
    onVerifierSubmit: (String) -> Unit,
    onLogout: () -> Unit,
    readUrls: Set<String>,
    onEntryOpened: (String) -> Unit,
    onOpenMyBookmarks: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenEntry: (PopularEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val categoryStates = remember { mutableStateMapOf<String, EntriesState>() }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    var selectedCategory by remember { mutableStateOf(entryCategories.first()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var hasUnreadNotifications by remember { mutableStateOf(false) }
    val notificationPreferences = LocalContext.current.getSharedPreferences(
        "notification_state",
        0,
    )

    fun loadEntries(category: EntryCategory = selectedCategory, forceReload: Boolean = false) {
        if (!forceReload && categoryStates[category.feedUrl] is EntriesState.Loaded) {
            return
        }
        categoryStates[category.feedUrl] = EntriesState.Loading
        scope.launch {
            categoryStates[category.feedUrl] = try {
                EntriesState.Loaded(withContext(Dispatchers.IO) {
                    fetchPopularEntries(category.feedUrl)
                })
            } catch (exception: Exception) {
                EntriesState.Error(exception.message ?: "記事を取得できませんでした")
            }
        }
    }

    fun selectCategory(category: EntryCategory) {
        if (category == selectedCategory) return
        selectedCategory = category
        if (categoryStates[category.feedUrl] !is EntriesState.Loaded) {
            loadEntries(category)
        }
    }

    LaunchedEffect(selectedCategory) {
        if (categoryStates[selectedCategory.feedUrl] !is EntriesState.Loaded) {
            loadEntries(selectedCategory)
        }
    }

    LaunchedEffect(oauthLoggedIn) {
        if (!oauthLoggedIn) {
            hasUnreadNotifications = false
        } else {
            try {
                val notifications = withContext(Dispatchers.IO) {
                    oauthClient.fetchNotifications()
                }
                val lastRead = notificationPreferences.getLong("last_read_created", 0L)
                hasUnreadNotifications = notifications.any { it.createdAt > lastRead }
            } catch (_: Exception) {
                hasUnreadNotifications = false
            }
        }
    }

    if (oauthLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("はてなに接続中") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("認証ページを準備しています…")
                }
            },
            confirmButton = {},
        )
    }
    oauthError?.let { message ->
        AlertDialog(
            onDismissRequest = onOAuthErrorDismiss,
            title = { Text("OAuthログインエラー") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onOAuthErrorDismiss) {
                    Text("閉じる")
                }
            },
        )
    }
    if (oauthWaitingForVerifier) {
        var verifier by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("認証コードを入力") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ブラウザのはてな認証画面に表示されたPINコードを入力してください。")
                    OutlinedTextField(
                        value = verifier,
                        onValueChange = { verifier = it },
                        label = { Text("PINコード") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onVerifierSubmit(verifier) },
                    enabled = verifier.isNotBlank(),
                ) {
                    Text("認証する")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(200.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "はてなブックマーク",
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(color = Color(0xFF294047))
                    DrawerItem(
                        label = "ホーム",
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                    )
                    DrawerItem("For You", false, enabled = false) {}
                    DrawerItem("お気に入り", false, enabled = false) {}
                    DrawerItem("関心ワード", false, enabled = false) {}
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF294047),
                    )
                    entryCategories.drop(2).forEach { category ->
                        DrawerItem(
                            label = category.label,
                            selected = selectedCategory == category,
                            onClick = {
                                selectCategory(category)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF294047),
                    )
                    DrawerItem(
                        label = if (oauthLoggedIn) "ログアウト" else "はてなにログイン",
                        selected = false,
                        onClick = {
                            if (oauthLoggedIn) {
                                onLogout()
                                scope.launch { drawerState.close() }
                            } else {
                                scope.launch { drawerState.close() }
                                onOAuthLogin()
                            }
                        }
                    )
                    DrawerItem(
                        label = "通知",
                        selected = false,
                        enabled = oauthLoggedIn,
                        hasBadge = hasUnreadNotifications,
                        onClick = {
                            hasUnreadNotifications = false
                            onOpenNotifications()
                            scope.launch { drawerState.close() }
                        },
                    )
                    DrawerItem("設定", false, enabled = false) {}
                }
            }
        },
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = "ホーム", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "メニュー",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadEntries(selectedCategory, forceReload = true) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "検索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                CategoryTabs(
                    selectedCategory = selectedCategory,
                    onCategorySelected = ::selectCategory,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedCategory) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            val currentIndex = entryCategories.indexOf(selectedCategory)
                            when {
                                totalDrag <= -80f -> {
                                    val nextIndex = (currentIndex + 1) % entryCategories.size
                                    selectCategory(entryCategories[nextIndex])
                                }
                                totalDrag >= 80f -> {
                                    val previousIndex =
                                        (currentIndex - 1 + entryCategories.size) % entryCategories.size
                                    selectCategory(entryCategories[previousIndex])
                                }
                            }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)).togetherWith(
                        fadeOut(animationSpec = tween(160)),
                    )
                },
                label = "category-content",
            ) { targetCategory ->
            when (val currentState = categoryStates[targetCategory.feedUrl] ?: EntriesState.Loading) {
                EntriesState.Loading -> LoadingContent(innerPadding)
                is EntriesState.Error -> ErrorContent(
                    message = currentState.message,
                    contentPadding = innerPadding,
                    onRetry = { loadEntries(targetCategory, forceReload = true) },
                )
                is EntriesState.Loaded -> {
                    if (currentState.entries.isEmpty()) {
                        ErrorContent(
                            message = "表示できる記事がありません",
                            contentPadding = innerPadding,
                            onRetry = { loadEntries(targetCategory, forceReload = true) },
                        )
                    } else {
                        val listState = listStates.getOrPut(targetCategory.feedUrl) {
                            LazyListState()
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = 96.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(currentState.entries, key = { it.url }) { entry ->
                                PopularEntryCard(
                                    entry = entry,
                                    isRead = entry.url in readUrls,
                                    onClick = {
                                        onEntryOpened(entry.url)
                                        onOpenEntry(entry)
                                    },
                                )
                            }
                            }
                            }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingActionButton(
                    onClick = onOpenMyBookmarks,
                    modifier = Modifier.padding(end = 20.dp, bottom = 20.dp),
                    containerColor = Color(0xFF00B8C8),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "ブックマーク")
                }
            }
        }
    }

    }
}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NotificationsScreen(
    oauthClient: HatenaOAuthClient,
    onBack: () -> Unit,
    onOpened: (Long) -> Unit,
    onNotificationClick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<NotificationsState>(NotificationsState.Loading) }
    BackHandler(onBack = onBack)

    fun loadNotifications() {
        state = NotificationsState.Loading
        scope.launch {
            state = try {
                NotificationsState.Loaded(
                    withContext(Dispatchers.IO) { oauthClient.fetchNotifications() },
                )
            } catch (exception: Exception) {
                NotificationsState.Error(exception.message ?: "通知を取得できませんでした")
            }
        }
    }

    LaunchedEffect(Unit) { loadNotifications() }
    Scaffold(
        containerColor = Color(0xFFF5FAFA),
        topBar = {
            TopAppBar(
                title = { Text("通知", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF17383C),
                    navigationIconContentColor = Color(0xFF17383C),
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when (val currentState = state) {
            NotificationsState.Loading -> LoadingContent(padding)
            is NotificationsState.Error -> ErrorContent(
                message = currentState.message,
                contentPadding = padding,
                onRetry = ::loadNotifications,
            )
            is NotificationsState.Loaded -> {
                LaunchedEffect(currentState.items) {
                    currentState.items.maxOfOrNull { it.createdAt }?.let(onOpened)
                }
                if (currentState.items.isEmpty()) {
                    ErrorContent(
                        message = "通知はありません",
                        contentPadding = padding,
                        onRetry = ::loadNotifications,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(Color(0xFFF5FAFA)),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(currentState.items) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = { onNotificationClick(notification.subject) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: HatenaNotification,
    onClick: () -> Unit,
) {
    val userText = when {
        notification.users.isEmpty() -> "誰か"
        notification.users.size == 1 -> "${notification.users.first()}さん"
        else -> notification.users.take(3).joinToString("さん、", postfix = "さん")
    }
    val message = if (notification.verb == "star") {
        "${userText}があなたのブックマークに★をつけました"
    } else {
        "${userText}から通知があります"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 34.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = Color(0xFF17383C),
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
        }
        if (notification.subjectTitle.isNotBlank()) {
            Text(
                text = notification.subjectTitle,
                modifier = Modifier.padding(top = 8.dp),
                color = Color(0xFF496568),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 22.dp),
            color = Color(0xFFD3D0D5),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MyBookmarksScreen(
    oauthClient: HatenaOAuthClient,
    stateHolder: MyBookmarksStateHolder,
    onBack: () -> Unit,
    onSelectEntry: (PopularEntry) -> Unit,
    onSelectComment: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val state = stateHolder.state
    val pageSize = 50
    BackHandler(onBack = onBack)

    suspend fun enrichBookmarks(
        bookmarks: List<MyBookmarkEntry>,
        userName: String,
    ): List<MyBookmarkEntry> =
        withContext(Dispatchers.IO) {
            bookmarks.map { bookmark ->
                async {
                    val enriched = bookmark.copy(
                        bookmarkCount = fetchEntryBookmarkCount(bookmark.url),
                        starCount = fetchMyBookmarkStarCount(bookmark, userName),
                    )
                    if (enriched.commentUri.contains("4793011382430096898")) {
                        Log.d("MyBookmarkStars", "enriched ${enriched.url} starCount=${enriched.starCount}")
                    }
                    enriched
                }
            }.awaitAll()
        }

    fun loadBookmarks() {
        stateHolder.state = MyBookmarksState.Loading
        scope.launch {
            stateHolder.state = try {
                val json = withContext(Dispatchers.IO) { oauthClient.fetchMyBookmarks() }
                val userName = withContext(Dispatchers.IO) { oauthClient.fetchMyUserName() }
                stateHolder.currentUserName = userName
                val bookmarks = parseMyBookmarks(json)
                val firstPage = enrichBookmarks(bookmarks.take(pageSize), userName)
                MyBookmarksState.Loaded(
                    entries = firstPage,
                    allEntries = bookmarks,
                    hasMore = bookmarks.size > pageSize,
                )
            } catch (exception: Exception) {
                MyBookmarksState.Error(exception.message ?: "ブックマークを取得できませんでした")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (stateHolder.state is MyBookmarksState.Loading) {
            loadBookmarks()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("マイブックマーク", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("すべて", "あとで読む").forEachIndexed { index, label ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            color = if (selectedTab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(
                                    if (selectedTab == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                        )
                    }
                }
            }
            when (val currentState = state) {
            MyBookmarksState.Loading -> LoadingContent(PaddingValues())
            is MyBookmarksState.Error -> ErrorContent(
                message = currentState.message,
                contentPadding = PaddingValues(),
                onRetry = ::loadBookmarks,
            )
            is MyBookmarksState.Loaded -> {
                val visibleEntries = if (selectedTab == 1) {
                    currentState.allEntries.filter { entry ->
                        entry.tags.any { tag -> tag.trim() == "あとで読む" }
                    }
                } else {
                    currentState.entries
                }
                if (visibleEntries.isEmpty()) {
                    ErrorContent(
                        message = "ブックマークがありません",
                        contentPadding = PaddingValues(),
                        onRetry = ::loadBookmarks,
                    )
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(visibleEntries, key = { it.url }) { bookmark ->
                            if (bookmark.url == "https://onaji.me/entry/2026/09/14") {
                                Log.d("MyBookmarkStars", "render ${bookmark.url} starCount=${bookmark.starCount}")
                            }
                            val parsedUrl = Uri.parse(bookmark.url)
                            PopularEntryCard(
                                entry = PopularEntry(
                                    title = bookmark.title,
                                    url = bookmark.url,
                                    domain = parsedUrl.host ?: bookmark.url,
                                    bookmarkCount = bookmark.bookmarkCount,
                                    commentCount = 0,
                                    imageUrl = null,
                                ),
                                onClick = {
                                    onSelectEntry(
                                        PopularEntry(
                                            title = bookmark.title,
                                            url = bookmark.url,
                                            domain = parsedUrl.host ?: bookmark.url,
                                            bookmarkCount = bookmark.bookmarkCount,
                                            commentCount = 0,
                                            imageUrl = null,
                                        ),
                                    )
                                },
                            )
                            if (bookmark.comment.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            bookmarkDetailUri(bookmark.commentUri)?.let(onSelectComment)
                                        }
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = bookmark.comment,
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFFB8C5C7),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (bookmark.starCount > 0) {
                                        Surface(
                                            color = Color(0xFFFFF3B0),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = "★ ${bookmark.starCount}",
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp,
                                                ),
                                                color = Color(0xFF8A6500),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (currentState.loadingMore) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    LaunchedEffect(currentState.entries.size, currentState.hasMore) {
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        }.collectLatest { lastVisibleIndex ->
                            if (lastVisibleIndex >= currentState.entries.size - 3 &&
                                currentState.hasMore &&
                                !currentState.loadingMore
                            ) {
                                val nextEntries = currentState.allEntries
                                    .drop(currentState.entries.size)
                                    .take(pageSize)
                                stateHolder.state = currentState.copy(loadingMore = true)
                                scope.launch {
                                    val enrichedNextEntries = enrichBookmarks(
                                        nextEntries,
                                        stateHolder.currentUserName,
                                    )
                                    val latest = stateHolder.state
                                    if (latest is MyBookmarksState.Loaded) {
                                        stateHolder.state = latest.copy(
                                            entries = latest.entries + enrichedNextEntries,
                                            hasMore = latest.entries.size + enrichedNextEntries.size <
                                                latest.allEntries.size,
                                            loadingMore = false,
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
        label: String,
        selected: Boolean,
        enabled: Boolean = true,
        hasBadge: Boolean = false,
        onClick: () -> Unit,
) {
        NavigationDrawerItem(
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (hasBadge) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                        )
                    }
                }
            },
            selected = selected,
            onClick = if (enabled) onClick else ({}),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 1.dp)
                .height(36.dp),
            colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                unselectedTextColor = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
}

@Composable
private fun CategoryTabs(
    selectedCategory: EntryCategory,
    onCategorySelected: (EntryCategory) -> Unit,
) {
    val scrollState = rememberScrollState()
    val repeatedCategories = List(3) { entryCategories }.flatten()
    val middleBlockStart = entryCategories.size
    var selectedTabIndex by remember {
        mutableStateOf(middleBlockStart + entryCategories.indexOf(selectedCategory))
    }
    fun tabWidth(category: EntryCategory): Dp =
        if (category.label == "アニメとゲーム") 176.dp else 112.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ),
    ) {
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(selectedCategory, maxWidth) {
            val selectedIndex = if (
                selectedTabIndex in repeatedCategories.indices &&
                repeatedCategories[selectedTabIndex] == selectedCategory
            ) {
                selectedTabIndex
            } else {
                with(density) {
                    val viewportCenter = scrollState.value + maxWidth.toPx() / 2f
                    repeatedCategories.indices
                        .filter { repeatedCategories[it] == selectedCategory }
                        .minByOrNull { index ->
                            val precedingWidth = repeatedCategories
                                .take(index)
                                .sumOf { tabWidth(it).toPx().toDouble() }
                            kotlin.math.abs(
                                precedingWidth + tabWidth(selectedCategory).toPx() / 2f -
                                    viewportCenter,
                            )
                        }
                        ?: (middleBlockStart + entryCategories.indexOf(selectedCategory))
                }
            }
            selectedTabIndex = selectedIndex
            val target = with(density) {
                val precedingWidth = repeatedCategories.take(selectedIndex)
                    .sumOf { tabWidth(it).toPx().toDouble() }
                    .toFloat()
                val selectedWidth = tabWidth(selectedCategory).toPx()
                (precedingWidth + selectedWidth / 2f - maxWidth.toPx() / 2f + 8.dp.toPx())
                    .toInt()
            }
            scope.launch {
                scrollState.animateScrollTo(
                    target.coerceIn(0, scrollState.maxValue),
                    tween(650, easing = FastOutSlowInEasing),
                )
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
        ) {
            repeatedCategories.forEachIndexed { index, category ->
                Column(
                    modifier = Modifier
                        .width(tabWidth(category))
                        .clickable {
                            selectedTabIndex = index
                            onCategorySelected(category)
                        }
                ) {
                    Text(
                        text = category.label,
                        color = if (category == selectedCategory) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (index == selectedTabIndex) 3.dp else 0.dp)
                            .background(
                                    if (index == selectedTabIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EntryWebViewScreen(
    entry: PopularEntry,
    oauthClient: HatenaOAuthClient,
    webViewStore: RetainedWebViewStore,
    onBack: () -> Unit,
    onSelectRelatedEntry: (RelatedEntry) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var selectedCommentUri by remember { mutableStateOf<String?>(null) }
    var showBookmarkEditor by remember { mutableStateOf(false) }
    var commentsState by remember { mutableStateOf<CommentsState>(CommentsState.Loading) }
    var currentEntry by remember(entry.url) { mutableStateOf(entry) }
    var loadedEntryUrl by remember { mutableStateOf<String?>(null) }
    val latestEntry by rememberUpdatedState(currentEntry)
    val scope = rememberCoroutineScope()
    val commentDisplayBatchSize = 50

    suspend fun loadComments(url: String) {
        commentsState = CommentsState.Loading
        try {
            var offset = 0
            var page = withContext(Dispatchers.IO) {
                fetchBookmarkComments(url, offset)
            }
            var accumulatedComments = page.comments
            val relatedEntries = page.relatedEntries
            offset += page.comments.size

            while (page.hasMore) {
                page = withContext(Dispatchers.IO) {
                    fetchBookmarkComments(url, offset)
                }
                accumulatedComments += page.comments
                offset += page.comments.size
                if (accumulatedComments.size >= commentDisplayBatchSize || !page.hasMore) {
                    commentsState = CommentsState.Loaded(
                        comments = accumulatedComments,
                        hasMore = page.hasMore,
                        loadingMore = page.hasMore,
                        relatedEntries = relatedEntries,
                    )
                }
            }
            commentsState = CommentsState.Loaded(
                comments = accumulatedComments,
                hasMore = false,
                loadingMore = false,
                relatedEntries = relatedEntries,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val current = commentsState
            commentsState = if (current is CommentsState.Loaded && current.comments.isNotEmpty()) {
                current.copy(loadingMore = false)
            } else {
                CommentsState.Error(exception.message ?: "コメントを取得できませんでした")
            }
        }
    }

    LaunchedEffect(currentEntry.url) {
        loadComments(currentEntry.url)
    }

    selectedCommentUri?.let { commentUri ->
        CommentWebViewScreen(
            commentUri = commentUri,
            webViewStore = webViewStore,
            onBack = { selectedCommentUri = null },
        )
        return
    }
    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentEntry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        bottomBar = {
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        }
                    },
                    enabled = canGoBack,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "前のページへ",
                        tint = if (canGoBack) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                IconButton(
                    onClick = { showBookmarkEditor = true },
                ) {
                    Text(
                        text = "B!",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showComments = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "コメント一覧",
                        tint = Color(0xFF00B8D4),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${currentEntry.bookmarkCount}",
                        color = Color(0xFFFF4D83),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(currentEntry.url))
                        Toast.makeText(context, "URLをコピーしました", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "URLをコピー",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = {
                        val targetUrl = webView?.url ?: currentEntry.url
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = "ブラウザで開く",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { context ->
                    webViewStore.obtain(context, "entry:${entry.url}").apply {
                        webView = this
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                val navigatedUrl = url ?: return
                                val pageView = view ?: return
                                val navigatedTitle = pageView.title?.takeIf { it.isNotBlank() }
                                if (latestEntry.url != navigatedUrl) {
                                    currentEntry = latestEntry.copy(
                                        url = navigatedUrl,
                                        title = navigatedTitle ?: latestEntry.title,
                                        domain = Uri.parse(navigatedUrl).host ?: navigatedUrl,
                                        bookmarkCount = 0,
                                    )
                                    scope.launch {
                                        val bookmarkCount = withContext(Dispatchers.IO) {
                                            fetchEntryBookmarkCount(navigatedUrl)
                                        }
                                        if (webView?.url == navigatedUrl && latestEntry.url == navigatedUrl) {
                                            currentEntry = latestEntry.copy(bookmarkCount = bookmarkCount)
                                        }
                                    }
                                } else if (navigatedTitle != null && navigatedTitle != latestEntry.title) {
                                    currentEntry = latestEntry.copy(title = navigatedTitle)
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                val pageUrl = view?.url ?: return
                                val pageTitle = title?.takeIf { it.isNotBlank() } ?: return
                                if (latestEntry.url == pageUrl && latestEntry.title != pageTitle) {
                                    currentEntry = latestEntry.copy(title = pageTitle)
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        loadedEntryUrl = currentEntry.url
                        loadUrl(currentEntry.url)
                    }
                },
                update = { view ->
                    webView = view
                    if (loadedEntryUrl != currentEntry.url) {
                        loadedEntryUrl = currentEntry.url
                        canGoBack = false
                        view.loadUrl(currentEntry.url)
                    }
                },
                onRelease = { view -> webView = view },
            )
        }

        if (showComments) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                CommentsScreen(
                    entry = currentEntry,
                    state = commentsState,
                    onBack = { showComments = false },
                    canPostStar = oauthClient.savedTokens() != null,
                    onCommentStarClick = {
                        selectedCommentUri = it
                        showComments = false
                    },
                    onCommentClick = {
                        selectedCommentUri = it
                        showComments = false
                    },
                    onLoadMore = {
                        val current = commentsState
                        if (current is CommentsState.Loaded && !current.loadingMore && current.hasMore) {
                            commentsState = current.copy(loadingMore = true)
                            scope.launch {
                                try {
                                    val nextPage = withContext(Dispatchers.IO) {
                                        fetchBookmarkComments(currentEntry.url, current.comments.size)
                                    }
                                    val latest = commentsState
                                    if (latest is CommentsState.Loaded) {
                                        commentsState = latest.copy(
                                            comments = latest.comments + nextPage.comments,
                                            hasMore = nextPage.hasMore,
                                            loadingMore = false,
                                        )
                                    }
                                } catch (exception: Exception) {
                                    val latest = commentsState
                                    if (latest is CommentsState.Loaded) {
                                        commentsState = latest.copy(loadingMore = false)
                                    }
                                }
                            }
                        }
                    },
                    onRetry = {
                        scope.launch { loadComments(currentEntry.url) }
                    },
                    onRelatedEntryClick = {
                        showComments = false
                        onSelectRelatedEntry(it)
                    },
                )
            }
        }

        if (showBookmarkEditor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                BookmarkEditorScreen(
                    entry = currentEntry,
                    oauthClient = oauthClient,
                    onBack = { showBookmarkEditor = false },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommentWebViewScreen(
    commentUri: String,
    webViewStore: RetainedWebViewStore,
    onBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("コメント詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
            webViewStore.obtain(context, commentUri).apply {
                    webView = this
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(commentUri)
                }
            },
            update = { view -> webView = view },
            onRelease = { view -> webView = view },
        )
    }
}

private fun bookmarkDetailUri(commentUri: String): String? {
    val match = Regex(
        "^https://b\\.hatena\\.ne\\.jp/([^/]+)/[^#]+#bookmark-(\\d+)$",
    ).find(commentUri) ?: return null
    return "https://b.hatena.ne.jp/entry/${match.groupValues[2]}/comment/${match.groupValues[1]}"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookmarkEditorScreen(
    entry: PopularEntry,
    oauthClient: HatenaOAuthClient,
    onBack: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf<BookmarkPostState>(BookmarkPostState.Idle) }
    var showTagSelection by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)
    LaunchedEffect(entry.url) {
        val previousComment = try {
            withContext(Dispatchers.IO) {
                oauthClient.fetchMyBookmarkComment(entry.url)
            }
        } catch (exception: Exception) {
            state = BookmarkPostState.Error(
                exception.message ?: "以前のコメントを取得できませんでした",
            )
            null
        }
        if (previousComment != null && comment.isBlank()) {
            comment = previousComment
        }
    }
    if (state is BookmarkPostState.Saved) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("ブックマークしました") },
            text = { Text("記事をブックマークに登録しました。") },
            confirmButton = {
                Button(onClick = onBack) { Text("閉じる") }
            },
        )
    }
    if (state is BookmarkPostState.Error) {
        AlertDialog(
            onDismissRequest = { state = BookmarkPostState.Idle },
            title = { Text("ブックマーク登録エラー") },
            text = { Text((state as BookmarkPostState.Error).message) },
            confirmButton = {
                Button(onClick = { state = BookmarkPostState.Idle }) { Text("閉じる") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("ブックマーク", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "閉じる")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                PopularEntryCard(entry = entry, onClick = {})
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 100) comment = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(180.dp),
                    placeholder = { Text("コメントを入力（任意）") },
                    supportingText = { Text("${comment.length} / 100") },
                )
                Text(
                    text = if (selectedTags.isEmpty()) "タグを入力" else selectedTags.joinToString("  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTagSelection = true }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                )
                Button(
                    onClick = {
                        state = BookmarkPostState.Saving
                        scope.launch {
                            state = try {
                                withContext(Dispatchers.IO) {
                                    oauthClient.addBookmark(entry.url, comment.trim(), selectedTags)
                                }
                                BookmarkPostState.Saved
                            } catch (exception: Exception) {
                                BookmarkPostState.Error(
                                    exception.message ?: "ブックマークを登録できませんでした",
                                )
                            }
                        }
                    },
                    enabled = state !is BookmarkPostState.Saving && comment.length <= 100,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(if (state is BookmarkPostState.Saving) "保存中…" else "保存")
                }
            }
        }

        if (showTagSelection) {
            TagSelectionScreen(
                oauthClient = oauthClient,
                selectedTags = selectedTags,
                onBack = { showTagSelection = false },
                onTagsChanged = { selectedTags = it.take(10) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TagSelectionScreen(
    oauthClient: HatenaOAuthClient,
    selectedTags: List<String>,
    onBack: () -> Unit,
    onTagsChanged: (List<String>) -> Unit,
) {
    var tags by remember { mutableStateOf<List<HatenaTag>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var customTag by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadTags() {
        scope.launch {
            try {
                tags = withContext(Dispatchers.IO) { oauthClient.fetchMyTags() }
            } catch (exception: Exception) {
                error = exception.message ?: "タグを取得できませんでした"
            }
        }
    }

    LaunchedEffect(Unit) { loadTags() }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("タグを入力", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customTag,
                    onValueChange = { customTag = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("タグを入力") },
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val tag = customTag.trim()
                        if (tag.isNotBlank() && tag !in selectedTags && selectedTags.size < 10) {
                            onTagsChanged(selectedTags + tag)
                            customTag = ""
                        }
                    },
                    enabled = customTag.isNotBlank() && selectedTags.size < 10,
                ) {
                    Text("追加")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "選択中 (${selectedTags.size}/10)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedTags.forEach { tag ->
                    Button(onClick = { onTagsChanged(selectedTags - tag) }) {
                        Text(tag)
                    }
                }
            }
            error?.let {
                ErrorContent(
                    message = it,
                    contentPadding = PaddingValues(16.dp),
                    onRetry = {
                        error = null
                        loadTags()
                    },
                )
            } ?: tags?.let { availableTags ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = "おすすめタグ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(availableTags.take(5), key = { "recommended-${it.name}" }) { tag ->
                        TagChoiceRow(tag, selectedTags, onTagsChanged)
                    }
                    item {
                        Text(
                            text = "すべてのタグ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    items(availableTags, key = { it.name }) { tag ->
                        TagChoiceRow(tag, selectedTags, onTagsChanged)
                    }
                }
            } ?: LoadingContent(PaddingValues(16.dp))
        }
    }
}

@Composable
private fun TagChoiceRow(
    tag: HatenaTag,
    selectedTags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
) {
    val selected = tag.name in selectedTags
    Button(
        onClick = {
            onTagsChanged(
                if (selected) selectedTags - tag.name
                else if (selectedTags.size < 10) selectedTags + tag.name else selectedTags,
            )
        },
        enabled = selected || selectedTags.size < 10,
    ) {
        Text("${tag.name} (${tag.count})")
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommentsScreen(
    entry: PopularEntry,
    state: CommentsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRelatedEntryClick: (RelatedEntry) -> Unit,
    canPostStar: Boolean,
    onCommentStarClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = entry.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    listOf("ダイジェスト", "コメント").forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = if (selectedTab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                                .padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        CommentsContent(
            state = state,
            contentPadding = innerPadding,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onRelatedEntryClick = onRelatedEntryClick,
            canPostStar = canPostStar,
            onCommentStarClick = onCommentStarClick,
            onCommentClick = onCommentClick,
            ranked = selectedTab == 0,
        )
    }
}

@Composable
private fun CommentsContent(
    state: CommentsState,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRelatedEntryClick: (RelatedEntry) -> Unit,
    canPostStar: Boolean,
    onCommentStarClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    ranked: Boolean,
) {
    when (state) {
        CommentsState.Loading -> LoadingContent(contentPadding)
        is CommentsState.Error -> ErrorContent(state.message, contentPadding, onRetry)
        is CommentsState.Loaded -> {
            if (state.comments.isEmpty()) {
                ErrorContent("公開コメントがありません", contentPadding, onRetry)
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, state.comments.size, state.hasMore) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    }.collectLatest { lastVisibleIndex ->
                        if (lastVisibleIndex >= state.comments.size - 3 && state.hasMore) {
                            onLoadMore()
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                      /*
                        Text(
                            text = "ブックマークユーザー",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp),
                        )
                       */
                    }
                    val comments = if (ranked) {
                        state.comments
                            .filter { it.stars >= 1 }
                            .sortedWith(
                                compareByDescending<BookmarkComment> { it.stars }
                                    .thenBy { it.timestamp },
                            )
                    } else {
                        state.comments
                    }
                    items(comments) { comment ->
                        CommentItem(
                            comment = comment,
                            canPostStar = canPostStar,
                            onStarClick = { onCommentStarClick(comment.commentUri) },
                            onClick = { onCommentClick(comment.detailUri) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    if (ranked && state.relatedEntries.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                            ) {
                                Text(
                                    text = "あわせて読みたい",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(state.relatedEntries) { related ->
                            RelatedEntryItem(
                                related = related,
                                onClick = { onRelatedEntryClick(related) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedEntryItem(
    related: RelatedEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = related.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${related.bookmarkCount} users",
                    color = Color(0xFFFF4D83),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun CommentItem(
    comment: BookmarkComment,
    canPostStar: Boolean,
    onStarClick: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = comment.userName,
            color = Color(0xFF00A8D0),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comment.text.ifBlank { "（コメントなし）" },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = comment.timestamp, color = Color(0xFF91A0A3))
            if (canPostStar) {
                Text(
                    text = "☆",
                    color = Color(0xFF91A0A3),
                    modifier = Modifier.clickable(onClick = onStarClick),
                )
            }
            if (comment.stars > 0) {
                Text(
                    text = "★ ${comment.stars}",
                    color = Color(0xFFFFD21C),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "記事を読み込んでいます")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = "再読み込み")
        }
    }
}

@Composable
private fun PopularEntryCard(
    entry: PopularEntry,
    isRead: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = if (isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.bookmarkCount} users",
                        color = Color(0xFFFF4D83),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = entry.domain,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val thumbnailModifier = Modifier
                .padding(start = 12.dp)
                .size(76.dp)
                .clip(RoundedCornerShape(6.dp))

            if (!entry.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = null,
                    modifier = thumbnailModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = thumbnailModifier
                        .background(Color(0xFF1E282B)),
                )
            }
        }
    }
}

@Composable
private fun HatenaBookmarkTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                background = Color(0xFF07181D),
                surface = Color(0xFF07181D),
                onBackground = Color(0xFFE6ECEC),
                onSurface = Color(0xFFE6ECEC),
                primary = Color(0xFF00B8D4),
            )
        } else {
            lightColorScheme(
                background = Color(0xFFF7FAFA),
                surface = Color.White,
                onBackground = Color(0xFF172A2F),
                onSurface = Color(0xFF172A2F),
                primary = Color(0xFF008A9A),
            )
        },
        content = content,
    )
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun PopularEntriesPreview() {
    HatenaBookmarkTheme {
        LoadingContent(PaddingValues())
    }
}
