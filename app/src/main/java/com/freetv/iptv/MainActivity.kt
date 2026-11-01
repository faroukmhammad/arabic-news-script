package com.freetv.iptv

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, List<Channel>>()

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val myPlaylistTitle by lazy { getString(R.string.my_playlist) }
    /** Special entry: no URLs, opens the "add playlist URL" dialog. */
    private val addEntry by lazy { Playlist(getString(R.string.add_playlist), emptyList()) }

    private var loaded: List<Channel> = emptyList()
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        search = findViewById(R.id.search)

        categoryAdapter = CategoryAdapter(categories()) { onCategory(it) }
        findViewById<RecyclerView>(R.id.categories).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = categoryAdapter
        }

        channelAdapter = ChannelAdapter { list, index -> play(list, index) }
        findViewById<RecyclerView>(R.id.channels).apply {
            val span = maxOf(2, (resources.configuration.screenWidthDp - 200) / 170)
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = channelAdapter
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        load(Playlists.all.first())
    }

    private fun customUrl(): String? = prefs.getString(KEY_CUSTOM_URL, null)?.takeIf { it.isNotBlank() }

    private fun categories(): List<Playlist> = buildList {
        addAll(Playlists.all)
        customUrl()?.let { add(Playlist(myPlaylistTitle, it)) }
        add(addEntry)
    }

    private fun onCategory(p: Playlist) {
        if (p.urls.isEmpty()) showPlaylistDialog() else load(p)
    }

    /** Lets the user enter their own M3U playlist URL (e.g. from a TV service they subscribe to). */
    private fun showPlaylistDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://example.com/playlist.m3u"
            setText(customUrl() ?: "")
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.add_playlist_title)
            .setMessage(R.string.add_playlist_message)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    prefs.edit().putString(KEY_CUSTOM_URL, url).apply()
                    cache.remove(myPlaylistTitle)
                    categoryAdapter.setItems(categories())
                    load(Playlist(myPlaylistTitle, url))
                } else {
                    android.widget.Toast.makeText(this, R.string.invalid_url, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (customUrl() != null) {
            builder.setNeutralButton(R.string.remove) { _, _ ->
                prefs.edit().remove(KEY_CUSTOM_URL).apply()
                cache.remove(myPlaylistTitle)
                categoryAdapter.setItems(categories())
                load(Playlists.all.first())
            }
        }
        builder.show()
        input.requestFocus()
    }

    private fun load(playlist: Playlist) {
        categoryAdapter.select(playlist)
        val id = ++requestId
        cache[playlist.title]?.let { show(it); return }

        progress.visibility = View.VISIBLE
        status.visibility = View.VISIBLE
        status.text = getString(R.string.loading, playlist.title)
        channelAdapter.submit(emptyList())

        executor.execute {
            val result = runCatching { fetchPlaylist(playlist) }
            main.post {
                if (id != requestId || isFinishing) return@post
                progress.visibility = View.GONE
                result.onSuccess {
                    cache[playlist.title] = it
                    show(it)
                }.onFailure {
                    status.visibility = View.VISIBLE
                    status.text = getString(R.string.load_error, it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    /** Downloads every source of a category, merges them, drops duplicates and applies the name filter. */
    private fun fetchPlaylist(playlist: Playlist): List<Channel> {
        val merged = LinkedHashMap<String, Channel>()
        var lastError: Throwable? = null
        for (url in playlist.urls) {
            runCatching { M3uParser.fetch(url) }
                .onSuccess { list -> list.forEach { merged.putIfAbsent(it.url, it) } }
                .onFailure { lastError = it }
        }
        if (merged.isEmpty()) lastError?.let { throw it }
        val filter = playlist.nameFilter ?: return merged.values.toList()
        return merged.values.filter { it.name.contains(filter, ignoreCase = true) }
    }

    private fun show(channels: List<Channel>) {
        progress.visibility = View.GONE
        loaded = channels
        applyFilter()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        val list = if (q.isEmpty()) loaded else loaded.filter {
            it.name.contains(q, ignoreCase = true) || (it.group?.contains(q, ignoreCase = true) == true)
        }
        channelAdapter.submit(list)
        if (list.isEmpty() && progress.visibility != View.VISIBLE) {
            status.visibility = View.VISIBLE
            status.text = getString(R.string.no_channels)
        } else if (list.isNotEmpty()) {
            status.visibility = View.GONE
        }
    }

    private fun play(list: List<Channel>, index: Int) {
        ChannelStore.channels = list
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, index))
    }

    companion object {
        private const val KEY_CUSTOM_URL = "custom_playlist_url"
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
