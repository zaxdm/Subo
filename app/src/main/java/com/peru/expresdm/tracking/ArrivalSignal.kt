package com.peru.expresdm.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RawRes
import androidx.core.content.getSystemService
import com.peru.expresdm.R
import com.peru.expresdm.data.AppSettings
import com.peru.expresdm.data.ArrivalTone

/**
 * La vibración y el sonido del aviso de bajada.
 *
 * Van aparte de la notificación porque un canal de Android no deja cambiar su
 * sonido ni su vibración una vez creado, y aquí el usuario los enciende, los
 * apaga y elige el tono desde Ajustes. Así también se puede probar sin esperar
 * a llegar a una parada.
 */
object ArrivalSignal {

    private const val TAG = "ArrivalSignal"

    /** Un sonido propio puede ser una canción entera: se corta aquí. */
    private const val MAX_SOUND_MS = 6_000L

    /** Tres golpes largos: se notan con el teléfono en el bolsillo y el micro en marcha. */
    private val VIBRATION_TIMINGS = longArrayOf(0, 600, 200, 600, 200, 900)
    private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopSound = Runnable {
        player?.release()
        player = null
    }

    fun play(context: Context, vibrate: Boolean, sound: Boolean) {
        val appContext = context.applicationContext
        if (vibrate) vibrate(appContext)
        if (sound) handler.post { ring(appContext, AppSettings.get(appContext).arrivalSoundChoice) }
    }

    /** Corta el sonido que esté sonando, si hay alguno. */
    fun stop() {
        handler.removeCallbacks(stopSound)
        handler.post(stopSound)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            context.getSystemService<Vibrator>()
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_TIMINGS, -1)
        }
    }

    private fun ring(context: Context, choice: String) {
        handler.removeCallbacks(stopSound)
        stopSound.run()

        val tone = ArrivalTone.fromKey(choice)
        val started = if (tone != null) {
            start(context) { it.setRawSource(context, resourceOf(tone)) }
        } else {
            start(context) { it.setDataSource(context, Uri.parse(choice)) }
        }
        // Un sonido propio puede haberse borrado del teléfono: el aviso no se
        // queda mudo por eso.
        if (!started && tone == null) {
            start(context) { it.setRawSource(context, resourceOf(ArrivalTone.DEFAULT)) }
        }
    }

    private fun start(context: Context, setSource: (MediaPlayer) -> Unit): Boolean {
        val media = MediaPlayer()
        return try {
            // Como alarma suena aunque el teléfono esté en silencio, con el
            // volumen de alarmas: de eso se trata un aviso para bajarse.
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setSource(media)
            media.setOnCompletionListener { stopSound.run() }
            media.prepare()
            media.start()
            player = media
            handler.postDelayed(stopSound, MAX_SOUND_MS)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo reproducir el sonido del aviso", e)
            media.release()
            false
        }
    }

    private fun MediaPlayer.setRawSource(context: Context, @RawRes resource: Int) {
        context.resources.openRawResourceFd(resource).use { file ->
            setDataSource(file.fileDescriptor, file.startOffset, file.length)
        }
    }

    @RawRes
    private fun resourceOf(tone: ArrivalTone): Int = when (tone) {
        ArrivalTone.BELL -> R.raw.arrival_bell
        ArrivalTone.CHIME -> R.raw.arrival_chime
        ArrivalTone.BEEP -> R.raw.arrival_beep
        ArrivalTone.SIREN -> R.raw.arrival_siren
        ArrivalTone.MELODY -> R.raw.arrival_melody
    }
}
