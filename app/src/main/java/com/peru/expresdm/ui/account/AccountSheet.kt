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

    /** Lo que se está haciendo en la hoja. */
    private enum class Mode {
        SIGN_IN,
        SIGN_UP,

        /** Olvidó la contraseña: pide el correo para mandarle un código. */
        FORGOT,

        /** Ya tiene el código: lo escribe junto a la contraseña nueva. */
        RESET,

        /** Pidió la cuenta: falta el código que le llegó al correo para crearla. */
        CONFIRM
    }

    private var mode = Mode.SIGN_IN
    private val creatingAccount get() = mode == Mode.SIGN_UP
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

        mode = savedInstanceState?.getString(STATE_MODE)?.let { name -> Mode.entries.firstOrNull { it.name == name } }
            ?: Mode.SIGN_IN
        renderMode()

        binding.btnAccountToggle.setOnClickListener {
            // Desde recuperar la contraseña, este botón vuelve a iniciar sesión;
            // desde el código del registro, a los datos de la cuenta.
            mode = if (mode == Mode.SIGN_IN || mode == Mode.CONFIRM) Mode.SIGN_UP else Mode.SIGN_IN
            renderMode()
        }
        binding.btnForgot.setOnClickListener {
            mode = Mode.FORGOT
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
            binding.passwordLayout to binding.passwordInput,
            binding.codeLayout to binding.codeInput
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
        outState.putString(STATE_MODE, mode.name)
    }

    private fun renderMode() {
        val binding = _binding ?: return
        val recovering = mode == Mode.FORGOT || mode == Mode.RESET
        val confirming = mode == Mode.CONFIRM
        binding.nameLayout.isVisible = creatingAccount
        binding.codeLayout.isVisible = mode == Mode.RESET || confirming
        binding.passwordLayout.isVisible = mode != Mode.FORGOT && !confirming
        binding.passwordLayout.hint =
            getString(if (mode == Mode.RESET) R.string.account_reset_new_password else R.string.account_password)
        // El correo al que se mandó el código ya no se cambia.
        binding.emailInput.isEnabled = mode != Mode.RESET && !confirming
        binding.btnForgot.isVisible = mode == Mode.SIGN_IN
        binding.btnGoogle.isVisible = !recovering && !confirming
        binding.googleStatus.isVisible = !recovering && !confirming && !GoogleAccount.isConfigured

        val title = when (mode) {
            Mode.SIGN_IN -> R.string.account_sign_in
            Mode.SIGN_UP -> R.string.account_sign_up
            Mode.FORGOT, Mode.RESET -> R.string.account_reset_title
            Mode.CONFIRM -> R.string.account_confirm_title
        }
        binding.accountTitle.setText(title)
        binding.btnAccountAction.setText(
            when (mode) {
                Mode.FORGOT -> R.string.account_reset_send
                Mode.RESET -> R.string.account_reset_change
                Mode.CONFIRM -> R.string.account_confirm_action
                else -> title
            }
        )
        binding.btnAccountToggle.setText(
            when (mode) {
                Mode.SIGN_IN -> R.string.account_no_account
                Mode.SIGN_UP -> R.string.account_have_account
                Mode.FORGOT, Mode.RESET -> R.string.account_reset_back
                Mode.CONFIRM -> R.string.account_confirm_back
            }
        )
    }

    /** Pide que se mande el código al correo y pasa a esperar que lo escriban. */
    private fun requestCode(email: String) {
        setWorking(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AccountRepository.forgotPassword(email)
            val binding = _binding ?: return@launch
            setWorking(false)
            when (result) {
                is ApiResult.Success -> {
                    mode = Mode.RESET
                    renderMode()
                    binding.codeLayout.helperText = getString(R.string.account_reset_sent, email)
                }
                is ApiResult.Failure -> binding.emailLayout.error =
                    result.message ?: getString(R.string.account_offline)
            }
        }
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
        if (mode == Mode.FORGOT) {
            requestCode(email)
            return
        }
        val code = binding.codeInput.text?.toString()?.trim().orEmpty()
        if ((mode == Mode.RESET || mode == Mode.CONFIRM) && code.length != 6) {
            binding.codeLayout.error = getString(R.string.account_reset_code_invalid)
            return
        }
        // Al confirmar el correo, la contraseña ya se mandó con el registro.
        if (mode != Mode.CONFIRM && password.length < MIN_PASSWORD) {
            binding.passwordLayout.error = getString(R.string.account_password_short)
            return
        }

        setWorking(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = when (mode) {
                Mode.SIGN_UP -> AccountRepository.register(email, password, name)
                Mode.RESET -> AccountRepository.resetPassword(email, code, password)
                Mode.CONFIRM -> AccountRepository.confirmRegistration(email, code)
                else -> AccountRepository.login(email, password)
            }
            val binding = _binding ?: return@launch

            when (result) {
                is ApiResult.Success -> if (result.data == false) {
                    // La cuenta todavía no existe: falta el código que se le mandó.
                    mode = Mode.CONFIRM
                    setWorking(false)
                    renderMode()
                    binding.codeInput.setText("")
                    binding.codeLayout.helperText = getString(R.string.account_confirm_sent, email)
                } else {
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
        // Al confirmar el correo no hay contraseña a la vista: el aviso va en el código.
        val field = if (mode == Mode.CONFIRM) binding.codeLayout else binding.passwordLayout
        field.error = message
    }

    private fun setWorking(busy: Boolean) {
        working = busy
        val binding = _binding ?: return
        binding.btnAccountAction.isEnabled = !busy
        binding.btnAccountToggle.isEnabled = !busy
        binding.btnForgot.isEnabled = !busy
        binding.codeInput.isEnabled = !busy
        binding.btnGoogle.isEnabled = !busy && GoogleAccount.isConfigured
        binding.emailInput.isEnabled = !busy && mode != Mode.RESET && mode != Mode.CONFIRM
        binding.passwordInput.isEnabled = !busy
        binding.nameInput.isEnabled = !busy
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "account"
        private const val STATE_MODE = "mode"
        private const val MIN_PASSWORD = 8

        const val RESULT_KEY = "account_changed"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                AccountSheet().show(fragmentManager, TAG)
            }
        }
    }
}
