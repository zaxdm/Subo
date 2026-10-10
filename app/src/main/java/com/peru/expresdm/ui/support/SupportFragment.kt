package com.peru.expresdm.ui.support

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.BuildConfig
import com.peru.expresdm.R
import com.peru.expresdm.data.net.AccountRepository
import com.peru.expresdm.data.net.ApiClient
import com.peru.expresdm.data.net.ApiFailure
import com.peru.expresdm.data.net.ApiResult
import com.peru.expresdm.databinding.FragmentSupportBinding
import com.peru.expresdm.databinding.ItemFaqBinding
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Soporte: las dudas de siempre y un buzón para escribirnos.
 *
 * Con sesión abierta, el buzón deja la sugerencia en el servidor, donde la lee
 * el administrador. Sin sesión o sin servidor abre el correo del teléfono,
 * dirigido a la dirección de `SUPPORT_EMAIL` en local.properties.
 */
class SupportFragment : Fragment() {

    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!

    private class Faq(@StringRes val question: Int, @StringRes val answer: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        MAIN_FAQS.forEach { addFaq(it) }
        binding.btnMoreQuestions.setOnClickListener {
            MORE_FAQS.forEach { addFaq(it) }
            binding.btnMoreQuestions.isVisible = false
        }

        binding.subjectInput.setText(resources.getStringArray(R.array.support_feedback_subjects).first(), false)
        binding.btnSend.setOnClickListener { sendFeedback() }

        binding.appVersion.text = getString(R.string.support_version, BuildConfig.VERSION_NAME)
    }

    private fun addFaq(faq: Faq) {
        val item = ItemFaqBinding.inflate(layoutInflater, binding.faqContainer, false)
        item.faqQuestion.setText(faq.question)
        item.faqAnswer.setText(faq.answer)
        item.root.setOnClickListener {
            val open = !item.faqAnswer.isVisible
            item.faqAnswer.isVisible = open
            item.faqChevron.rotation = if (open) 90f else 0f
        }
        binding.faqContainer.addView(item.root)
    }

    private fun sendFeedback() {
        val message = binding.messageInput.text?.toString().orEmpty().trim()
        binding.messageLayout.error = getString(R.string.support_feedback_empty).takeIf { message.isEmpty() }
        if (message.isEmpty()) return

        val subject = binding.subjectInput.text.toString()
        val email = BuildConfig.SUPPORT_EMAIL
        when {
            ApiClient.isConfigured && AccountRepository.isSignedIn -> sendToServer(subject, message)
            // Sin sesión o sin servidor, el correo es el único camino que queda.
            email.isNotBlank() -> sendByEmail(email, subject, message)
            ApiClient.isConfigured -> showMessage(R.string.support_feedback_needs_account)
            else -> showMessage(R.string.support_feedback_no_address)
        }
    }

    /** La deja en el buzón del servidor, donde la lee el administrador. */
    private fun sendToServer(subject: String, message: String) {
        binding.btnSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val body = JSONObject()
                .put("subject", subject)
                .put("message", message)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            val result = ApiClient.post("/api/feedback", body, AccountRepository.token)

            val binding = _binding ?: return@launch
            binding.btnSend.isEnabled = true
            when (result) {
                is ApiResult.Success -> {
                    binding.messageInput.text = null
                    showMessage(R.string.support_feedback_sent)
                }
                is ApiResult.Failure -> when (result.reason) {
                    ApiFailure.UNAUTHORIZED -> showMessage(R.string.profile_name_session_expired)
                    ApiFailure.NETWORK -> showMessage(R.string.support_feedback_failed)
                    // El motivo que da el servidor ya viene listo para mostrar.
                    else -> Snackbar.make(
                        binding.root,
                        result.message ?: getString(R.string.support_feedback_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showMessage(@StringRes text: Int) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    /** Abre el correo con la sugerencia y la versión, que es lo primero que hace falta saber. */
    private fun sendByEmail(email: String, subject: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(R.string.support_email_subject, subject)
            )
            putExtra(
                Intent.EXTRA_TEXT,
                getString(
                    R.string.support_email_body,
                    message,
                    BuildConfig.VERSION_NAME,
                    Build.VERSION.RELEASE,
                    "${Build.MANUFACTURER} ${Build.MODEL}"
                )
            )
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.support_no_email_app, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val MAIN_FAQS = listOf(
            Faq(R.string.support_faq_save_q, R.string.support_faq_save_a),
            Faq(R.string.support_faq_report_q, R.string.support_faq_report_a),
            Faq(R.string.support_faq_assistant_q, R.string.support_faq_assistant_a)
        )

        /** Las que salen con "Ver más preguntas". */
        val MORE_FAQS = listOf(
            Faq(R.string.support_faq_arrival_q, R.string.support_faq_arrival_a),
            Faq(R.string.support_faq_location_q, R.string.support_faq_location_a)
        )
    }
}
