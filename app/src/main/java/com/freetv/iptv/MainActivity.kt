package com.freetv.iptv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
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

    private var loaded: List<Channel> = emptyList()
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        search = findViewById(R.id.search)

        categoryAdapter = CategoryAdapter(Playlists.all) { load(it) }
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

    private fun load(playlist: Playlist) {
        categoryAdapter.select(playlist)
        val id = ++requestId
        cache[playlist.url]?.let { show(it); return }

        progress.visibility = View.VISIBLE
        status.visibility = View.VISIBLE
        status.text = getString(R.string.loading, playlist.title)
        channelAdapter.submit(emptyList())

        executor.execute {
            val result = runCatching { M3uParser.fetch(playlist.url) }
            main.post {
                if (id != requestId || isFinishing) return@post
                progress.visibility = View.GONE
                result.onSuccess {
                    cache[playlist.url] = it
                    show(it)
                }.onFailure {
                    status.visibility = View.VISIBLE
                    status.text = getString(R.string.load_error, it.message ?: it.javaClass.simpleName)
                }
            }
        }
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
