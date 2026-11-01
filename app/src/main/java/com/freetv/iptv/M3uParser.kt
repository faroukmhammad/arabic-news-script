package com.freetv.iptv

import java.net.HttpURLConnection
import java.net.URL

object M3uParser {

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun fetch(url: String): List<Channel> {
        var current = url
        // Follow redirects manually (HttpURLConnection does not follow http<->https).
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "FreeIPTV/1.0 (Android)")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    current = conn.getHeaderField("Location") ?: error("Bad redirect")
                    return@repeat
                }
                if (code != 200) error("HTTP $code")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                return parse(text)
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects")
    }

    fun parse(text: String): List<Channel> {
        val result = ArrayList<Channel>()
        var attrs: Map<String, String> = emptyMap()
        var name: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaIdx = findTitleComma(line)
                    val header = if (commaIdx >= 0) line.substring(0, commaIdx) else line
                    name = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else null
                    attrs = attrRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    userAgent = attrs["user-agent"]
                    referrer = attrs["http-referrer"]
                }
                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) ->
                    userAgent = line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) ->
                    referrer = line.substringAfter('=')
                line.startsWith("#") -> Unit
                else -> {
                    val title = name?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"] ?: line
                    result += Channel(
                        name = title,
                        url = line,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() },
                        userAgent = userAgent,
                        referrer = referrer,
                    )
                    attrs = emptyMap(); name = null; userAgent = null; referrer = null
                }
            }
        }
        return result
    }

    /** The title follows the first comma that is not inside a quoted attribute. */
    private fun findTitleComma(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }
}
