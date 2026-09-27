package jp.hatena.bookmarkclient

import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

internal fun fetchPopularEntries(feedUrl: String): List<PopularEntry> {
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

internal fun parseMyBookmarks(json: String): List<MyBookmarkEntry> {
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

internal suspend fun fetchBookmarkComments(
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

internal fun fetchCommentStarCount(commentUri: String): Int {
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

internal fun fetchMyBookmarkStarCount(bookmark: MyBookmarkEntry): Int {
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

internal fun fetchEntryBookmarkCount(entryUrl: String): Int {
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

internal fun parsePopularEntries(xml: String): List<PopularEntry> {
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
