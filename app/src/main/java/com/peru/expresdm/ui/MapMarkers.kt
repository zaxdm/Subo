package com.peru.expresdm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.peru.expresdm.R
import kotlin.math.roundToInt

/** Cómo se destaca una parada numerada en el mapa. */
enum class StopMarkerStyle {
    /** Parada intermedia. */
    NORMAL,

    /** Origen o destino de la ruta. */
    ENDPOINT,

    /** La parada donde sube el usuario. */
    SELECTED
}

/**
 * Marcadores del mapa con los colores de la app.
 *
 * Sustituyen a los de osmdroid por defecto (un pin verde con una mano), que no
 * encajan con la paleta.
 */
object MapMarkers {

    /** Punto de color con borde blanco: la parada donde subir a una ruta. */
    fun stopDot(context: Context, @ColorInt color: Int): Drawable {
        val size = context.dp(18f)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(context.dp(3f), Color.WHITE)
            setSize(size, size)
        }
    }

    /** Parada con su número dentro, en el tamaño que marque [style]. */
    fun numberedStop(
        context: Context,
        number: Int,
        @ColorInt color: Int,
        style: StopMarkerStyle
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val diameterDp = when (style) {
            StopMarkerStyle.NORMAL -> 22f
            StopMarkerStyle.ENDPOINT -> 28f
            StopMarkerStyle.SELECTED -> 44f
        }
        val size = (diameterDp * density).roundToInt()
        val center = size / 2f

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var radius = center
        if (style == StopMarkerStyle.SELECTED) {
            // Halo alrededor de la parada donde sube el usuario.
            paint.color = color
            paint.alpha = 64
            canvas.drawCircle(center, center, center, paint)
            radius = center * 0.66f
        }

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, radius, paint)

        paint.color = color
        canvas.drawCircle(center, center, radius - 2.5f * density, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = radius * 0.9f
        val baseline = center - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(number.toString(), center, baseline, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /** Alerta: círculo del color de su tipo con el icono dentro y un halo suave. */
    fun alert(context: Context, @DrawableRes icon: Int, @ColorInt color: Int): Drawable =
        iconBadge(context, icon, color, diameterDp = 36f, haloAlpha = 70)

    /** Lugar buscado. El elegido se dibuja más grande que el resto de resultados. */
    fun place(context: Context, chosen: Boolean): Drawable {
        val color = context.themeColor(com.google.android.material.R.attr.colorSecondary)
        return iconBadge(
            context,
            R.drawable.ic_place,
            color,
            diameterDp = if (chosen) 46f else 32f,
            haloAlpha = if (chosen) 70 else 0
        )
    }

    /** Un lugar favorito, con el icono de lo que es (casa, trabajo…) sobre el amarillo de la marca. */
    fun favorite(context: Context, @DrawableRes icon: Int): Drawable =
        iconBadge(
            context,
            icon,
            context.getColor(R.color.brand),
            diameterDp = 34f,
            haloAlpha = 0,
            iconTint = context.getColor(R.color.on_brand)
        )

    /** El punto cuya calle se está viendo en el panel de arriba. */
    fun streetView(context: Context): Drawable {
        val color = context.themeColor(com.google.android.material.R.attr.colorPrimary)
        return iconBadge(context, R.drawable.ic_streetview, color, diameterDp = 46f, haloAlpha = 70)
    }

    /** Dónde se baja el usuario: el punto que eligió tocando el recorrido. */
    fun dropOff(context: Context): Drawable {
        val color = context.themeColor(com.google.android.material.R.attr.colorSecondary)
        return iconBadge(context, R.drawable.ic_arrival_alarm, color, diameterDp = 46f, haloAlpha = 70)
    }

    private fun iconBadge(
        context: Context,
        @DrawableRes icon: Int,
        @ColorInt color: Int,
        diameterDp: Float,
        haloAlpha: Int,
        @ColorInt iconTint: Int = Color.WHITE
    ): Drawable {
        val size = context.dp(diameterDp)
        val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val badgeRadius = if (haloAlpha > 0) center * 0.76f else center
        if (haloAlpha > 0) {
            paint.color = color
            paint.alpha = haloAlpha
            canvas.drawCircle(center, center, center, paint)
        }

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, badgeRadius, paint)
        paint.color = color
        canvas.drawCircle(center, center, badgeRadius - context.dp(2.5f), paint)

        AppCompatResources.getDrawable(context, icon)?.mutate()?.let { drawable ->
            DrawableCompat.setTint(drawable, iconTint)
            val half = (badgeRadius * 0.55f).roundToInt()
            val c = center.roundToInt()
            drawable.setBounds(c - half, c - half, c + half, c + half)
            drawable.draw(canvas)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    /** Punto azul del usuario, con halo, como en las apps de mapas habituales. */
    fun userDot(context: Context): Bitmap {
        val primary = context.themeColor(com.google.android.material.R.attr.colorPrimary)
        val size = context.dp(36f)
        val center = size / 2f

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = primary
        paint.alpha = 64
        canvas.drawCircle(center, center, center, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, context.dp(9f).toFloat(), paint)

        paint.color = primary
        canvas.drawCircle(center, center, context.dp(6.5f).toFloat(), paint)

        return bitmap
    }

    private fun Context.dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
