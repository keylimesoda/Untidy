package com.tidal.wear.ui.player

import com.tidal.wear.core.playback.DeliveredAudioFormat
import java.util.Locale

internal enum class DeliveredQualityTier { Low, High, Max, Unknown }

internal data class QualityGemPresentation(
    val label: String,
    val tier: DeliveredQualityTier,
)

/** Builds a compact label from values reported by the selected decoder format; nothing is guessed. */
internal fun DeliveredAudioFormat?.toQualityGemPresentation(): QualityGemPresentation? {
    val format = this ?: return null
    val identity = listOfNotNull(format.codec, format.container).joinToString(" ").lowercase(Locale.US)
    val isLossless = identity.contains("flac") || identity.contains("alac")
    val tier = when {
        isLossless && ((format.bitDepth ?: 0) > 16 || (format.sampleRateHz ?: 0) > 48_000) -> DeliveredQualityTier.Max
        isLossless -> DeliveredQualityTier.High
        identity.contains("aac") || identity.contains("mp4a") || identity.contains("audio/mp4") -> DeliveredQualityTier.Low
        else -> DeliveredQualityTier.Unknown
    }

    val parts = buildList {
        format.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
        if (format.bitDepth == null) {
            format.bitrate?.takeIf { it > 0 }?.let { add("${it / 1_000}kbps") }
        }
        format.sampleRateHz?.takeIf { it > 0 }?.let {
            add(String.format(Locale.US, "%.1fkHz", it / 1_000.0))
        }
    }
    if (parts.isEmpty()) return null
    return QualityGemPresentation(label = parts.joinToString(" "), tier = tier)
}
