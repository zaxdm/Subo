package com.peru.expresdm.ui.routes

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.databinding.FragmentRouteDetailBinding
import com.peru.expresdm.databinding.ItemRouteStopBinding
import com.peru.expresdm.ui.RouteAccent

/**
 * La ficha de un micro en una pantalla propia: su foto, cuándo pasa, cuánto
 * cuesta, por dónde va y lo que la gente dice de él.
 *
 * Al tocar una ruta en el mapa la ficha sale ahí mismo, en el panel; a esta
 * pantalla se llega por un enlace compartido o desde un viaje de "Cómo llegar".
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
        // Solo la primera vez: girar la pantalla no es volver a abrir la ficha.
        RouteInfoPanel(this, binding.routeInfo, ::showMessage)
            .show(route, countView = savedInstanceState == null)
    }

    private fun render(route: BusRoute) {
        val color = ContextCompat.getColor(requireContext(), RouteAccent.colorFor(route.accentIndex))

        binding.detailBadge.text = route.letter
        binding.detailBadge.backgroundTintList = ColorStateList.valueOf(color)
        binding.detailName.text = route.name
        binding.detailSubtitle.text = route.subtitle

        binding.detailStops.removeAllViews()
        route.stops.forEachIndexed { index, stop ->
            val item = ItemRouteStopBinding.inflate(layoutInflater, binding.detailStops, false)
            item.stopNumber.text = (index + 1).toString()
            item.stopNumber.backgroundTintList = ColorStateList.valueOf(color)
            item.stopName.text = stop.name
            // Aquí los puntos solo se leen; en el mapa son los que se tocan.
            item.root.isClickable = false
            item.root.isFocusable = false
            binding.detailStops.addView(item.root)
        }
    }

    private fun showMessage(text: String) {
        val binding = _binding ?: return
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ROUTE_ID = "routeId"

        fun args(routeId: String): Bundle = bundleOf(ARG_ROUTE_ID to routeId)
    }
}
