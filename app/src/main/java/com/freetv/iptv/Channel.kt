package com.freetv.iptv

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val userAgent: String?,
    val referrer: String?,
)

/**
 * A category built from free public playlists of the iptv-org project
 * (https://github.com/iptv-org/iptv). If [nameFilter] is set, only channels whose
 * name contains it are shown.
 */
data class Playlist(
    val title: String,
    val urls: List<String>,
    val nameFilter: String? = null,
) {
    constructor(title: String, url: String) : this(title, listOf(url))
}

object Playlists {
    private const val BASE = "https://iptv-org.github.io/iptv"

    val all = listOf(
        Playlist("Arabic", "$BASE/languages/ara.m3u"),
        // Every channel in the iptv-org database, grouped by country (≈10,000 channels).
        // Search by channel or country name, e.g. "France".
        Playlist("All World", "$BASE/index.country.m3u"),
        Playlist("News", "$BASE/categories/news.m3u"),
        // beIN-branded channels that appear in the free public lists (free-to-air ones only;
        // beIN's paid channels are not in these lists).
        Playlist(
            "beIN Sports",
            listOf("$BASE/categories/sports.m3u", "$BASE/languages/ara.m3u", "$BASE/countries/qa.m3u"),
            nameFilter = "bein",
        ),
        Playlist("Sports", "$BASE/categories/sports.m3u"),
        Playlist("Kids", "$BASE/categories/kids.m3u"),
        Playlist("Movies", "$BASE/categories/movies.m3u"),
        Playlist("Music", "$BASE/categories/music.m3u"),
        Playlist("Documentary", "$BASE/categories/documentary.m3u"),
        Playlist("Religious", "$BASE/categories/religious.m3u"),
        Playlist("English", "$BASE/languages/eng.m3u"),
        Playlist("Saudi Arabia", "$BASE/countries/sa.m3u"),
        Playlist("Egypt", "$BASE/countries/eg.m3u"),
        Playlist("UAE", "$BASE/countries/ae.m3u"),
        Playlist("Jordan", "$BASE/countries/jo.m3u"),
    )
}

/** Holds the list currently being watched so the player can zap up/down. */
object ChannelStore {
    var channels: List<Channel> = emptyList()
}
