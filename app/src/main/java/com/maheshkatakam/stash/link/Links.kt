package com.maheshkatakam.stash.link

import android.net.Uri

object Links {
    private val URL_REGEX = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val BARE_DOMAIN = Regex("""^(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)

    /** First http(s) link in a piece of shared text. */
    fun firstUrl(text: String): String? =
        URL_REGEX.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    /** Accepts a pasted link, or a typed "example.com/page" without https://. */
    fun fromTyped(text: String): String? {
        firstUrl(text)?.let { return it }
        val t = text.trim()
        return if (BARE_DOMAIN.matches(t)) "https://$t" else null
    }

    fun hostOf(url: String): String = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()

    fun isX(host: String) = host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")

    /** Removes tracking bits (utm_*, Instagram's igsh, YouTube's si, X's s/t) so saved links stay clean. */
    fun clean(url: String): String = runCatching {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return url
        val drop = mutableSetOf("igsh", "igshid", "fbclid", "gclid", "mibextid", "ref_src", "ref_url")
        if (host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("spotify.com")) drop += "si"
        if (isX(host)) {
            drop += "s"
            drop += "t"
        }
        val names = uri.queryParameterNames
        if (names.none { it in drop || it.startsWith("utm_") }) return url
        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (name in drop || name.startsWith("utm_")) continue
            for (value in uri.getQueryParameters(name)) builder.appendQueryParameter(name, value)
        }
        builder.build().toString()
    }.getOrDefault(url)

    fun sourceFor(url: String): String {
        val host = hostOf(url)
        val path = runCatching { Uri.parse(url).path.orEmpty() }.getOrDefault("")
        return when {
            host.endsWith("instagram.com") || host == "instagr.am" -> "Instagram"
            isX(host) -> "X"
            host.endsWith("youtube.com") || host == "youtu.be" -> "YouTube"
            host.endsWith("tiktok.com") -> "TikTok"
            host.endsWith("reddit.com") || host == "redd.it" -> "Reddit"
            host.endsWith("linkedin.com") || host == "lnkd.in" -> "LinkedIn"
            host.endsWith("facebook.com") || host == "fb.watch" || host == "fb.com" -> "Facebook"
            host.endsWith("threads.net") || host.endsWith("threads.com") -> "Threads"
            host.endsWith("github.com") -> "GitHub"
            host.endsWith("medium.com") -> "Medium"
            host.endsWith("spotify.com") -> "Spotify"
            host.endsWith("pinterest.com") || host == "pin.it" -> "Pinterest"
            host == "maps.app.goo.gl" || host.startsWith("maps.google.") ||
                (host.endsWith("google.com") && path.startsWith("/maps")) -> "Maps"
            host.contains("amazon.") || host == "amzn.to" || host == "a.co" -> "Amazon"
            host.isEmpty() -> "Link"
            else -> host.removePrefix("www.").removePrefix("m.")
        }
    }

    /** Links that are videos, so cards can show a play badge. */
    fun looksLikeVideo(url: String?): Boolean {
        if (url == null) return false
        val u = url.lowercase()
        return "/reel/" in u || "/reels/" in u || "youtube.com/watch" in u || "youtu.be/" in u ||
            "/shorts/" in u || "tiktok.com/" in u || "/video/" in u
    }
}
