package jp.hatena.bookmarkclient

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import android.util.Xml
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import org.xmlpull.v1.XmlPullParser

private data class PopularEntry(
    val title: String,
    val url: String,
    val domain: String,
    val bookmarkCount: Int,
    val commentCount: Int,
    val imageUrl: String?,
    val comment: String = "",
)

private data class BookmarkComment(
    val userName: String,
    val text: String,
    val timestamp: String,
    val stars: Int,
    val commentUri: String,
)

private data class RelatedEntry(
    val title: String,
    val entryUrl: String,
    val articleUrl: String,
    val bookmarkCount: Int,
)

private data class BookmarkCommentPage(
    val comments: List<BookmarkComment>,
    val hasMore: Boolean,
    val relatedEntries: List<RelatedEntry> = emptyList(),
)

private data class BookmarkCommentTarget(
    val userName: String,
    val text: String,
    val timestamp: String,
    val commentUri: String,
)

private data class MyBookmarkEntry(
    val url: String,
    val title: String,
    val comment: String,
    val createdAt: String,
    val bookmarkCount: Int,
    val starCount: Int = 0,
)

private sealed interface BookmarkPostState {
    data object Idle : BookmarkPostState
    data object Saving : BookmarkPostState
    data object Saved : BookmarkPostState
    data class Error(val message: String) : BookmarkPostState
}

private sealed interface MyBookmarksState {
    data object Loading : MyBookmarksState
    data class Loaded(
        val entries: List<MyBookmarkEntry>,
        val allEntries: List<MyBookmarkEntry>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : MyBookmarksState
    data class Error(val message: String) : MyBookmarksState
}

private sealed interface CommentsState {
    data object Loading : CommentsState
    data class Loaded(
        val comments: List<BookmarkComment>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val relatedEntries: List<RelatedEntry> = emptyList(),
    ) : CommentsState
    data class Error(val message: String) : CommentsState
}

private sealed interface EntriesState {
    data object Loading : EntriesState
    data class Loaded(val entries: List<PopularEntry>) : EntriesState
    data class Error(val message: String) : EntriesState
}

private data class EntryCategory(
    val label: String,
    val feedUrl: String,
)

private val entryCategories = listOf(
    EntryCategory("おすすめ", "https://b.hatena.ne.jp/hotentry.rss"),
    EntryCategory("新着", "https://b.hatena.ne.jp/entrylist/all.rss?sort=new"),
    EntryCategory("総合", "https://b.hatena.ne.jp/hotentry/all.rss"),
    EntryCategory("一般", "https://b.hatena.ne.jp/hotentry/general.rss"),
    EntryCategory("テクノロジー", "https://b.hatena.ne.jp/hotentry/it.rss"),
    EntryCategory("世の中", "https://b.hatena.ne.jp/hotentry/social.rss"),
    EntryCategory("政治と経済", "https://b.hatena.ne.jp/hotentry/economics.rss"),
    EntryCategory("暮らし", "https://b.hatena.ne.jp/hotentry/life.rss"),
    EntryCategory("学び", "https://b.hatena.ne.jp/hotentry/knowledge.rss"),
    EntryCategory("おもしろ", "https://b.hatena.ne.jp/hotentry/fun.rss"),
    EntryCategory("エンタメ", "https://b.hatena.ne.jp/hotentry/entertainment.rss"),
    EntryCategory("アニメとゲーム", "https://b.hatena.ne.jp/hotentry/game.rss"),
)

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
                PopularEntriesScreen(
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
) {
    val scope = rememberCoroutineScope()
    val categoryStates = remember { mutableStateMapOf<String, EntriesState>() }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    var selectedEntry by remember { mutableStateOf<PopularEntry?>(null) }
    var selectedCategory by remember { mutableStateOf(entryCategories.first()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showMyBookmarks by remember { mutableStateOf(false) }

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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(color = Color(0xFF294047))
                    DrawerItem(
                        label = "ホーム",
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                    )
                    DrawerItem("For You", false) {}
                    DrawerItem("お気に入り", false) {}
                    DrawerItem("関心ワード", false) {}
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
                    DrawerItem("設定", false) {}
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
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { it / 3 },
                    ) + fadeIn(animationSpec = tween(300))).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { -it / 3 },
                        ) + fadeOut(animationSpec = tween(200)),
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
                                        selectedEntry = entry
                                    },
                                )
                            }
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
                    onClick = { showMyBookmarks = true },
                    modifier = Modifier.padding(end = 20.dp, bottom = 20.dp),
                    containerColor = Color(0xFF00B8C8),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "ブックマーク")
                }
            }

        }
    }

        // Main content container
        Box(modifier = Modifier.fillMaxSize()) {
            // EntryWebViewScreen: always present when an entry is selected
            selectedEntry?.let { entry ->
                EntryWebViewScreen(
                    entry = entry,
                    oauthClient = oauthClient,
                    onBack = { selectedEntry = null }
                )
            }

            // Overlay MyBookmarksScreen on top when needed
            if (showMyBookmarks) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    MyBookmarksScreen(
                        oauthClient = oauthClient,
                        onBack = { showMyBookmarks = false },
                        onSelectEntry = { selectedEntry = it }
                    )
                }
            }
        }

    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MyBookmarksScreen(
    oauthClient: HatenaOAuthClient,
    onBack: () -> Unit,
    onSelectEntry: (PopularEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MyBookmarksState>(MyBookmarksState.Loading) }
    val pageSize = 50
    BackHandler(onBack = onBack)

    suspend fun enrichBookmarks(bookmarks: List<MyBookmarkEntry>): List<MyBookmarkEntry> =
        withContext(Dispatchers.IO) {
            bookmarks.map { bookmark ->
                async {
                    bookmark.copy(
                        bookmarkCount = fetchEntryBookmarkCount(bookmark.url),
                        starCount = fetchMyBookmarkStarCount(bookmark),
                    )
                }
            }.awaitAll()
        }

    fun loadBookmarks() {
        state = MyBookmarksState.Loading
        scope.launch {
            state = try {
                val json = withContext(Dispatchers.IO) { oauthClient.fetchMyBookmarks() }
                val bookmarks = parseMyBookmarks(json)
                val firstPage = enrichBookmarks(bookmarks.take(pageSize))
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

    LaunchedEffect(Unit) { loadBookmarks() }

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
        when (val currentState = state) {
            MyBookmarksState.Loading -> LoadingContent(padding)
            is MyBookmarksState.Error -> ErrorContent(
                message = currentState.message,
                contentPadding = padding,
                onRetry = ::loadBookmarks,
            )
            is MyBookmarksState.Loaded -> {
                if (currentState.entries.isEmpty()) {
                    ErrorContent(
                        message = "ブックマークがありません",
                        contentPadding = padding,
                        onRetry = ::loadBookmarks,
                    )
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(currentState.entries, key = { it.url }) { bookmark ->
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
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(text = bookmark.comment, color = Color(0xFFB8C5C7))
                                    if (bookmark.starCount > 0) {
                                        Text(
                                            text = "★ ${bookmark.starCount}",
                                            color = Color(0xFFFFD21C),
                                            fontWeight = FontWeight.Medium,
                                        )
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
                                state = currentState.copy(loadingMore = true)
                                scope.launch {
                                    val enrichedNextEntries = enrichBookmarks(nextEntries)
                                    val latest = state
                                    if (latest is MyBookmarksState.Loaded) {
                                        state = latest.copy(
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

@Composable
private fun DrawerItem(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
) {
        NavigationDrawerItem(
            label = {
                Text(
                    text = label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            },
            selected = selected,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
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
            val selectedIndex = entryCategories.indexOf(selectedCategory)
            val target = with(density) {
                val precedingWidth = repeatedCategories
                    .take(middleBlockStart + selectedIndex)
                    .sumOf { tabWidth(it).toPx().toDouble() }
                    .toFloat()
                val selectedWidth = tabWidth(selectedCategory).toPx()
                (precedingWidth + selectedWidth / 2f - maxWidth.toPx() / 2f + 8.dp.toPx())
                    .toInt()
            }
            scope.launch {
                scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue), tween(350))
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
                        .clickable { onCategorySelected(category) }
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
                            .padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (category == selectedCategory) 3.dp else 0.dp)
                            .background(
                                if (category == selectedCategory) {
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
    onBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var selectedCommentUri by remember { mutableStateOf<String?>(null) }
    var showBookmarkEditor by remember { mutableStateOf(false) }
    var commentsState by remember { mutableStateOf<CommentsState>(CommentsState.Loading) }
    val scope = rememberCoroutineScope()

    fun loadCommentsInBackground() {
        scope.launch {
            commentsState = CommentsState.Loading
            try {
                var offset = 0
                var page = withContext(Dispatchers.IO) {
                    fetchBookmarkComments(entry.url, offset)
                }
                commentsState = CommentsState.Loaded(
                    comments = page.comments,
                    hasMore = page.hasMore,
                    loadingMore = page.hasMore,
                    relatedEntries = page.relatedEntries,
                )
                offset += page.comments.size

                while (page.hasMore) {
                    page = withContext(Dispatchers.IO) {
                        fetchBookmarkComments(entry.url, offset)
                    }
                    val current = commentsState
                    if (current !is CommentsState.Loaded) return@launch
                    commentsState = current.copy(
                        comments = current.comments + page.comments,
                        hasMore = page.hasMore,
                        loadingMore = page.hasMore,
                    )
                    offset += page.comments.size
                }
                val current = commentsState
                if (current is CommentsState.Loaded) {
                    commentsState = current.copy(loadingMore = false)
                }
            } catch (exception: Exception) {
                val current = commentsState
                commentsState = if (current is CommentsState.Loaded && current.comments.isNotEmpty()) {
                    current.copy(loadingMore = false)
                } else {
                    CommentsState.Error(exception.message ?: "コメントを取得できませんでした")
                }
            }
        }
    }

    LaunchedEffect(entry.url) {
        loadCommentsInBackground()
    }

    if (showComments) {
        CommentsScreen(
            entry = entry,
            state = commentsState,
            onBack = { showComments = false },
            canPostStar = oauthClient.savedTokens() != null,
            onCommentStarClick = {
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
                                fetchBookmarkComments(entry.url, current.comments.size)
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
                loadCommentsInBackground()
            },
        )
        return
    }
    selectedCommentUri?.let { commentUri ->
        CommentWebViewScreen(
            commentUri = commentUri,
            onBack = { selectedCommentUri = null },
        )
        return
    }
    if (showBookmarkEditor) {
        BookmarkEditorScreen(
            entry = entry,
            oauthClient = oauthClient,
            onBack = { showBookmarkEditor = false },
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

    Scaffold(
        topBar = {
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
                        text = "${entry.bookmarkCount}",
                        color = Color(0xFFFF4D83),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(entry.url))
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
                        val targetUrl = webView?.url ?: entry.url
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
                WebView(context).apply {
                    webView = this
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() == true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    loadUrl(entry.url)
                }
            },
            update = { view -> webView = view },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
                webView = null
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommentWebViewScreen(
    commentUri: String,
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
                title = { Text("コメントのスター") },
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
                WebView(context).apply {
                    webView = this
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(commentUri)
                }
            },
            update = { view -> webView = view },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
                webView = null
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookmarkEditorScreen(
    entry: PopularEntry,
    oauthClient: HatenaOAuthClient,
    onBack: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<BookmarkPostState>(BookmarkPostState.Idle) }
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
        },
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
            Button(
                onClick = {
                    state = BookmarkPostState.Saving
                    scope.launch {
                        state = try {
                            withContext(Dispatchers.IO) {
                                oauthClient.addBookmark(entry.url, comment.trim())
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
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommentsScreen(
    entry: PopularEntry,
    state: CommentsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    canPostStar: Boolean,
    onCommentStarClick: (String) -> Unit,
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
            canPostStar = canPostStar,
            onCommentStarClick = onCommentStarClick,
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
    canPostStar: Boolean,
    onCommentStarClick: (String) -> Unit,
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
                            RelatedEntryItem(related = related)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedEntryItem(related: RelatedEntry) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(related.articleUrl)))
            } catch (e: Exception) {
                // ignore
            }
        },
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

private fun fetchPopularEntries(feedUrl: String): List<PopularEntry> {
    val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/rss+xml, application/xml")
        setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
    }

    return try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("APIエラー: HTTP ${connection.responseCode}")
        }
        val xml = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        parsePopularEntries(xml)
    } finally {
        connection.disconnect()
    }
}

private fun parseMyBookmarks(json: String): List<MyBookmarkEntry> {
    val trimmed = json.trim()
    if (trimmed.startsWith("<?xml") || trimmed.startsWith("<rss") || trimmed.startsWith("<feed")) {
        return parsePopularEntries(trimmed).map { entry ->
            MyBookmarkEntry(
                url = entry.url,
                title = entry.title,
                comment = entry.comment,
                createdAt = "",
                bookmarkCount = entry.bookmarkCount,
            )
        }
    }
    val items = when {
        trimmed.startsWith("[") -> JSONArray(trimmed)
        trimmed.startsWith("{") -> {
            val root = JSONObject(trimmed)
            root.optJSONArray("bookmarks")
                ?: root.optJSONArray("entries")
                ?: JSONArray().apply { put(root) }
        }
        else -> error("ブックマークAPIのレスポンス形式が不正です")
    }
    return buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (url.isBlank()) continue
            add(
                MyBookmarkEntry(
                    url = url,
                    title = item.optString("title").ifBlank { url },
                    comment = item.optString("comment").trim(),
                    createdAt = item.optString("created_at").ifBlank {
                        item.optString("createdAt")
                    },
                    bookmarkCount = item.optInt("count"),
                ),
            )
        }
    }
}

private suspend fun fetchBookmarkComments(
    entryUrl: String,
    offset: Int,
    pageSize: Int = 10,
): BookmarkCommentPage {
    val endpoint = "https://b.hatena.ne.jp/entry/json/?url=" +
        Uri.encode(entryUrl)
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
    }

    return try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("コメントAPIエラー: HTTP ${connection.responseCode}")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val entryId = root.optString("eid")
        val bookmarks = root.optJSONArray("bookmarks") ?: return BookmarkCommentPage(emptyList(), false)
        val targets = buildList {
            for (index in 0 until bookmarks.length()) {
                val item = bookmarks.optJSONObject(index) ?: continue
                val text = item.optString("comment").trim()
                if (text.isBlank()) continue
                val timestamp = item.optString("timestamp")
                val date = timestamp.replace("/", "").take(8)
                val userName = item.optString("user")
                if (entryId.isBlank() || userName.isBlank() || date.length != 8) continue
                add(
                    BookmarkCommentTarget(
                        userName = userName,
                        text = text,
                        timestamp = timestamp,
                        commentUri = "https://b.hatena.ne.jp/$userName/$date#bookmark-$entryId",
                    ),
                )
            }
        }
        val page = targets.drop(offset).take(pageSize)
        val comments = coroutineScope {
            page.map { target ->
                async(Dispatchers.IO) {
                    BookmarkComment(
                        userName = target.userName,
                        text = target.text,
                        timestamp = target.timestamp,
                        stars = fetchCommentStarCount(target.commentUri),
                        commentUri = target.commentUri,
                    )
                }
            }.awaitAll()
        }
        val relatedEntries = if (offset == 0) {
            val relatedArray = root.optJSONArray("related") ?: JSONArray()
            buildList {
                for (i in 0 until relatedArray.length()) {
                    val item = relatedArray.optJSONObject(i) ?: continue
                    val relatedEntryUrl = item.optString("entry_url").takeIf { it.isNotBlank() } ?: continue
                    val articleUrl = relatedEntryUrl
                        .removePrefix("https://b.hatena.ne.jp/entry/s/")
                        .removePrefix("https://b.hatena.ne.jp/entry/")
                        .let { if (it.startsWith("http")) it else "https://$it" }
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    add(
                        RelatedEntry(
                            title = title,
                            entryUrl = relatedEntryUrl,
                            articleUrl = articleUrl,
                            bookmarkCount = item.optInt("count"),
                        )
                    )
                }
            }
        } else emptyList()
        BookmarkCommentPage(
            comments = comments,
            hasMore = offset + page.size < targets.size,
            relatedEntries = relatedEntries,
        )
    } finally {
        connection.disconnect()
    }
}

private fun fetchCommentStarCount(commentUri: String): Int {
    val endpoint = "https://s.hatena.com/entry.json?uri=" + Uri.encode(commentUri)
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
    }
    return try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("スターAPIエラー: HTTP ${connection.responseCode}")
        }
        val entries = JSONObject(
            connection.inputStream.bufferedReader().use { it.readText() },
        ).optJSONArray("entries") ?: return 0
        entries.optJSONObject(0)?.optJSONArray("stars")?.length() ?: 0
    } finally {
        connection.disconnect()
    }
}

private fun fetchMyBookmarkStarCount(bookmark: MyBookmarkEntry): Int {
    if (bookmark.comment.isBlank()) return 0
    val endpoint = "https://b.hatena.ne.jp/entry/jsonlite/?url=" + Uri.encode(bookmark.url)
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
    }
    return try {
        if (connection.responseCode !in 200..299) return 0
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val entryId = root.optString("eid")
        val bookmarks = root.optJSONArray("bookmarks") ?: return 0
        for (index in 0 until bookmarks.length()) {
            val item = bookmarks.optJSONObject(index) ?: continue
            if (item.optString("comment").trim() != bookmark.comment) continue
            val user = item.optString("user")
            val date = item.optString("timestamp").replace("/", "").take(8)
            if (entryId.isBlank() || user.isBlank() || date.length != 8) return 0
            return fetchCommentStarCount(
                "https://b.hatena.ne.jp/$user/$date#bookmark-$entryId",
            )
        }
        0
    } finally {
        connection.disconnect()
    }
}

private fun fetchEntryBookmarkCount(entryUrl: String): Int {
    val endpoint = "https://b.hatena.ne.jp/entry/jsonlite/?url=" + Uri.encode(entryUrl)
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
    }
    return try {
        if (connection.responseCode !in 200..299) return 0
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(json).optInt("count")
    } finally {
        connection.disconnect()
    }
}

private fun parsePopularEntries(xml: String): List<PopularEntry> {
    val parser = Xml.newPullParser().apply {
        setInput(xml.reader())
    }
    val entries = mutableListOf<PopularEntry>()
    var eventType = parser.eventType
    var insideItem = false
    var title = ""
    var url = ""
    var bookmarkCount = 0
    var imageUrl: String? = null
    var description = ""

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "item" -> {
                    insideItem = true
                    title = ""
                    url = ""
                    bookmarkCount = 0
                    imageUrl = null
                    description = ""
                }
                "title" -> if (insideItem) title = parser.nextText()
                "link" -> if (insideItem) url = parser.nextText()
                "bookmarkcount" -> if (insideItem) {
                    bookmarkCount = parser.nextText().toIntOrNull() ?: 0
                }
                "imageurl", "hatena:imageurl", "thumbnail" -> if (insideItem) {
                    val resource = parser.getAttributeValue(null, "rdf:resource")
                        ?: parser.getAttributeValue(null, "resource")
                    imageUrl = resource ?: parser.nextText().trim().takeIf { it.isNotBlank() }
                }
                "description" -> if (insideItem) description = parser.nextText()
            }
            XmlPullParser.END_TAG -> if (parser.name == "item" && insideItem) {
                if (url.isNotBlank()) {
                    entries += PopularEntry(
                        title = title.ifBlank { url },
                        url = url,
                        domain = Uri.parse(url).host ?: url,
                        bookmarkCount = bookmarkCount,
                        commentCount = 0,
                        imageUrl = imageUrl,
                        comment = description,
                    )
                }
                insideItem = false
            }
        }
        eventType = parser.next()
    }
    return entries
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
