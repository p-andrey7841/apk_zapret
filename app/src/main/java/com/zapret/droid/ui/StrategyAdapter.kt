package com.zapret.droid.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.zapret.droid.R
import com.zapret.droid.strategies.Strategy

class StrategyAdapter(
    private val strategies: List<Strategy>,
    private val onSelect: (Strategy) -> Unit
) : RecyclerView.Adapter<StrategyAdapter.VH>() {

    var selectedId: String = strategies.firstOrNull()?.id ?: ""
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardStrategy)
        val tvName: TextView = view.findViewById(R.id.tvStrategyName)
        val tvDesc: TextView = view.findViewById(R.id.tvStrategyDesc)
        val tvServices: TextView = view.findViewById(R.id.tvStrategyServices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_strategy, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val strategy = strategies[position]
        holder.tvName.text = strategy.name
        holder.tvDesc.text = strategy.description
        holder.tvServices.text = strategy.services.joinToString(" · ") { it.name }

        // Programmatic selection highlight
        holder.card.isCheckable = true
        holder.card.isChecked = strategy.id == selectedId
        holder.card.strokeWidth = if (strategy.id == selectedId) 4 else 0

        holder.card.setOnClickListener {
            selectedId = strategy.id
            onSelect(strategy)
        }
    }

    override fun getItemCount() = strategies.size
}
