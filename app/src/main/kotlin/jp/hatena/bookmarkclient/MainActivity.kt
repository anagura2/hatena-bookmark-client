package jp.hatena.bookmarkclient

import android.os.Bundle
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Xml
import org.json.JSONObject
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
)

private data class BookmarkComment(
    val userName: String,
    val text: String,
    val timestamp: String,
    val stars: Int,
)

private sealed interface CommentsState {
    data object Loading : CommentsState
    data class Loaded(val comments: List<BookmarkComment>) : CommentsState
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
    EntryCategory("新着", "https://b.hatena.ne.jp/entrylist/rss?sort=new"),
    EntryCategory("総合", "https://b.hatena.ne.jp/hotentry/all.rss"),
    EntryCategory("テクノロジー", "https://b.hatena.ne.jp/hotentry/it.rss"),
    EntryCategory("世の中", "https://b.hatena.ne.jp/hotentry/social.rss"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HatenaBookmarkTheme {
                PopularEntriesScreen()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PopularEntriesScreen() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<EntriesState>(EntriesState.Loading) }
    var selectedEntry by remember { mutableStateOf<PopularEntry?>(null) }
    var selectedCategory by remember { mutableStateOf(entryCategories.first()) }

    fun loadEntries(category: EntryCategory = selectedCategory) {
        state = EntriesState.Loading
        scope.launch {
            state = try {
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
        loadEntries(category)
    }

    LaunchedEffect(Unit) {
        loadEntries()
    }

    if (selectedEntry != null) {
        EntryWebViewScreen(
            entry = selectedEntry!!,
            onBack = { selectedEntry = null },
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF07181D),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = "ホーム", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.Menu, contentDescription = "メニュー")
                        }
                    },
                    actions = {
                        IconButton(onClick = ::loadEntries) {
                            Icon(Icons.Outlined.Search, contentDescription = "検索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF23363A),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
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
                                totalDrag <= -80f && currentIndex < entryCategories.lastIndex ->
                                    selectCategory(entryCategories[currentIndex + 1])
                                totalDrag >= 80f && currentIndex > 0 ->
                                    selectCategory(entryCategories[currentIndex - 1])
                            }
                        },
                    )
                },
        ) {
            when (val currentState = state) {
                EntriesState.Loading -> LoadingContent(innerPadding)
                is EntriesState.Error -> ErrorContent(
                    message = currentState.message,
                    contentPadding = innerPadding,
                    onRetry = ::loadEntries,
                )
                is EntriesState.Loaded -> {
                    if (currentState.entries.isEmpty()) {
                        ErrorContent(
                            message = "表示できる記事がありません",
                            contentPadding = innerPadding,
                            onRetry = ::loadEntries,
                        )
                    } else {
                        LazyColumn(
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
                                    onClick = { selectedEntry = entry },
                                )
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                containerColor = Color(0xFF00B8C8),
                contentColor = Color.White,
            ) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = "ブックマーク")
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    selectedCategory: EntryCategory,
    onCategorySelected: (EntryCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF172A2F))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        entryCategories.forEach { category ->
            Text(
                text = category.label,
                color = if (category == selectedCategory) Color(0xFF00B8D4) else Color(0xFFB4C0C3),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EntryWebViewScreen(
    entry: PopularEntry,
    onBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showComments by remember { mutableStateOf(false) }
    var commentsState by remember { mutableStateOf<CommentsState>(CommentsState.Loading) }
    val scope = rememberCoroutineScope()

    if (showComments) {
        CommentsScreen(
            entry = entry,
            state = commentsState,
            onBack = { showComments = false },
            onRetry = {
                scope.launch {
                    commentsState = try {
                        CommentsState.Loaded(withContext(Dispatchers.IO) {
                            fetchBookmarkComments(entry.url)
                        })
                    } catch (exception: Exception) {
                        CommentsState.Error(exception.message ?: "コメントを取得できませんでした")
                    }
                }
            },
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

    LaunchedEffect(entry.url) {
        commentsState = try {
            CommentsState.Loaded(withContext(Dispatchers.IO) {
                fetchBookmarkComments(entry.url)
            })
        } catch (exception: Exception) {
            CommentsState.Error(exception.message ?: "コメントを取得できませんでした")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF172A2F)),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = {}) {
                    Text(
                        text = "B!",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = { showComments = true }) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "コメント",
                        tint = Color(0xFF00B8D4),
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
                    webViewClient = WebViewClient()
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
private fun CommentsScreen(
    entry: PopularEntry,
    state: CommentsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
                        .background(Color(0xFF172A2F)),
                ) {
                    listOf("ダイジェスト", "コメント").forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = if (selectedTab == index) Color(0xFF00B8D4) else Color(0xFF9EACAF),
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
            ranked = selectedTab == 0,
        )
    }
}

@Composable
private fun CommentsContent(
    state: CommentsState,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    ranked: Boolean,
) {
    when (state) {
        CommentsState.Loading -> LoadingContent(contentPadding)
        is CommentsState.Error -> ErrorContent(state.message, contentPadding, onRetry)
        is CommentsState.Loaded -> {
            if (state.comments.isEmpty()) {
                ErrorContent("公開コメントがありません", contentPadding, onRetry)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        Text(
                            text = "ブックマークユーザー",
                            color = Color(0xFF9EACAF),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF102328))
                                .padding(16.dp),
                        )
                    }
                    val comments = if (ranked) {
                        state.comments.sortedWith(
                            compareByDescending<BookmarkComment> { it.stars }
                                .thenBy { it.timestamp },
                        )
                    } else {
                        state.comments
                    }
                    items(comments) { comment ->
                        CommentItem(comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: BookmarkComment) {
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
            color = Color(0xFFE6ECEC),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = comment.timestamp, color = Color(0xFF91A0A3))
            if (comment.stars > 0) {
                Text(text = "☆", color = Color(0xFF91A0A3))
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
private fun PopularEntryCard(entry: PopularEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFF07181D),
        ),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = Color(0xFFE6ECEC),
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
                        color = Color(0xFF9EACAF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.imageUrl != null) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(88.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color(0xFF5C7478),
                    modifier = Modifier.padding(start = 12.dp),
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

private fun fetchBookmarkComments(entryUrl: String): List<BookmarkComment> {
    val endpoint = "https://b.hatena.ne.jp/entry/jsonlite/?url=" +
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
        val bookmarks = JSONObject(json).optJSONArray("bookmarks") ?: return emptyList()
        buildList {
            for (index in 0 until bookmarks.length()) {
                val item = bookmarks.optJSONObject(index) ?: continue
                val text = item.optString("comment").trim()
                if (text.isBlank()) continue
                add(
                    BookmarkComment(
                        userName = item.optString("user"),
                        text = text,
                        timestamp = item.optString("timestamp"),
                        stars = item.optInt("star").takeIf { it > 0 }
                            ?: item.optInt("star_count").takeIf { it > 0 }
                            ?: item.optInt("starCount").takeIf { it > 0 }
                            ?: item.optJSONArray("stars")?.length()
                            ?: 0,
                    ),
                )
            }
        }
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

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "item" -> {
                    insideItem = true
                    title = ""
                    url = ""
                    bookmarkCount = 0
                    imageUrl = null
                }
                "title" -> if (insideItem) title = parser.nextText()
                "link" -> if (insideItem) url = parser.nextText()
                "bookmarkcount" -> if (insideItem) {
                    bookmarkCount = parser.nextText().toIntOrNull() ?: 0
                }
                "thumbnail" -> if (insideItem) {
                    imageUrl = parser.getAttributeValue(null, "rdf:resource")
                        ?: parser.getAttributeValue(null, "resource")
                }
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
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF07181D),
            surface = Color(0xFF07181D),
            onBackground = Color(0xFFE6ECEC),
            onSurface = Color(0xFFE6ECEC),
        ),
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
