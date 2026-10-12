package com.peru.expresdm.ui.account

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.peru.expresdm.BuildConfig
import com.peru.expresdm.R
import com.peru.expresdm.data.Premium
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import com.peru.expresdm.data.net.Session
import com.peru.expresdm.databinding.SheetPremiumBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date

/**
 * Subo Premium: qué trae de más, si la cuenta ya lo tiene y cómo pedirlo.
 *
 * Premium va con la cuenta, así que primero hay que tener una. Pedirlo deja un
 * mensaje en el buzón del servidor; el administrador lo activa al confirmar el
 * pago y la app se entera sola la próxima vez que pregunta por la sesión.
 */
class PremiumSheet : BottomSheetDialogFragment() {

    private var _binding: SheetPremiumBinding? = null
    private val binding get() = _binding!!

    /** Ya se mandó la solicitud desde esta hoja: no hace falta repetirla. */
    private var requested = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requested = savedInstanceState?.getBoolean(STATE_REQUESTED) ?: false

        BENEFITS.forEach { text ->
            val row = layoutInflater.inflate(R.layout.item_premium_benefit, binding.benefits, false) as TextView
            row.text = getString(text, Premium.PREMIUM_SAVED_ROUTES)
            binding.benefits.addView(row)
        }

        binding.btnPremiumAction.setOnClickListener { onAction() }

        viewLifecycleOwner.lifecycleScope.launch {
            // Puede que lo acaben de activar: se pregunta al abrir.
            launch { AccountRepository.refresh() }
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccountRepository.session.collect { render(it) }
            }
        }
    }

    private fun render(session: Session?) {
        val binding = _binding ?: return
        val active = Premium.isActive(session)

        binding.premiumStatus.text = when {
            active -> getString(
                R.string.premium_status_active,
                DateFormat.getMediumDateFormat(requireContext()).format(Date(session!!.premiumUntil!!))
            )
            session == null -> getString(R.string.premium_status_signed_out)
            else -> getString(R.string.premium_status_free)
        }

        binding.btnPremiumAction.isVisible = !active
        binding.btnPremiumAction.isEnabled = !requested
        binding.btnPremiumAction.setText(
            when {
                session == null -> R.string.premium_action_sign_in
                requested -> R.string.premium_action_requested
                else -> R.string.premium_action_request
            }
        )
        binding.premiumHow.isVisible = !active
        binding.premiumHow.setText(
            when {
                session == null -> R.string.premium_how_sign_in
                requested -> R.string.premium_how_requested
                else -> R.string.premium_how_request
            }
        )
    }

    private fun onAction() {
        val session = AccountRepository.session.value
        if (session == null) {
            AccountSheet.show(parentFragmentManager)
            dismiss()
            return
        }
        binding.btnPremiumAction.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val body = JSONObject()
                .put("subject", getString(R.string.premium_request_subject))
                .put("message", getString(R.string.premium_request_message, session.email))
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            val result = ApiClient.post("/api/feedback", body, session.token)
            val binding = _binding ?: return@launch
            when (result) {
                is ApiResult.Success -> {
                    requested = true
                    render(AccountRepository.session.value)
                }
                is ApiResult.Failure -> {
                    binding.btnPremiumAction.isEnabled = true
                    binding.premiumHow.isVisible = true
                    // El motivo que da el servidor ya viene listo para mostrar.
                    binding.premiumHow.text = when (result.reason) {
                        ApiFailure.NETWORK -> getString(R.string.premium_request_failed)
                        else -> result.message ?: getString(R.string.premium_request_failed)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_REQUESTED, requested)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "PremiumSheet"
        private const val STATE_REQUESTED = "requested"

        /** Solo lo que ya funciona: no se promete lo que aún no existe. */
        private val BENEFITS = listOf(R.string.premium_benefit_offline)

        fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag(TAG) == null) PremiumSheet().show(manager, TAG)
        }
    }
}
