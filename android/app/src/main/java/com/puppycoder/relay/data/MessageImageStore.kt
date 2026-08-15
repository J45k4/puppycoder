package com.puppycoder.relay.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal class MessageImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, "message-images")

    suspend fun import(messageId: String, uriValues: List<String>): List<MessageImage> =
        withContext(Dispatchers.IO) {
            val uniqueUris = uriValues.distinct()
            require(uniqueUris.size <= MAX_IMAGES) { "You can attach up to $MAX_IMAGES images" }
            val imported = mutableListOf<MessageImage>()
            try {
                uniqueUris.forEachIndexed { index, value ->
                    imported += importOne(messageId, Uri.parse(value), index)
                }
                require(imported.sumOf(MessageImage::sizeBytes) <= MAX_TOTAL_STORED_BYTES) {
                    "The combined images are too large to send"
                }
                imported
            } catch (error: Throwable) {
                imported.forEach(::delete)
                throw error
            }
        }

    fun delete(image: MessageImage) {
        runCatching { File(image.filePath).delete() }
    }

    private fun importOne(messageId: String, uri: Uri, index: Int): MessageImage {
        val resolver = appContext.contentResolver
        val sourceName = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank) ?: "image-${index + 1}"

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("Could not open $sourceName")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "$sourceName is not a supported image" }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Could not decode $sourceName")

        val largestSide = max(decoded.width, decoded.height)
        val output = if (largestSide > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / largestSide
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        imageDirectory.mkdirs()
        check(imageDirectory.isDirectory) { "Could not create private image storage" }
        val preserveAlpha = output.hasAlpha()
        val mimeType = if (preserveAlpha) "image/png" else "image/jpeg"
        val extension = if (preserveAlpha) "png" else "jpg"
        val target = File(imageDirectory, "${UUID.randomUUID()}.$extension")
        val outputWidth = output.width
        val outputHeight = output.height
        try {
            target.outputStream().buffered().use { stream ->
                val format = if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                check(output.compress(format, JPEG_QUALITY, stream)) { "Could not save $sourceName" }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            output.recycle()
        }
        if (target.length() > MAX_STORED_BYTES) {
            target.delete()
            error("$sourceName is too large after resizing")
        }

        return MessageImage(
            messageId = messageId,
            mimeType = mimeType,
            filePath = target.absolutePath,
            fileName = sourceName.take(MAX_FILE_NAME_LENGTH),
            sizeBytes = target.length(),
            width = outputWidth,
            height = outputHeight,
            createdAt = System.currentTimeMillis() + index,
        )
    }

    private companion object {
        const val MAX_IMAGES = 4
        const val MAX_DIMENSION = 2_048
        const val MAX_STORED_BYTES = 10L * 1024 * 1024
        const val MAX_TOTAL_STORED_BYTES = 8L * 1024 * 1024
        const val MAX_FILE_NAME_LENGTH = 160
        const val JPEG_QUALITY = 88
    }
}
