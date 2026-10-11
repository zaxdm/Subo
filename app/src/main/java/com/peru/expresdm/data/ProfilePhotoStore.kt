package com.peru.expresdm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La foto de perfil de cada cuenta.
 *
 * Vive solo en este teléfono, en el almacenamiento privado de la app: no se
 * sube al servidor. Se guarda ya recortada y pequeña, una por cuenta, para que
 * dos personas que comparten el teléfono no se pisen la foto.
 */
object ProfilePhotoStore {

    private const val TAG = "ProfilePhoto"
    private const val SIZE_PX = 512
    private const val JPEG_QUALITY = 88

    private fun file(context: Context, userId: String): File {
        val folder = File(context.filesDir, "avatars").apply { mkdirs() }
        // El id viene del servidor: se limpia para que nunca salga de la carpeta.
        return File(folder, userId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")
    }

    suspend fun load(context: Context, userId: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(context, userId)
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** Recorta la imagen elegida a un cuadrado y la guarda. Devuelve la foto, o `null` si no se pudo leer. */
    suspend fun save(context: Context, userId: String, source: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val photo = centerSquare(decode(context, source) ?: return@withContext null)
            file(context, userId).outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            photo
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar la foto de perfil", e)
            null
        }
    }

    suspend fun delete(context: Context, userId: String) = withContext(Dispatchers.IO) {
        file(context, userId).delete()
    }

    private fun decode(context: Context, source: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder ya endereza las fotos que la cámara guarda giradas.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val smallest = minOf(info.size.width, info.size.height)
                // Se reduce al leerla: una foto de cámara entera no cabe en memoria.
                if (smallest > SIZE_PX) decoder.setTargetSampleSize(smallest / SIZE_PX)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val smallest = minOf(bounds.outWidth, bounds.outHeight)
        if (smallest <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = (smallest / SIZE_PX).coerceAtLeast(1) }
        return context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        return if (side > SIZE_PX) Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true) else square
    }
}
