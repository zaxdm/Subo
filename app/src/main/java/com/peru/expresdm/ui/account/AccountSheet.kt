package com.peru.expresdm.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.peru.expresdm.R
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import com.peru.expresdm.databinding.SheetAccountBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException

/**
 * Iniciar sesión o crear una cuenta.
 *
 * La cuenta hace falta para publicar alertas, compartir rutas y usar el
 * asistente. Ver el mapa y las rutas no la necesita.
 */
class AccountSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAccountBinding? = null
    private val binding get() = _binding!!

    private var creatingAccount = false
    private var working = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        creatingAccount = savedInstanceState?.getBoolean(STATE_CREATING) ?: false
        renderMode()

        binding.btnAccountToggle.setOnClickListener {
            creatingAccount = !creatingAccount
            renderMode()
        }
        binding.btnAccountAction.setOnClickListener { submit() }
        binding.btnGoogle.setOnClickListener { submitGoogle() }
        binding.btnGoogle.isEnabled = GoogleAccount.isConfigured
        binding.googleStatus.isVisible = !GoogleAccount.isConfigured
        binding.googleStatus.setText(R.string.account_google_pending)

        listOf(
            binding.nameLayout to binding.nameInput,
            binding.emailLayout to binding.emailInput,
            binding.passwordLayout to binding.passwordInput
        ).forEach { (layout, input) -> input.doAfterTextChanged { layout.error = null } }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CREATING, creatingAccount)
    }

    private fun renderMode() {
        val binding = _binding ?: return
        binding.nameLayout.isVisible = creatingAccount
        binding.accountTitle.setText(if (creatingAccount) R.string.account_sign_up else R.string.account_sign_in)
        binding.btnAccountAction.setText(if (creatingAccount) R.string.account_sign_up else R.string.account_sign_in)
        binding.btnAccountToggle.setText(
            if (creatingAccount) R.string.account_have_account else R.string.account_no_account
        )
    }

    private fun submit() {
        if (working) return
        val binding = _binding ?: return

        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()

        if (creatingAccount && name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.account_name_required)
            return
        }
        if (!email.contains("@") || email.length < 5) {
            binding.emailLayout.error = getString(R.string.account_email_invalid)
            return
        }
        if (password.length < MIN_PASSWORD) {
            binding.passwordLayout.error = getString(R.string.account_password_short)
            return
        }

        setWorking(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (creatingAccount) {
                AccountRepository.register(email, password, name)
            } else {
                AccountRepository.login(email, password)
            }
            if (_binding == null) return@launch

            when (result) {
                is ApiResult.Success -> {
                    parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                    dismiss()
                }

                is ApiResult.Failure -> {
                    setWorking(false)
                    showFailure(result)
                }
            }
        }
    }

    private fun submitGoogle() {
        if (working || !GoogleAccount.isConfigured) return
        val password = binding.passwordInput.text?.toString().orEmpty()
        setWorking(true)
        binding.googleStatus.isVisible = true
        binding.googleStatus.setText(R.string.account_google_working)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val challenge = AccountRepository.googleChallenge()
                if (challenge is ApiResult.Failure) {
                    showGoogleFailure(challenge.message ?: getString(R.string.account_offline))
                    return@launch
                }
                val nonce = (challenge as ApiResult.Success).data.optString("nonce")
                if (nonce.length !in 32..128) {
                    showGoogleFailure(getString(R.string.account_google_error))
                    return@launch
                }
                val token = GoogleAccount.idToken(requireActivity(), nonce)
                when (val result = AccountRepository.loginGoogle(token, nonce, password)) {
                    is ApiResult.Success -> {
                        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                        dismiss()
                    }
                    is ApiResult.Failure -> showGoogleFailure(result.message ?: getString(R.string.account_google_error))
                }
            } catch (_: GetCredentialCancellationException) {
                _binding?.googleStatus?.isVisible = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showGoogleFailure(getString(R.string.account_google_error))
            } finally {
                if (_binding != null) setWorking(false)
            }
        }
    }

    private fun showGoogleFailure(message: String) {
        val binding = _binding ?: return
        binding.googleStatus.isVisible = true
        binding.googleStatus.text = message
    }

    private fun showFailure(failure: ApiResult.Failure) {
        val binding = _binding ?: return
        // El servidor explica en su idioma por qué rechaza (correo en uso,
        // contraseña incorrecta...); si no dice nada, se usa un texto propio.
        val message = failure.message ?: when (failure.reason) {
            ApiFailure.NOT_CONFIGURED -> getString(R.string.account_no_server)
            ApiFailure.NETWORK, ApiFailure.SERVER -> getString(R.string.account_offline)
            ApiFailure.UNAUTHORIZED, ApiFailure.REJECTED -> getString(R.string.account_email_invalid)
        }
        binding.passwordLayout.error = message
    }

    private fun setWorking(busy: Boolean) {
        working = busy
        val binding = _binding ?: return
        binding.btnAccountAction.isEnabled = !busy
        binding.btnAccountToggle.isEnabled = !busy
        binding.btnGoogle.isEnabled = !busy && GoogleAccount.isConfigured
        binding.emailInput.isEnabled = !busy
        binding.passwordInput.isEnabled = !busy
        binding.nameInput.isEnabled = !busy
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "account"
        private const val STATE_CREATING = "creating"
        private const val MIN_PASSWORD = 8

        const val RESULT_KEY = "account_changed"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                AccountSheet().show(fragmentManager, TAG)
            }
        }
    }
}
