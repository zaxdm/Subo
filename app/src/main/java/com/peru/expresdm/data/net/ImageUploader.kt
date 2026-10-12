package com.peru.expresdm.data.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Sube al servidor una foto elegida por el usuario.
 *
 * Antes de mandarla se reduce: una foto de cámara pesa varios megas y aquí
 * basta con que se vea bien en el teléfono. El servidor la revisa antes de
 * guardarla y devuelve su dirección, o el motivo por el que no la acepta.
 */
object ImageUploader {

    private const val TAG = "ImageUploader"
    private const val JPEG_QUALITY = 82

    /** Para qué es la foto: el servidor es más o menos exigente según el caso. */
    enum class Purpose(val key: String, val maxSidePx: Int) {
        ALERT("alert", 1280),
        AVATAR("avatar", 512),

        /** La captura de un pago: tiene que poder leerse el monto y la confirmación. */
        PAYMENT("payment", 1600)
    }

    suspend fun upload(context: Context, source: Uri, purpose: Purpose): ApiResult<String> {
        val bytes = withContext(Dispatchers.IO) { compress(context, source, purpose.maxSidePx) }
            ?: return ApiResult.Failure(ApiFailure.REJECTED)

        val language = Locale.getDefault().language
        val path = "/api/images?purpose=${purpose.key}&language=$language"
        return when (val result = ApiClient.postBytes(path, bytes, "image/jpeg", AccountRepository.token)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.data.optString("url").takeIf { it.isNotBlank() }
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiFailure.SERVER)
        }
    }

    /** La imagen como JPEG, con el lado mayor recortado a [maxSide]. `null` si no se pudo leer. */
    private fun compress(context: Context, source: Uri, maxSide: Int): ByteArray? = try {
        val bitmap = decode(context, source, maxSide)?.let { scaleDown(it, maxSide) }
        bitmap?.let {
            ByteArrayOutputStream().apply { it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, this) }.toByteArray()
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo preparar la foto", e)
        null
    }

    private fun decode(context: Context, source: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder ya endereza las fotos que la cámara guarda giradas.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = maxOf(info.size.width, info.size.height)
                if (largest > maxSide) decoder.setTargetSampleSize(largest / maxSide)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = (largest / maxSide).coerceAtLeast(1) }
        return context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }
}
