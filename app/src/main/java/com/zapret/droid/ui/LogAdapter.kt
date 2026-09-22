package com.zapret.droid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zapret.droid.R

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val logs = mutableListOf<String>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLog: TextView = view.findViewById(R.id.tvLogEntry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tvLog.text = logs[position]
    }

    override fun getItemCount() = logs.size

    fun addLog(msg: String) {
        logs.add(0, "[${currentTime()}] $msg")
        if (logs.size > 200) logs.removeAt(logs.size - 1)
        notifyItemInserted(0)
    }

    fun setLogs(newLogs: List<String>) {
        logs.clear()
        logs.addAll(newLogs)
        notifyDataSetChanged()
    }

    private fun currentTime(): String {
        val cal = java.util.Calendar.getInstance()
        return "%02d:%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND))
    }
}
