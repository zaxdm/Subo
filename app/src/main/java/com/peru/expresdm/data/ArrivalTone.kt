package com.peru.expresdm.data

/** Los sonidos del aviso de bajada que trae la app. */
enum class ArrivalTone(val key: String) {
    BELL("bell"),
    CHIME("chime"),
    BEEP("beep"),
    SIREN("siren"),
    MELODY("melody");

    companion object {
        val DEFAULT = BELL

        /** `null` si la clave no es de un tono de la app, sino un sonido que añadió el usuario. */
        fun fromKey(key: String?): ArrivalTone? = entries.firstOrNull { it.key == key }
    }
}

/** Un sonido que el usuario eligió de su teléfono. */
data class CustomSound(
    /** Dirección del archivo, tal como la entrega el selector de Android. */
    val uri: String,
    val name: String
)
