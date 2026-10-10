package com.peru.expresdm.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.peru.expresdm.R
import com.peru.expresdm.databinding.FragmentAssistantBinding
import com.peru.expresdm.ui.MapRouteRequests
import kotlinx.coroutines.launch

/**
 * Chat con el asistente, abierto desde la burbuja del mapa.
 *
 * La conversación vive en un ViewModel de la Activity: cerrar la burbuja y
 * volver a abrirla no borra lo que ya se habló. "Ver ruta" cierra el chat y
 * traza la ruta en el mapa.
 */
class AssistantSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAssistantBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AssistantViewModel by activityViewModels()

    private val adapter by lazy {
        AssistantAdapter(
            onRouteClick = { route ->
                MapRouteRequests.request(route.id)
                dismiss()
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.messagesRecycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messagesRecycler.adapter = adapter

        setUpSuggestions()

        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }

        if (!viewModel.isAssistantAvailable) {
            Snackbar.make(binding.root, R.string.assistant_disabled, Snackbar.LENGTH_LONG).show()
        }

        observeConversation()
    }

    override fun onStart() {
        super.onStart()
        // El teclado empuja la caja de texto hacia arriba en lugar de taparla.
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** Preguntas de ejemplo para que la primera vez no haya que pensar qué escribir. */
    private fun setUpSuggestions() {
        val suggestions = listOf(
            R.string.assistant_suggestion_1,
            R.string.assistant_suggestion_2,
            R.string.assistant_suggestion_3
        )

        suggestions.forEach { resId ->
            val chip = Chip(requireContext()).apply {
                text = getString(resId)
                isCheckable = false
                setOnClickListener {
                    binding.messageInput.setText(getString(resId))
                    sendCurrentInput()
                }
            }
            binding.suggestions.addView(chip)
        }
    }

    private fun sendCurrentInput() {
        val text = binding.messageInput.text?.toString().orEmpty()
        if (text.isBlank()) return

        viewModel.send(text)
        binding.messageInput.text?.clear()
        hideKeyboard()
    }

    private fun observeConversation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        val shown = if (messages.isEmpty()) listOf(welcomeMessage()) else messages
                        adapter.submitList(shown) {
                            _binding?.messagesRecycler?.scrollToPosition(shown.lastIndex)
                        }
                        // Las sugerencias solo estorban una vez hay conversación.
                        binding.suggestionsScroll.visibility =
                            if (messages.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isThinking.collect { thinking ->
                        binding.btnSend.isEnabled = !thinking
                        binding.messageInput.isEnabled = !thinking
                        binding.messageInput.hint = getString(
                            if (thinking) R.string.assistant_thinking else R.string.assistant_hint
                        )
                    }
                }
            }
        }
    }

    private fun welcomeMessage() = ChatMessage(
        id = 0L,
        text = getString(R.string.assistant_welcome),
        fromUser = false
    )

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
    }

    override fun onDestroyView() {
        binding.messagesRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "assistant"
        private const val SHEET_HEIGHT_RATIO = 0.88

        /** Abre el chat, salvo que ya esté abierto (dos toques rápidos a la burbuja). */
        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                AssistantSheet().show(fragmentManager, TAG)
            }
        }
    }
}
