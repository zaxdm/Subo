package com.peru.expresdm.ui.routes

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.ai.ModerationService
import com.peru.expresdm.ai.ModerationVerdict
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.FareTable
import com.peru.expresdm.data.RouteCommunity
import com.peru.expresdm.data.RouteCommunityRepository
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.SavedRoutes
import com.peru.expresdm.data.SharedLink
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import com.peru.expresdm.databinding.DialogRateRouteBinding
import com.peru.expresdm.databinding.FragmentRouteDetailBinding
import com.peru.expresdm.databinding.ItemReviewBinding
import com.peru.expresdm.databinding.ItemRouteStopBinding
import com.peru.expresdm.ui.AlertStyle
import com.peru.expresdm.ui.RouteAccent
import com.peru.expresdm.ui.RouteImages
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * La ficha de un micro: su foto, cuándo pasa, cuánto cuesta, por dónde va y lo
 * que la gente dice de él.
 *
 * El mapa enseña el recorrido; esta pantalla, lo que hay que saber antes de
 * subirse.
 */
class RouteDetailFragment : Fragment() {

    private var _binding: FragmentRouteDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var route: BusRoute
    private var community: RouteCommunity? = null

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

        val found = arguments?.getString(ARG_ROUTE_ID)?.let { RouteRepository.byId(it) }
        if (found == null) {
            // La ruta se borró mientras tanto: no hay ficha que enseñar.
            findNavController().popBackStack()
            return
        }
        route = found
        render(route)

        binding.btnShare.setOnClickListener { share() }
        binding.btnSaveOffline.setOnClickListener { toggleSaved() }
        binding.btnRate.setOnClickListener { askForRating() }
        renderSaved()

        if (ApiClient.isConfigured) {
            // Solo la primera vez: girar la pantalla no es volver a abrir la ficha.
            if (savedInstanceState == null) RouteCommunityRepository.recordView(route.id)
            binding.communitySection.isVisible = true
            renderCommunity()
            viewLifecycleOwner.lifecycleScope.launch {
                val result = RouteCommunityRepository.load(route.id)
                if (result is ApiResult.Success) {
                    community = result.data
                    renderCommunity()
                }
            }
        }
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
        binding.detailFrequency.isVisible = route.frequencyMinutes != null
        route.frequencyMinutes?.let { binding.detailFrequency.text = getString(R.string.route_detail_frequency, it) }

        // Los pasajes reducidos solo se enseñan cuando alguien los cargó en el panel.
        val fares = FareTable.of(route)
        binding.rowFareSchool.isVisible = fares.school != null
        binding.rowFareUniversity.isVisible = fares.university != null
        fares.school?.let { binding.fareSchool.text = formatFare(it) }
        fares.university?.let { binding.fareUniversity.text = formatFare(it) }
        binding.fareRegular.text = formatFare(fares.regular)
        binding.faresNote.isVisible = fares.isIncomplete

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

    private fun formatFare(amount: Double): String =
        String.format(Locale.US, getString(R.string.fare_format), amount)

    // --- Compartir ---

    /** Manda la ruta por la app que elija el usuario: qué micro es, cuánto cuesta y por dónde pasa. */
    private fun share() {
        val places = route.stops.joinToString(", ") { it.name }
        val text = getString(
            R.string.route_share_text,
            route.name,
            route.subtitle,
            formatFare(route.fare),
            route.schedule,
            places
        )
        // Con el enlace, quien tiene Subo abre esta ficha; quien no, la web.
        val link = SharedLink.forRoute(route.id)
        val message = if (link == null) text else "$text\n$link"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        startActivity(Intent.createChooser(intent, getString(R.string.route_detail_share)))
    }

    // --- Guardar sin conexión ---

    private fun toggleSaved() {
        val saved = SavedRoutes.get(requireContext())
        when {
            saved.isSaved(route.id) -> {
                saved.remove(route.id)
                renderSaved()
            }

            !saved.hasRoom -> showMessage(getString(R.string.route_save_limit, SavedRoutes.MAX_SAVED))

            else -> {
                binding.btnSaveOffline.isEnabled = false
                binding.btnSaveOffline.setText(R.string.route_save_downloading)
                saved.save(route) { downloaded ->
                    val binding = _binding ?: return@save
                    binding.btnSaveOffline.isEnabled = true
                    renderSaved()
                    showMessage(getString(if (downloaded) R.string.route_save_done else R.string.route_save_partial))
                }
            }
        }
    }

    private fun renderSaved() {
        val saved = SavedRoutes.get(requireContext())
        binding.btnSaveOffline.setText(
            if (saved.isSaved(route.id)) R.string.route_detail_saved else R.string.route_detail_save
        )
        binding.saveOfflineNote.text =
            getString(R.string.route_save_count, saved.ids.value.size, SavedRoutes.MAX_SAVED)
    }

    // --- Calificaciones ---

    private fun renderCommunity() {
        val binding = _binding ?: return
        val data = community

        val average = data?.ratingAverage
        binding.ratingSummary.text = if (data == null || average == null) {
            getString(R.string.route_detail_no_ratings)
        } else {
            resources.getQuantityString(
                R.plurals.route_detail_rating_summary, data.ratingCount,
                String.format(Locale.US, "%.1f", average), data.ratingCount
            )
        }
        binding.btnRate.setText(
            if (data?.myStars == null) R.string.route_detail_rate else R.string.route_detail_rate_change
        )

        binding.reviewsContainer.removeAllViews()
        data?.reviews.orEmpty().forEach { review ->
            val item = ItemReviewBinding.inflate(layoutInflater, binding.reviewsContainer, false)
            item.reviewHeader.text = getString(R.string.route_review_header, stars(review.stars), review.userName)
            item.reviewComment.text = review.comment
            binding.reviewsContainer.addView(item.root)
        }

        // Lo más reportado de este micro en la semana, si hubo algo.
        val reports = data?.alertsThisWeek.orEmpty().filterValues { it > 0 }
        binding.detailAlertsWeek.isVisible = reports.isNotEmpty()
        if (reports.isNotEmpty()) {
            val parts = reports.entries.sortedByDescending { it.value }.joinToString(", ") { (type, count) ->
                getString(R.string.route_detail_alert_count, count, getString(AlertStyle.label(type)).lowercase())
            }
            binding.detailAlertsWeek.text = getString(R.string.route_detail_alerts_week, parts)
        }
    }

    private fun stars(count: Int): String = "★".repeat(count.coerceIn(0, 5)) + "☆".repeat((5 - count).coerceIn(0, 5))

    private fun askForRating() {
        if (!AccountRepository.isSignedIn) {
            showMessage(getString(R.string.route_rate_needs_account))
            return
        }
        val dialogBinding = DialogRateRouteBinding.inflate(layoutInflater)
        dialogBinding.ratingStars.rating = (community?.myStars ?: 0).toFloat()
        dialogBinding.ratingComment.setText(community?.myComment.orEmpty())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.route_rate_title, route.name))
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val stars = dialogBinding.ratingStars.rating.toInt()
                val comment = dialogBinding.ratingComment.text?.toString().orEmpty().trim()
                if (stars < 1) showMessage(getString(R.string.route_rate_needs_stars)) else rate(stars, comment)
            }
            .show()
    }

    private fun rate(stars: Int, comment: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Lo escrito pasa por la misma moderación que los comentarios de las alertas.
            when (val verdict = if (comment.isEmpty()) ModerationVerdict.Allowed else ModerationService.review(comment)) {
                ModerationVerdict.Allowed -> Unit
                is ModerationVerdict.RejectedLocally -> {
                    showMessage(getString(R.string.moderation_local_reason, verdict.word))
                    return@launch
                }
                is ModerationVerdict.RejectedByModel -> {
                    showMessage(verdict.reason)
                    return@launch
                }
            }

            when (val result = RouteCommunityRepository.rate(route.id, stars, comment, community)) {
                is ApiResult.Success -> {
                    community = result.data
                    renderCommunity()
                    showMessage(getString(R.string.route_rate_saved))
                }
                is ApiResult.Failure -> showMessage(
                    when (result.reason) {
                        ApiFailure.UNAUTHORIZED -> getString(R.string.route_rate_needs_account)
                        ApiFailure.NETWORK -> getString(R.string.publish_failed)
                        else -> result.message ?: getString(R.string.publish_failed)
                    }
                )
            }
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
