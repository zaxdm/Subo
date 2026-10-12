package com.peru.expresdm.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peru.expresdm.R
import com.peru.expresdm.ai.AssistantRepository
import com.peru.expresdm.ai.AssistantResult
import com.peru.expresdm.ai.GeminiClient
import com.peru.expresdm.data.BusRoute
import com.peru.expresdm.data.net.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Un mensaje de la conversación con el asistente. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val suggestedRoute: BusRoute? = null,
    val isError: Boolean = false
)

/**
 * Conversación con el asistente.
 *
 * Vive en el ViewModel para que el hilo no se pierda al rotar la pantalla ni al
 * cambiar de pestaña y volver.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private var nextId = 1L

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _premiumOffer = MutableStateFlow(false)

    /** Se acabaron las preguntas del día: toca enseñar lo que da Premium. */
    val premiumOffer: StateFlow<Boolean> = _premiumOffer.asStateFlow()

    fun premiumOfferShown() {
        _premiumOffer.value = false
    }

    /** Con servidor, la clave de Gemini está allí; sin él, tiene que estar en la app. */
    val isAssistantAvailable: Boolean get() = ApiClient.isConfigured || GeminiClient.isConfigured

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _isThinking.value) return

        // El historial que se manda al modelo debe capturarse antes de añadir
        // la pregunta nueva, que se envía aparte.
        val history = _messages.value
            .filterNot { it.isError }
            .map { GeminiClient.Turn(it.text, it.fromUser) }
            .takeLast(MAX_HISTORY_TURNS)

        append(ChatMessage(nextId++, text, fromUser = true))
        _isThinking.value = true

        viewModelScope.launch {
            val message = when (val result = AssistantRepository.ask(text, history)) {
                is AssistantResult.Success -> ChatMessage(
                    id = nextId++,
                    text = result.answer.text,
                    fromUser = false,
                    suggestedRoute = result.answer.suggestedRoute
                )

                is AssistantResult.Failure -> ChatMessage(
                    id = nextId++,
                    text = AssistantRepository.describeFailure(result.reason),
                    fromUser = false,
                    isError = true
                )

                is AssistantResult.LimitReached -> {
                    _premiumOffer.value = true
                    ChatMessage(
                        id = nextId++,
                        text = result.message
                            ?: getApplication<Application>().getString(R.string.assistant_daily_limit),
                        fromUser = false,
                        isError = true
                    )
                }

                // Con servidor, el asistente gasta su cuota de IA: hace falta cuenta.
                AssistantResult.NeedsAccount -> ChatMessage(
                    id = nextId++,
                    text = getApplication<Application>().getString(R.string.assistant_needs_account),
                    fromUser = false,
                    isError = true
                )
            }

            _isThinking.value = false
            append(message)
        }
    }

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private companion object {
        /** Suficiente contexto para seguir el hilo sin encarecer cada consulta. */
        const val MAX_HISTORY_TURNS = 8
    }
}
