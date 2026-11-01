package com.freetv.iptv

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val userAgent: String?,
    val referrer: String?,
)

/** A free public playlist from the iptv-org project (https://github.com/iptv-org/iptv). */
data class Playlist(val title: String, val url: String)

object Playlists {
    private const val BASE = "https://iptv-org.github.io/iptv"

    val all = listOf(
        Playlist("Arabic", "$BASE/languages/ara.m3u"),
        Playlist("News", "$BASE/categories/news.m3u"),
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
