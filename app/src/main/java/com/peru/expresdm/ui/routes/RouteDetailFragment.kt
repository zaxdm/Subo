package com.peru.expresdm.ui.routes

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.peru.expresdm.R
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.FareTable
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.databinding.FragmentRouteDetailBinding
import com.peru.expresdm.databinding.ItemRouteStopBinding
import com.peru.expresdm.ui.RouteAccent
import com.peru.expresdm.ui.RouteImages
import java.util.Locale

/**
 * La ficha de un micro: su foto, cuándo pasa, cuánto cuesta y por dónde va.
 *
 * El mapa enseña el recorrido; esta pantalla, lo que hay que saber antes de
 * subirse.
 */
class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val route = arguments?.getString(ARG_ROUTE_ID)?.let { RouteRepository.byId(it) }
        if (route == null) {
            // La ruta se borró mientras tanto: no hay ficha que enseñar.
            findNavController().popBackStack()
            return
        }
        render(route)
    }

    private fun render(route: BusRoute) {
        val color = ContextCompat.getColor(requireContext(), RouteAccent.colorFor(route.accentIndex))

        binding.detailBadge.text = route.letter
        binding.detailBadge.backgroundTintList = ColorStateList.valueOf(color)
        binding.detailName.text = route.name
        binding.detailSubtitle.text = route.subtitle

        binding.detailImageCard.setCardBackgroundColor(color)
        binding.detailWatermark.text = route.letter
        RouteImages.show(binding.detailImage, route.imageUrl, route.vehicleName.ifBlank { route.name })

        val description = listOf(route.vehicleName, route.description)
            .filter { it.isNotBlank() }.joinToString("\n")
        binding.detailDescription.text = description
        binding.detailDescription.isVisible = description.isNotEmpty()

        binding.detailSchedule.text = route.schedule
        binding.detailDuration.text = getString(R.string.duration_minutes, route.durationMinutes)

        val fares = FareTable.of(route)
        binding.fareSchool.text = formatFare(fares.school)
        binding.fareUniversity.text = formatFare(fares.university)
        binding.fareRegular.text = formatFare(fares.regular)

        binding.detailStops.removeAllViews()
        route.stops.forEachIndexed { index, stop ->
            val item = ItemRouteStopBinding.inflate(layoutInflater, binding.detailStops, false)
            item.stopNumber.text = (index + 1).toString()
            item.stopNumber.backgroundTintList = ColorStateList.valueOf(color)
            item.stopName.text = stop.name
            // Aquí las paradas solo se leen; en el mapa son las que se tocan.
            item.root.isClickable = false
            item.root.isFocusable = false
            binding.detailStops.addView(item.root)
        }
    }

    private fun formatFare(amount: Double): String =
        String.format(Locale.US, getString(R.string.fare_format), amount)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ROUTE_ID = "routeId"

        fun args(routeId: String): Bundle = bundleOf(ARG_ROUTE_ID to routeId)
    }
}
