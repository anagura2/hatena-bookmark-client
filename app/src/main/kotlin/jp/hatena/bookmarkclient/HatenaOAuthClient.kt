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
        return fetchPublicFeed(feedUrl)
    }

    fun addBookmark(url: String, comment: String) {
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
        request(
            method = "POST",
            endpoint = MyBookmarkEndpoint,
            parameters = parameters,
            tokenSecret = tokens.tokenSecret,
        )
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
    ): String {
        val encodedParameters = parameters.entries
            .sortedWith(compareBy({ percentEncode(it.key) }, { percentEncode(it.value) }))
            .joinToString("&") { "${percentEncode(it.key)}=${percentEncode(it.value)}" }
        val encodedBody = parameters.entries
            .filterNot { it.key.startsWith("oauth_") }
            .sortedWith(compareBy({ percentEncode(it.key) }, { percentEncode(it.value) }))
            .joinToString("&") { "${percentEncode(it.key)}=${percentEncode(it.value)}" }
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
        val authorization = parameters.entries
            .filter { it.key.startsWith("oauth_") }
            .map { "${percentEncode(it.key)}=\"${percentEncode(it.value)}\"" }
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
    }
}
