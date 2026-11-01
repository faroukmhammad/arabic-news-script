package com.freetv.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CategoryAdapter(
    private val items: List<Playlist>,
    private val onClick: (Playlist) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var selected: Playlist? = null

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
    }

    fun select(p: Playlist) {
        val old = items.indexOf(selected)
        selected = p
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(items.indexOf(p))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.title.text = p.title
        holder.itemView.isActivated = p == selected
        holder.itemView.setOnClickListener { onClick(p) }
    }
}

class ChannelAdapter(
    private val onClick: (List<Channel>, Int) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val group: TextView = view.findViewById(R.id.group)
    }

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.group.text = c.group ?: ""
        holder.group.visibility = if (c.group.isNullOrBlank()) View.GONE else View.VISIBLE
        Glide.with(holder.logo)
            .load(c.logo)
            .placeholder(R.drawable.ic_tv)
            .error(R.drawable.ic_tv)
            .fitCenter()
            .into(holder.logo)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items, pos)
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }
}
