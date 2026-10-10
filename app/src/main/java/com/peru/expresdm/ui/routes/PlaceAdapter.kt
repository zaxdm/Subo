package com.peru.expresdm.ui.routes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peru.expresdm.data.Place
import com.peru.expresdm.databinding.ItemPlaceBinding
import com.peru.expresdm.ui.formatDistance

/** Un lugar encontrado y, si se conoce la posición del usuario, lo lejos que queda. */
data class PlaceResult(
    val place: Place,
    val distanceMeters: Double?
)

/** Resultados del buscador de lugares. */
class PlaceAdapter(
    private val onPlaceClick: (Place) -> Unit
) : ListAdapter<PlaceResult, PlaceAdapter.PlaceViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaceViewHolder(
        private val binding: ItemPlaceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: PlaceResult) {
            val context = binding.root.context
            binding.placeName.text = result.place.name
            binding.placeDetail.text = result.place.detail
            binding.placeDetail.isVisible = result.place.detail.isNotBlank()
            binding.placeDistance.isVisible = result.distanceMeters != null
            result.distanceMeters?.let { binding.placeDistance.text = context.formatDistance(it) }
            binding.root.setOnClickListener { onPlaceClick(result.place) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PlaceResult>() {
            override fun areItemsTheSame(old: PlaceResult, new: PlaceResult) = old.place == new.place
            override fun areContentsTheSame(old: PlaceResult, new: PlaceResult) = old == new
        }
    }
}
