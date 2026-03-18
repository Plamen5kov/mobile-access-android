package xyz.fivekov.terminal.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.data.ServerConfig

class ServerAdapter(
    private val onConnect: (ServerConfig) -> Unit,
    private val onEdit: (ServerConfig) -> Unit,
) : ListAdapter<ServerConfig, ServerAdapter.ViewHolder>(ServerDiff) {

    private val activeServerIds = mutableSetOf<String>()

    fun setActiveServerIds(ids: Set<String>) {
        activeServerIds.clear()
        activeServerIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = getItem(position)
        holder.name.text = server.displayName
        holder.detail.text = "${server.username}@${server.hostname}:${server.port}"
        holder.statusDot.visibility =
            if (activeServerIds.contains(server.id)) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onConnect(server) }
        holder.editBtn.setOnClickListener { onEdit(server) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.server_name)
        val detail: TextView = view.findViewById(R.id.server_detail)
        val statusDot: View = view.findViewById(R.id.status_dot)
        val editBtn: ImageButton = view.findViewById(R.id.btn_edit)
    }

    private object ServerDiff : DiffUtil.ItemCallback<ServerConfig>() {
        override fun areItemsTheSame(a: ServerConfig, b: ServerConfig) = a.id == b.id
        override fun areContentsTheSame(a: ServerConfig, b: ServerConfig) = a == b
    }
}
