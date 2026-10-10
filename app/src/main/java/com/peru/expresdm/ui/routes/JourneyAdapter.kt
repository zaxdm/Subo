package com.peru.expresdm.ui.routes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peru.expresdm.R
import com.peru.expresdm.data.Journey
import com.peru.expresdm.data.JourneyLeg
import com.peru.expresdm.data.RidePoint
import com.peru.expresdm.databinding.ItemJourneyBinding
import com.peru.expresdm.databinding.ItemJourneyCardBinding
import com.peru.expresdm.ui.RouteAccent
import com.peru.expresdm.ui.RouteImages
import com.peru.expresdm.ui.formatDistance
import com.peru.expresdm.ui.themeColor
import java.util.Locale

/**
 * Las formas de llegar al lugar buscado.
 *
 * Con la hoja en reposo son un carrusel de tarjetas compactas (qué micro, cuánto
 * se tarda, cuánto se camina y cuánto cuesta) para comparar de un vistazo sin
 * tapar el mapa; al subirla, la lista con las instrucciones paso a paso.
 */
class JourneyAdapter(
    private val onJourneyClick: (Journey) -> Unit
) : ListAdapter<Journey, RecyclerView.ViewHolder>(DIFF) {

    /** `true`: tarjetas de carrusel; `false`: lista con las instrucciones. */
    var carousel: Boolean = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** El viaje dibujado en el mapa, por los micros que usa; se marca con un borde. */
    var selectedKey: List<String>? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = if (carousel) TYPE_CARD else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CARD) {
            CardViewHolder(ItemJourneyCardBinding.inflate(inflater, parent, false))
        } else {
            JourneyViewHolder(ItemJourneyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> holder.bind(getItem(position))
            is JourneyViewHolder -> holder.bind(getItem(position))
        }
    }

    /** Tarjeta del carrusel: lo justo para elegir entre una opción y otra. */
    inner class CardViewHolder(
        private val binding: ItemJourneyCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(journey: Journey) {
            val context = binding.root.context
            val rides = journey.rides
            val first = rides.firstOrNull()

            // El color y la foto son los del primer micro; andando, el de la marca.
            val accent = first?.let { ContextCompat.getColor(context, RouteAccent.colorFor(it.route.accentIndex)) }
                ?: context.themeColor(com.google.android.material.R.attr.colorPrimary)
            binding.journeyCard.setCardBackgroundColor(accent)
            RouteImages.show(
                binding.journeyImage, first?.route?.imageUrl, first?.route?.let { it.vehicleName.ifBlank { it.name } }.orEmpty()
            )

            binding.journeyChain.removeAllViews()
            if (rides.isEmpty()) {
                binding.journeyChain.addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_walk)
                        imageTintList = ColorStateList.valueOf(context.getColor(R.color.white))
                        layoutParams = ViewGroup.LayoutParams(dp(24), dp(24))
                        importantForAccessibility = ViewGroup.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                )
            }
            rides.forEachIndexed { index, ride ->
                binding.journeyChain.addView(
                    TextView(context).apply {
                        text = ride.route.letter
                        setTextColor(ContextCompat.getColor(context, RouteAccent.colorFor(ride.route.accentIndex)))
                        textSize = 15f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_chip_white)
                        minWidth = dp(30)
                        setPadding(dp(8), 0, dp(8), 0)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30))
                            .apply { if (index > 0) marginStart = dp(6) }
                    }
                )
            }

            binding.journeyTime.text = context.getString(R.string.duration_minutes, journey.totalMinutes)
            binding.journeyTitle.text = if (first == null) {
                context.getString(R.string.journey_card_walk)
            } else {
                rides.joinToString(" + ") { it.route.vehicleName.ifBlank { it.route.name } }
            }
            binding.journeyBoard.text = if (first == null) {
                context.getString(R.string.journey_card_walk_detail)
            } else {
                context.getString(R.string.journey_card_board, pointLabel(context, first.fromKind, first.from.name, Role.BOARD))
            }
            binding.journeyWalk.text =
                context.getString(R.string.journey_total_walk, context.formatDistance(journey.walkMeters))
            binding.journeyFare.isVisible = first != null
            binding.journeyFare.text = String.format(Locale.US, context.getString(R.string.fare_format), journey.fare)

            binding.journeyCard.strokeWidth = if (journey.key == selectedKey) dp(3) else 0
            binding.journeyCard.contentDescription =
                listOf(binding.journeyTitle.text, binding.journeyTime.text, stepsText(context, journey)).joinToString(". ")
            binding.journeyCard.setOnClickListener { onJourneyClick(journey) }
        }

        private fun dp(value: Int) = (value * binding.root.resources.displayMetrics.density).toInt()
    }

    /** Fila de la lista: el viaje con sus instrucciones. */
    inner class JourneyViewHolder(
        private val binding: ItemJourneyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(journey: Journey) {
            val context = binding.root.context

            binding.journeyTime.text = context.getString(R.string.duration_minutes, journey.totalMinutes)
            binding.journeyFare.isVisible = !journey.walkingOnly
            binding.journeyFare.text =
                String.format(Locale.US, context.getString(R.string.fare_format), journey.fare)

            binding.journeyTransfers.isVisible = journey.transfers > 0
            if (journey.transfers > 0) {
                binding.journeyTransfers.text = context.resources.getQuantityString(
                    R.plurals.journey_transfers, journey.transfers, journey.transfers
                )
                binding.journeyTransfers.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.alert_traffic)
                )
            }

            buildChain(journey)
            binding.journeySteps.text = stepsText(context, journey)
            binding.journeyPhotos.removeAllViews()
            journey.rides.filter { !it.route.imageUrl.isNullOrBlank() }.forEach { ride ->
                val image = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120))
                        .apply { topMargin = dp(10) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                binding.journeyPhotos.addView(image)
                RouteImages.show(image, ride.route.imageUrl, ride.route.vehicleName.ifBlank { ride.route.name })
            }
            binding.journeyCard.strokeWidth = dp(if (journey.key == selectedKey) 2 else 1)
            binding.journeyCard.strokeColor = context.themeColor(
                if (journey.key == selectedKey) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOutline
            )
            binding.journeyCard.setOnClickListener { onJourneyClick(journey) }
        }

        /** La cadena visual: 🚶 · (A) · 🚶 · (H) · 🚶 */
        private fun buildChain(journey: Journey) {
            val context = binding.root.context
            val chain = binding.journeyChain
            chain.removeAllViews()

            journey.legs.forEachIndexed { index, leg ->
                if (index > 0) chain.addView(separator())

                when (leg) {
                    is JourneyLeg.Walk -> chain.addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_walk)
                            imageTintList = ColorStateList.valueOf(
                                context.getColor(android.R.color.darker_gray)
                            )
                            layoutParams = ViewGroup.LayoutParams(dp(18), dp(18))
                            importantForAccessibility = ViewGroup.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }
                    )

                    is JourneyLeg.Ride -> chain.addView(
                        TextView(context).apply {
                            text = leg.route.letter
                            setTextColor(context.getColor(R.color.white))
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setBackgroundResource(R.drawable.bg_circle_badge)
                            backgroundTintList = ContextCompat.getColorStateList(
                                context, RouteAccent.colorFor(leg.route.accentIndex)
                            )
                            layoutParams = ViewGroup.LayoutParams(dp(26), dp(26))
                            contentDescription = leg.route.name
                        }
                    )
                }
            }
        }

        private fun separator() = TextView(binding.root.context).apply {
            text = binding.root.context.getString(R.string.journey_chain_separator)
            setTextColor(context.getColor(android.R.color.darker_gray))
            textSize = 12f
            setPadding(dp(6), 0, dp(6), 0)
        }

        private fun dp(value: Int) =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }

    /** Para qué se nombra un punto del recorrido: cambia cómo se dice cuando no es una parada. */
    private enum class Role { BOARD, EXIT, TRANSFER }

    /**
     * Cómo se llama el sitio donde se sube o se baja. Una parada tiene nombre;
     * un punto cualquiera del recorrido se describe por lo que tiene cerca.
     */
    private fun pointLabel(context: Context, kind: RidePoint, stopName: String, role: Role): String = when (kind) {
        RidePoint.STOP -> stopName
        RidePoint.NEAR_STOP -> context.getString(R.string.journey_point_near, stopName)
        RidePoint.ON_ROUTE -> context.getString(
            when (role) {
                Role.BOARD -> R.string.journey_point_board
                Role.EXIT -> R.string.journey_point_exit
                Role.TRANSFER -> R.string.journey_point_transfer
            }
        )
    }

    /** Las instrucciones en una frase por tramo: dónde subir, dónde cambiar, dónde bajar. */
    private fun stepsText(context: Context, journey: Journey): String {
        if (journey.walkingOnly) {
            val walk = journey.legs.filterIsInstance<JourneyLeg.Walk>().firstOrNull()
            return context.getString(
                R.string.journey_walk_only,
                context.formatDistance(walk?.meters ?: journey.walkMeters)
            )
        }

        val rides = journey.rides
        return journey.legs.mapIndexed { index, leg ->
            val text = when (leg) {
                is JourneyLeg.Walk -> context.getString(
                    R.string.journey_walk_step, context.formatDistance(leg.meters), leg.minutes
                )
                is JourneyLeg.Ride -> context.getString(
                    R.string.journey_ride_step,
                    leg.route.vehicleName.ifBlank { leg.route.name },
                    pointLabel(context, leg.fromKind, leg.from.name, if (leg === rides.first()) Role.BOARD else Role.TRANSFER),
                    pointLabel(context, leg.toKind, leg.to.name, if (leg === rides.last()) Role.EXIT else Role.TRANSFER),
                    leg.minutes
                )
            }
            "${index + 1}. $text"
        }.joinToString("\n")
    }

    private companion object {
        const val TYPE_ROW = 0
        const val TYPE_CARD = 1

        val DIFF = object : DiffUtil.ItemCallback<Journey>() {
            override fun areItemsTheSame(old: Journey, new: Journey) = old.key == new.key

            override fun areContentsTheSame(old: Journey, new: Journey) = old == new
        }
    }
}
