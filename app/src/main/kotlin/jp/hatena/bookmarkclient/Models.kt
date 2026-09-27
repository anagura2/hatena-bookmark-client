package jp.hatena.bookmarkclient

internal data class PopularEntry(
    val title: String,
    val url: String,
    val domain: String,
    val bookmarkCount: Int,
    val commentCount: Int,
    val imageUrl: String?,
    val comment: String = "",
)

internal data class BookmarkComment(
    val userName: String,
    val text: String,
    val timestamp: String,
    val stars: Int,
    val commentUri: String,
)

internal data class RelatedEntry(
    val title: String,
    val entryUrl: String,
    val articleUrl: String,
    val bookmarkCount: Int,
)

internal data class BookmarkCommentPage(
    val comments: List<BookmarkComment>,
    val hasMore: Boolean,
    val relatedEntries: List<RelatedEntry> = emptyList(),
)

internal data class BookmarkCommentTarget(
    val userName: String,
    val text: String,
    val timestamp: String,
    val commentUri: String,
)

internal data class MyBookmarkEntry(
    val url: String,
    val title: String,
    val comment: String,
    val createdAt: String,
    val bookmarkCount: Int,
    val starCount: Int = 0,
)

internal data class HatenaTag(
    val name: String,
    val count: Int,
)

internal sealed interface BookmarkPostState {
    data object Idle : BookmarkPostState
    data object Saving : BookmarkPostState
    data object Saved : BookmarkPostState
    data class Error(val message: String) : BookmarkPostState
}

internal sealed interface MyBookmarksState {
    data object Loading : MyBookmarksState
    data class Loaded(
        val entries: List<MyBookmarkEntry>,
        val allEntries: List<MyBookmarkEntry>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : MyBookmarksState
    data class Error(val message: String) : MyBookmarksState
}

internal sealed interface CommentsState {
    data object Loading : CommentsState
    data class Loaded(
        val comments: List<BookmarkComment>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val relatedEntries: List<RelatedEntry> = emptyList(),
    ) : CommentsState
    data class Error(val message: String) : CommentsState
}

internal sealed interface EntriesState {
    data object Loading : EntriesState
    data class Loaded(val entries: List<PopularEntry>) : EntriesState
    data class Error(val message: String) : EntriesState
}

internal data class EntryCategory(
    val label: String,
    val feedUrl: String,
)

internal val entryCategories = listOf(
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
