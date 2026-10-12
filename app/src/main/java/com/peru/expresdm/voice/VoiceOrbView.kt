package com.peru.expresdm.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * La cara de la voz de Subo: una esfera de luz en el centro, esferas
 * concéntricas que laten, anillos de datos que giran a su alrededor y líneas
 * de código que cambian a los lados. Todo se mueve al ritmo de la voz: de la
 * del usuario mientras escucha y de la del asistente mientras habla.
 *
 * Se dibuja sola mientras está a la vista; al ocultarla deja de animarse.
 *
 * Se adapta a su tamaño: en una franja ancha caben las líneas de código a los
 * lados; en [compact], pensada para un círculo pequeño sobre el mapa, quedan
 * solo la esfera y sus anillos, sin fondo propio.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Qué está pasando: decide los colores. */
    enum class Mode { LISTENING, THINKING, SPEAKING }

    var mode: Mode = Mode.LISTENING
        set(value) {
            field = value
            invalidate()
        }

    /** Solo la esfera y los anillos, sin escenario ni código: para pintarla en pequeño. */
    var compact = false
        set(value) {
            field = value
            invalidate()
        }

    /** Hacia dónde va la intensidad, de 0 (silencio) a 1 (voz fuerte). */
    private var target = 0f

    /** La intensidad que se pinta: sigue a [target] con suavidad, para que no dé saltos. */
    private var level = 0f

    private val density = resources.displayMetrics.density
    private val started = SystemClock.uptimeMillis()
    private var lastFrame = started
    private var lastCodeChange = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 10f * density
    }
    private val oval = RectF()

    /** El grosor de los trazos y puntos: se afina cuando la esfera es pequeña. */
    private var unit = density

    private val random = Random(7)
    private val codeLeft = MutableList(CODE_LINES) { randomCode() }
    private val codeRight = MutableList(CODE_LINES) { randomCode() }

    /** Los puntos de datos que flotan en cada anillo: su posición de salida, en vueltas. */
    private val dots = List(RINGS.size) { ring -> List(5 + ring * 2) { random.nextFloat() } }

    /**
     * La voz que se oye ahora, de 0 a 1. Se llama muchas veces por segundo
     * mientras se escucha.
     */
    fun setLevel(value: Float) {
        target = value.coerceIn(0f, 1f)
    }

    /** Un golpe de voz (una palabra del asistente): sube de golpe y vuelve a caer sola. */
    fun pulse(strength: Float = 0.85f) {
        target = strength
        level = maxOf(level, strength * 0.7f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val seconds = (now - started) / 1000f
        val delta = ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now

        // La intensidad sube rápido y baja despacio, como un medidor de sonido.
        val speed = if (target > level) 14f else 4f
        level += (target - level) * (speed * delta).coerceAtMost(1f)
        // Sin voz nueva, la meta se va apagando: un pulso no se queda arriba.
        target *= (1f - 2.2f * delta).coerceAtLeast(0f)

        // Aun en silencio respira un poco: se ve viva, no congelada.
        val breath = 0.06f + 0.04f * sin(seconds * 2.2f)
        val energy = (level + breath).coerceAtMost(1f)

        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        // Los anillos llegan a 3,5 veces la esfera: con esto caben enteros.
        val base = size * 0.125f
        unit = density * (size / (FULL_SIZE_DP * density)).coerceIn(0.4f, 1f)
        val (core, glow, accent) = colors()

        if (!compact) {
            drawBackdrop(canvas, cx, cy, glow)
            drawCode(canvas, now, energy, accent)
        }
        drawSpheres(canvas, cx, cy, base, energy, seconds, glow)
        drawRings(canvas, cx, cy, base, energy, seconds, accent)
        drawCore(canvas, cx, cy, base, energy, core, glow)

        if (isShown) postInvalidateOnAnimation()
    }

    private fun colors(): Triple<Int, Int, Int> = when (mode) {
        Mode.LISTENING -> Triple(Color.WHITE, COLOR_GOLD, COLOR_CYAN)
        Mode.THINKING -> Triple(Color.WHITE, COLOR_VIOLET, COLOR_CYAN)
        Mode.SPEAKING -> Triple(Color.WHITE, COLOR_CYAN, COLOR_GOLD)
    }

    /** El halo de fondo: una luz tenue que tiñe el escenario del color de la esfera. */
    private fun drawBackdrop(canvas: Canvas, cx: Float, cy: Float, glow: Int) {
        canvas.drawColor(COLOR_STAGE)
        val radius = min(width, height) * 0.75f
        fill.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(withAlpha(glow, 60), withAlpha(glow, 14), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fill)
        fill.shader = null
    }

    /** Las líneas de código de los lados: cambian más deprisa cuanto más fuerte es la voz. */
    private fun drawCode(canvas: Canvas, now: Long, energy: Float, accent: Int) {
        val every = (220 - 170 * energy).toLong().coerceAtLeast(40)
        if (now - lastCodeChange > every) {
            lastCodeChange = now
            // Cada vez cambian unas pocas, no todas: parece que se escribe.
            repeat(2) {
                codeLeft[random.nextInt(CODE_LINES)] = randomCode()
                codeRight[random.nextInt(CODE_LINES)] = randomCode()
            }
        }
        val lineHeight = text.textSize * 1.55f
        // Solo las líneas que quepan en la altura que hay.
        val lines = min(CODE_LINES, (height / lineHeight).toInt() - 1)
        if (lines < 3) return
        val top = height / 2f - lineHeight * lines / 2f - text.textSize * 0.3f
        val margin = 14f * density
        for (index in 0 until lines) {
            // Más tenues arriba y abajo, para que no corten contra el borde.
            val middle = 1f - kotlin.math.abs(index - (lines - 1) / 2f) / (lines / 2f)
            text.color = withAlpha(accent, (40 + 110 * middle * (0.5f + energy * 0.5f)).toInt())
            val y = top + lineHeight * (index + 1)
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(codeLeft[index], margin, y, text)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(codeRight[index], width - margin, y, text)
        }
    }

    /** Las esferas concéntricas: capas translúcidas que se abren con la voz, cada una a su tiempo. */
    private fun drawSpheres(canvas: Canvas, cx: Float, cy: Float, base: Float, energy: Float, seconds: Float, glow: Int) {
        for (index in SPHERES downTo 1) {
            val wave = sin(seconds * 3f - index * 0.9f) * 0.5f + 0.5f
            val radius = base * (1.15f + index * 0.42f) * (1f + energy * (0.22f + 0.1f * wave))
            fill.color = withAlpha(glow, (26 - index * 5).coerceAtLeast(6))
            canvas.drawCircle(cx, cy, radius, fill)
            stroke.pathEffect = null
            stroke.strokeWidth = 1.2f * unit
            stroke.color = withAlpha(glow, (120 - index * 28 + 60 * energy).toInt().coerceIn(20, 255))
            canvas.drawCircle(cx, cy, radius, stroke)
        }
    }

    /**
     * Los anillos de datos: elipses inclinadas que giran alrededor de la esfera,
     * hechas de trazos sueltos y con puntos que las recorren.
     */
    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, base: Float, energy: Float, seconds: Float, accent: Int) {
        RINGS.forEachIndexed { index, ring ->
            val radius = base * ring.size * (1f + energy * 0.12f)
            val turn = seconds * ring.speed * (1f + energy * 1.5f)
            canvas.save()
            canvas.rotate(ring.tilt + seconds * ring.drift, cx, cy)
            oval.set(cx - radius, cy - radius * ring.flatten, cx + radius, cy + radius * ring.flatten)

            stroke.strokeWidth = ring.width * unit
            stroke.color = withAlpha(accent, (90 + 130 * energy).toInt().coerceAtMost(255))
            // Trazos sueltos en vez de una línea entera: parecen bloques de datos.
            val segments = ring.segments
            val sweep = 360f / segments
            for (segment in 0 until segments) {
                val length = sweep * (0.35f + 0.4f * ((segment * 7 + index * 3) % 5) / 4f)
                canvas.drawArc(oval, turn * 57.3f + segment * sweep, length, false, stroke)
            }

            fill.color = withAlpha(Color.WHITE, (150 + 100 * energy).toInt().coerceAtMost(255))
            dots[index].forEach { offset ->
                val angle = (offset * 2f * PI.toFloat()) - turn * 1.7f
                canvas.drawCircle(
                    cx + cos(angle) * radius,
                    cy + sin(angle) * radius * ring.flatten,
                    (1.4f + 1.6f * energy) * unit,
                    fill
                )
            }
            canvas.restore()
        }
    }

    /** La esfera de luz: un núcleo blanco que se funde en su color y crece con la voz. */
    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, base: Float, energy: Float, core: Int, glow: Int) {
        val radius = base * (0.85f + energy * 0.55f)
        val halo = radius * 2.3f
        fill.shader = RadialGradient(
            cx, cy, halo,
            intArrayOf(core, withAlpha(glow, 235), withAlpha(glow, 90), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, halo, fill)
        fill.shader = null
        fill.color = withAlpha(core, 235)
        canvas.drawCircle(cx, cy, radius * 0.42f, fill)
    }

    private fun randomCode(): String {
        val snippet = CODE_SNIPPETS[random.nextInt(CODE_SNIPPETS.size)]
        val number = random.nextInt(0x10000).toString(16).uppercase().padStart(4, '0')
        return if (random.nextBoolean()) "$snippet 0x$number" else "$number:${random.nextInt(100, 999)} $snippet"
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /** Un anillo de datos: su tamaño respecto a la esfera, cuánto se aplana, su inclinación y su giro. */
    private class Ring(
        val size: Float,
        val flatten: Float,
        val tilt: Float,
        val speed: Float,
        val drift: Float,
        val segments: Int,
        val width: Float
    )

    private companion object {
        const val SPHERES = 3
        const val CODE_LINES = 9

        /** Desde este tamaño (en dp) los trazos se pintan con todo su grosor. */
        const val FULL_SIZE_DP = 220f

        val COLOR_STAGE = Color.rgb(8, 12, 24)
        val COLOR_GOLD = Color.rgb(255, 196, 37)
        val COLOR_CYAN = Color.rgb(64, 220, 255)
        val COLOR_VIOLET = Color.rgb(150, 120, 255)

        val RINGS = listOf(
            Ring(size = 2.5f, flatten = 0.34f, tilt = -18f, speed = 0.9f, drift = 4f, segments = 9, width = 2.2f),
            Ring(size = 3.0f, flatten = 0.5f, tilt = 32f, speed = -0.6f, drift = -3f, segments = 14, width = 1.4f),
            Ring(size = 3.5f, flatten = 0.22f, tilt = 78f, speed = 0.4f, drift = 2f, segments = 20, width = 1f)
        )

        val CODE_SNIPPETS = listOf(
            "ruta.sync", "gps.fix", "voz.rx", "nodo", "eta++", "parada[i]", "lat,lon", "micro.id",
            "seq", "ack", "tx", "ping", "if(ok)", "0b1011", "map.tile", "audio.fft"
        )
    }
}
