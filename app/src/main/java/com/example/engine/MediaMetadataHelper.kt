package com.example.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.abs
import kotlin.math.sin

object MediaMetadataHelper {

    fun getFileName(context: Context, uri: Uri, fallback: String = "Media"): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: fallback
    }

    fun getMediaDurationSec(context: Context, uri: Uri, defaultSec: Float = 10f): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return defaultSec
            (durMs / 1000f).coerceAtLeast(1f)
        } catch (_: Exception) {
            defaultSec
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun buildWaveformProfile(seedKey: String, bins: Int = 48): List<Float> {
        val seed = abs(seedKey.hashCode())
        return List(bins) { idx ->
            val phase = idx * 0.42f + (seed % 17)
            val v = 0.3f + 0.55f * abs(sin(phase)) * (0.6f + 0.4f * abs(sin(phase * 0.37f)))
            v.coerceIn(0.18f, 1f)
        }
    }
}
