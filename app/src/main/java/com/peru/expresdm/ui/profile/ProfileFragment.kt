package com.peru.expresdm.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.data.ProfileStats
import com.peru.expresdm.data.RouteRepository
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.Session
import com.peru.expresdm.databinding.DialogEditNameBinding
import com.peru.expresdm.databinding.FragmentProfileBinding
import com.peru.expresdm.ui.Initials
import com.peru.expresdm.ui.account.AccountSheet
import com.peru.expresdm.ui.routes.RouteAdapter
import com.peru.expresdm.ui.showRouteOnMap
import kotlinx.coroutines.launch

/**
 * Quién eres, lo que llevas hecho y tus rutas de siempre.
 *
 * Las favoritas no se marcan a mano: salen del uso, así que esta pantalla se
 * llena sola conforme la app se va usando.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private val adapter by lazy {
        // Una favorita se traza en el mapa del inicio, igual que desde la lista de cercanas.
        RouteAdapter(onRouteClick = { route -> showRouteOnMap(route.id) })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.favoritesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
        // La lista crece con su contenido dentro de la pantalla, que es la que
        // se desplaza. En XML este atributo revienta en algunos teléfonos.
        binding.favoritesRecycler.isNestedScrollingEnabled = false

        binding.rowSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }

        binding.rowSupport.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_support)
        }

        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_about)
                .setMessage(R.string.profile_about_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.btnAccount.setOnClickListener { AccountSheet.show(childFragmentManager) }
        binding.btnEditName.setOnClickListener { askForName() }
        binding.rowSignOut.setOnClickListener { signOut() }

        childFragmentManager.setFragmentResultListener(AccountSheet.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            // Con la sesión recién abierta, el catálogo compartido puede traer
            // rutas de otros usuarios.
            viewLifecycleOwner.lifecycleScope.launch { RouteRepository.refresh() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.favorites.collect { items ->
                        adapter.submitList(items)
                        binding.emptyFavorites.isVisible = items.isEmpty()
                    }
                }

                launch { viewModel.stats.collect { renderStats(it) } }

                launch { AccountRepository.session.collect { renderAccount(it) } }

                launch {
                    viewModel.messages.collect { message ->
                        Snackbar.make(
                            binding.root,
                            message.text ?: getString(message.fallback),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /** La tarjeta de identidad: quién eres, o una invitación a entrar. */
    private fun renderAccount(session: Session?) {
        val binding = _binding ?: return
        val name = session?.displayName?.takeIf { it.isNotBlank() }

        binding.profileName.text = name ?: getString(R.string.profile_guest)
        binding.profileAvatar.text = Initials.of(name)
        binding.profileAvatar.isVisible = name != null
        binding.profileAvatarIcon.isVisible = name == null
        binding.profileSubtitle.text = when {
            session != null -> session.email
            !ApiClient.isConfigured -> getString(R.string.account_no_server)
            else -> getString(R.string.profile_guest_subtitle)
        }

        val signedIn = session != null
        binding.btnEditName.isVisible = signedIn
        binding.rowSignOut.isVisible = signedIn
        binding.signOutDivider.isVisible = signedIn
        // Sin servidor no hay cuentas que abrir: el botón solo estorbaría.
        binding.btnAccount.isVisible = !signedIn && ApiClient.isConfigured
    }

    /** Tres números con lo que el usuario lleva aportado. */
    private fun renderStats(stats: ProfileStats) {
        val binding = _binding ?: return
        binding.statRecorded.text = stats.recordedRoutes.toString()
        binding.statAlerts.text = stats.activeAlerts.toString()
        binding.statUsed.text = stats.usedRoutes.toString()
        // Tres ceros no dicen nada: se explica de dónde saldrán.
        binding.emptyActivity.isVisible = stats.isEmpty
    }

    private fun askForName() {
        val dialogBinding = DialogEditNameBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(AccountRepository.session.value?.displayName.orEmpty())
        dialogBinding.nameInput.setSelection(dialogBinding.nameInput.text?.length ?: 0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_name)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, R.string.account_name_required, Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.rename(name)
                }
            }
            .show()
    }

    private fun signOut() {
        AccountRepository.signOut()
        Snackbar.make(binding.root, R.string.account_signed_out, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.favoritesRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
