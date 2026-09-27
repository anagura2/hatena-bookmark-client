package jp.hatena.bookmarkclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.json.JSONArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class HatenaTokens(
    val token: String,
    val tokenSecret: String,
)

internal data class MyBookmark(
    val url: String,
    val comment: String,
    val createdAt: String,
)

internal class HatenaOAuthClient(
    context: Context,
    private val consumerKey: String,
    private val consumerSecret: String,
) {
    private val preferences = context.getSharedPreferences("hatena_oauth", Context.MODE_PRIVATE)

    fun savedTokens(): HatenaTokens? {
        val token = preferences.getString("token", null) ?: return null
        val secret = preferences.getString("token_secret", null) ?: return null
        return HatenaTokens(token, secret)
    }

    fun clearTokens() {
        preferences.edit().clear().apply()
    }

    fun fetchMyBookmarks(): String {
        val tokens = savedTokens() ?: error("はてなにログインしてください")
        val parameters = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
        )
        val profile = JSONObject(
            request(
                method = "GET",
                endpoint = HatenaProfileEndpoint,
                parameters = parameters,
                tokenSecret = tokens.tokenSecret,
            ),
        )
        val userName = profile.optString("url_name").ifBlank {
            error("はてなユーザーIDを取得できませんでした")
        }
        val feedUrl = "https://b.hatena.ne.jp/${percentEncode(userName)}/rss"
        val items = buildString {
            for (offset in 0 until 100 step 20) {
                val page = fetchPublicFeed("$feedUrl?of=$offset")
                val pageItems = Regex("(?s)<item\\b.*?</item>").findAll(page).map { it.value }.toList()
                if (pageItems.isEmpty()) break
                append(pageItems.joinToString("\n"))
                if (pageItems.size < 20) break
            }
        }
        return """
            <rss xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:dc="http://purl.org/dc/elements/1.1/"
                 xmlns:taxo="http://purl.org/rss/1.0/modules/taxonomy/"
                 xmlns:hatena="http://www.hatena.ne.jp/info/xmlns#"
                 xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:syn="http://purl.org/rss/1.0/modules/syndication/"
                 xmlns:admin="http://webns.net/mvcb/">
                <channel>$items</channel>
            </rss>
        """.trimIndent()
    }

    fun fetchMyUserName(): String {
        val tokens = savedTokens() ?: error("はてなにログインしてください")
        val parameters = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
        )
        return JSONObject(
            request(
                method = "GET",
                endpoint = HatenaProfileEndpoint,
                parameters = parameters,
                tokenSecret = tokens.tokenSecret,
            ),
        ).optString("url_name").ifBlank {
            error("はてなユーザーIDを取得できませんでした")
        }
    }

    fun fetchNotifications(): List<HatenaNotification> {
        val tokens = savedTokens() ?: error("はてなにログインしてください")
        val parameters = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
        )
        val response = request(
            method = "GET",
            endpoint = NotificationsEndpoint,
            parameters = parameters,
            tokenSecret = tokens.tokenSecret,
        )
        val trimmedResponse = response.trim()
        val items = if (trimmedResponse.startsWith("[")) {
            JSONArray(trimmedResponse)
        } else {
            val root = JSONObject(trimmedResponse)
            root.optJSONArray("notices")
                ?: root.optJSONArray("notifications")
                ?: root.optJSONArray("entries")
                ?: root.optJSONArray("items")
                ?: JSONArray().apply { put(root) }
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val users = buildList {
                    collectNotificationUsers(item, this)
                }.distinct()
                add(
                    HatenaNotification(
                        verb = item.optString("verb"),
                        subject = item.optString("subject"),
                        subjectTitle = item.optJSONObject("metadata")?.optString("subject_title").orEmpty(),
                        users = users,
                        createdAt = item.optLong("created"),
                    ),
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun collectNotificationUsers(value: Any?, users: MutableList<String>) {
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectNotificationUsers(value.opt(index), users)
                }
            }
            is JSONObject -> {
                listOf("user", "user_name", "username", "screen_name").forEach { key ->
                    value.optString(key).takeIf { it.isNotBlank() }?.let(users::add)
                }
                listOf("object", "actor", "author", "user").forEach { key ->
                    if (value.has(key)) {
                        collectNotificationUsers(value.opt(key), users)
                    }
                }
            }
        }
    }

    fun addBookmark(url: String, comment: String, tags: List<String> = emptyList()) {
        val tokens = savedTokens() ?: error("はてなにログインしてください")
        val parameters = linkedMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
            "url" to url,
            "comment" to comment,
        )
        val repeatedParameters = parameters.entries.map { it.toPair() } +
            tags.take(10).map { "tags" to it }
        request(
            method = "POST",
            endpoint = MyBookmarkEndpoint,
            parameters = repeatedParameters,
            tokenSecret = tokens.tokenSecret,
        )
    }

    fun fetchMyTags(): List<HatenaTag> {
        val tokens = savedTokens() ?: error("はてなにログインしてください")
        val parameters = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
        )
        val response = request(
            method = "GET",
            endpoint = MyTagsEndpoint,
            parameters = parameters,
            tokenSecret = tokens.tokenSecret,
        )
        val root = JSONObject(response)
        val tags = root.optJSONArray("tags") ?: JSONArray()
        return buildList {
            for (index in 0 until tags.length()) {
                val item = tags.optJSONObject(index) ?: continue
                val name = item.optString("tag").trim()
                if (name.isNotBlank()) {
                    add(HatenaTag(name = name, count = item.optInt("count")))
                }
            }
        }.sortedByDescending { it.count }
    }

    fun fetchMyBookmarkComment(url: String): String? {
        val tokens = savedTokens() ?: return null
        val parameters = linkedMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to tokens.token,
            "oauth_version" to "1.0",
            "url" to url,
        )
        val response = try {
            request(
                method = "GET",
                endpoint = MyBookmarkEndpoint,
                parameters = parameters,
                tokenSecret = tokens.tokenSecret,
            )
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("HTTP 404") == true) return null
            throw exception
        }
        val root = JSONObject(response)
        return root.optString("comment").takeIf { it.isNotBlank() }
            ?: root.optJSONObject("bookmark")?.optString("comment")
                ?.takeIf { it.isNotBlank() }
    }

    private fun fetchPublicFeed(feedUrl: String): String {
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/rss+xml, application/xml")
            setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
        }
        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (responseCode !in 200..299) error("ブックマークRSSエラー: HTTP $responseCode")
            body
        } finally {
            connection.disconnect()
        }
    }

    fun beginAuthorization(): Intent {
        require(consumerKey.isNotBlank() && consumerSecret.isNotBlank()) {
            "Consumer Key/Secretが設定されていません"
        }
        val nonce = randomNonce()
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val parameters = mapOf(
            "oauth_callback" to CallbackUri,
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to nonce,
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to timestamp,
            "oauth_version" to "1.0",
            "scope" to "read_public,write_public,read_private,write_private",
        )
        val response = request(
            method = "POST",
            endpoint = RequestTokenEndpoint,
            parameters = parameters,
            tokenSecret = "",
        )
        val values = parseForm(response)
        val token = values["oauth_token"] ?: error("Request Tokenを取得できませんでした")
        val tokenSecret = values["oauth_token_secret"] ?: error("Request Token Secretを取得できませんでした")
        preferences.edit()
            .putString("request_token", token)
            .putString("request_token_secret", tokenSecret)
            .apply()
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("$AuthorizeEndpoint?oauth_token=${percentEncode(token)}"),
        )
    }

    fun completeAuthorization(verifier: String): HatenaTokens {
        if (verifier.isBlank()) error("OAuth verifierを入力してください")
        val requestToken = preferences.getString("request_token", null)
            ?: error("Request Tokenがありません")
        val requestSecret = preferences.getString("request_token_secret", null)
            ?: error("Request Token Secretがありません")
        val parameters = mapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to randomNonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "oauth_token" to requestToken,
            "oauth_verifier" to verifier,
            "oauth_version" to "1.0",
        )
        val values = parseForm(
            request(
                method = "POST",
                endpoint = AccessTokenEndpoint,
                parameters = parameters,
                tokenSecret = requestSecret,
            ),
        )
        val tokens = HatenaTokens(
            token = values["oauth_token"] ?: error("Access Tokenを取得できませんでした"),
            tokenSecret = values["oauth_token_secret"] ?: error("Access Token Secretを取得できませんでした"),
        )
        preferences.edit()
            .remove("request_token")
            .remove("request_token_secret")
            .putString("token", tokens.token)
            .putString("token_secret", tokens.tokenSecret)
            .apply()
        return tokens
    }

    private fun request(
        method: String,
        endpoint: String,
        parameters: Map<String, String>,
        tokenSecret: String,
    ): String = request(
        method = method,
        endpoint = endpoint,
        parameters = parameters.entries.map { it.toPair() },
        tokenSecret = tokenSecret,
    )

    private fun request(
        method: String,
        endpoint: String,
        parameters: List<Pair<String, String>>,
        tokenSecret: String,
    ): String {
        val encodedParameters = parameters
            .sortedWith(compareBy({ percentEncode(it.first) }, { percentEncode(it.second) }))
            .joinToString("&") { "${percentEncode(it.first)}=${percentEncode(it.second)}" }
        val encodedBody = parameters
            .filterNot { it.first.startsWith("oauth_") }
            .sortedWith(compareBy({ percentEncode(it.first) }, { percentEncode(it.second) }))
            .joinToString("&") { "${percentEncode(it.first)}=${percentEncode(it.second)}" }
        val baseString = listOf(
            method,
            percentEncode(endpoint),
            percentEncode(encodedParameters),
        ).joinToString("&")
        val signingKey = "${percentEncode(consumerSecret)}&${percentEncode(tokenSecret)}"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val signature = Base64.encodeToString(
            mac.doFinal(baseString.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP,
        )
        val authorization = parameters
            .filter { it.first.startsWith("oauth_") }
            .map { "${percentEncode(it.first)}=\"${percentEncode(it.second)}\"" }
            .toMutableList()
            .apply { add("oauth_signature=\"${percentEncode(signature)}\"") }
            .joinToString(", ")

        val requestUrl = if (method == "GET" && encodedBody.isNotBlank()) {
            "$endpoint?$encodedBody"
        } else {
            endpoint
        }
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            setRequestProperty("Authorization", "OAuth $authorization")
            setRequestProperty("User-Agent", "HatenaBookmarkClient/0.1.0")
        }
        return try {
            if (method == "POST") {
                connection.outputStream.use { output ->
                    output.write(encodedBody.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (responseCode !in 200..299) {
                error("OAuth APIエラー: HTTP $responseCode${if (body.isNotBlank()) " ($body)" else ""}")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseForm(response: String): Map<String, String> =
        response.split("&")
            .filter { it.contains("=") }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                Uri.decode(key) to Uri.decode(value)
            }

    private fun randomNonce(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")

    private fun percentEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")

    private companion object {
        const val CallbackUri = "oob"
        const val RequestTokenEndpoint = "https://www.hatena.com/oauth/initiate"
        const val AuthorizeEndpoint = "https://www.hatena.ne.jp/oauth/authorize"
        const val AccessTokenEndpoint = "https://www.hatena.com/oauth/token"
        const val HatenaProfileEndpoint = "https://n.hatena.com/applications/my.json"
        const val MyBookmarkEndpoint = "https://bookmark.hatenaapis.com/rest/1/my/bookmark"
        const val MyTagsEndpoint = "https://bookmark.hatenaapis.com/rest/1/my/tags"
        const val NotificationsEndpoint = "https://www.hatena.ne.jp/notify/api/pull"
    }
}
