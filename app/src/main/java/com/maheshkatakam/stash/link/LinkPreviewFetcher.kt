package com.maheshkatakam.stash.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.UnsupportedMimeTypeException
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LinkPreview(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
)

object Http {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** Instagram and Facebook only show post details to link-preview robots like this one. */
    const val PREVIEW_BOT_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    /** Returns the body, or null for a non-2xx answer. Network failures throw IOException (so the caller can retry later). */
    fun getText(url: String, userAgent: String = BROWSER_UA): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            if (conn.responseCode !in 200..299) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun getJson(url: String): JSONObject? = getText(url)?.let { runCatching { JSONObject(it) }.getOrNull() }
}

/**
 * Finds a title, text and picture for a saved link.
 * X posts use the free fxtwitter API (falls back to X's own embed API), YouTube and TikTok use their
 * public oEmbed APIs, and everything else (Instagram included) reads the page's Open Graph tags.
 */
object LinkPreviewFetcher {
    private val X_STATUS = Regex("""(?:x|twitter)\.com/([^/?#]+)/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)

    suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        val host = Links.hostOf(url)
        val special = when {
            Links.isX(host) -> fetchX(url)
            host.endsWith("youtube.com") || host == "youtu.be" ->
                oEmbed("https://www.youtube.com/oembed?format=json&url=${enc(url)}")
            host.endsWith("tiktok.com") -> oEmbed("https://www.tiktok.com/oembed?url=${enc(url)}")
            else -> null
        }
        if (special != null) return@withContext special
        val metaRobotSites = host.endsWith("instagram.com") || host.endsWith("facebook.com") ||
            host.endsWith("threads.net") || host.endsWith("threads.com")
        if (metaRobotSites) {
            generic(url, Http.PREVIEW_BOT_UA)?.takeIf { it.isUseful() } ?: generic(url, Http.BROWSER_UA)?.takeIf { it.isUseful() }
        } else {
            generic(url, Http.BROWSER_UA)
        }
    }

    private fun LinkPreview.isUseful(): Boolean {
        // Instagram's login wall has the title "Instagram" and nothing else.
        val boringTitle = title == null || title.equals("Instagram", true) || title.equals("Login • Instagram", true)
        return !(boringTitle && description == null && imageUrl == null)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun generic(url: String, userAgent: String): LinkPreview? {
        val doc: Document = try {
            Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .followRedirects(true)
                .timeout(15_000)
                .maxBodySize(3_000_000)
                .get()
        } catch (e: HttpStatusException) {
            return null
        } catch (e: UnsupportedMimeTypeException) {
            return if (e.mimeType?.startsWith("image") == true) LinkPreview(null, null, url, null) else null
        }

        fun metaElement(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
            doc.select("meta").firstOrNull { el ->
                (el.attr("property").equals(key, true) || el.attr("name").equals(key, true)) &&
                    el.attr("content").isNotBlank()
            }
        }

        fun meta(vararg keys: String): String? = metaElement(*keys)?.attr("content")?.trim()

        val title = meta("og:title", "twitter:title") ?: doc.title().trim().takeIf { it.isNotEmpty() }
        val description = meta("og:description", "twitter:description", "description")
        val image = metaElement("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
            ?.let { el -> el.absUrl("content").ifBlank { el.attr("content") } }
        val site = meta("og:site_name")
        if (title == null && description == null && image == null) return null
        return LinkPreview(title?.take(300), description?.take(2000), image, site)
    }

    private fun oEmbed(endpoint: String): LinkPreview? {
        val o = Http.getJson(endpoint) ?: return null
        val title = o.optString("title").takeIf { it.isNotBlank() }
        val author = o.optString("author_name").takeIf { it.isNotBlank() }
        return LinkPreview(
            title = title ?: author,
            description = if (title != null) author else null,
            imageUrl = o.optString("thumbnail_url").takeIf { it.isNotBlank() },
            siteName = o.optString("provider_name").takeIf { it.isNotBlank() },
        )
    }

    private fun fetchX(url: String): LinkPreview? {
        val match = X_STATUS.find(url) ?: return null
        val user = match.groupValues[1]
        val id = match.groupValues[2]

        val viaFx = runCatching {
            val json = Http.getJson("https://api.fxtwitter.com/$user/status/$id")
            val tweet = json?.optJSONObject("tweet")
            if (tweet == null) {
                null
            } else {
                val author = tweet.optJSONObject("author")
                val name = author?.optString("name").orEmpty()
                val handle = author?.optString("screen_name").orEmpty()
                val media = tweet.optJSONObject("media")
                val photo = media?.optJSONArray("photos")?.optJSONObject(0)?.optString("url")
                val videoThumb = media?.optJSONArray("videos")?.optJSONObject(0)?.optString("thumbnail_url")
                val avatar = author?.optString("avatar_url")
                val image = listOf(photo, videoThumb, avatar).firstOrNull { !it.isNullOrBlank() }
                val title = when {
                    name.isNotBlank() && handle.isNotBlank() -> "$name (@$handle)"
                    name.isNotBlank() -> name
                    handle.isNotBlank() -> "@$handle"
                    else -> null
                }
                LinkPreview(title, tweet.optString("text").takeIf { it.isNotBlank() }, image, "X")
            }
        }.getOrNull()
        if (viaFx != null) return viaFx

        val o = Http.getJson("https://publish.twitter.com/oembed?omit_script=true&url=${enc(url)}") ?: return null
        val text = Jsoup.parse(o.optString("html")).select("p").text().takeIf { it.isNotBlank() }
        return LinkPreview(o.optString("author_name").takeIf { it.isNotBlank() }, text, null, "X")
    }
}
