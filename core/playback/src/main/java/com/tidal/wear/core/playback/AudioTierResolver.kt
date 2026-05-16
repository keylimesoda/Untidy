package com.tidal.wear.core.playback

import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.EffectiveAudioTier

class AudioTierResolver {
    fun resolve(
        preset: AudioPreset,
        decoders: List<DecoderCapability>,
        cellular: Boolean = false,
        lowBattery: Boolean = false,
    ): EffectiveAudioTier {
        val effectivePreset = when {
            lowBattery -> AudioPreset.BatterySaver
            cellular -> AudioPreset.BatterySaver
            else -> preset
        }

        val hardwareMimes = decoders
            .filter { it.hardwareAccelerated && !it.softwareOnly }
            .map { it.mimeType }
            .toSet()

        val candidates = when (effectivePreset) {
            AudioPreset.BatterySaver -> listOf(
                Candidate("audio/mp4a-latm", 96),
                Candidate("audio/mpeg", 128),
            )
            AudioPreset.Balanced -> listOf(
                Candidate("audio/mp4a-latm", 192),
                Candidate("audio/mp4a-latm", 160),
                Candidate("audio/mpeg", 192),
                Candidate("audio/mp4a-latm", 96),
            )
            AudioPreset.High -> listOf(
                Candidate("audio/mp4a-latm", 256),
                Candidate("audio/mp4a-latm", 192),
                Candidate("audio/mp4a-latm", 160),
                Candidate("audio/mpeg", 192),
                Candidate("audio/mp4a-latm", 96),
            )
        }

        val selected = candidates.firstOrNull { it.mimeType in hardwareMimes }
            ?: Candidate("audio/mp4a-latm", 96)

        return EffectiveAudioTier(
            preset = effectivePreset,
            mimeType = selected.mimeType,
            targetBitrateKbps = selected.bitrateKbps,
            reason = if (selected.mimeType in hardwareMimes) {
                "hardware decoder"
            } else {
                "debug or fallback tier; release builds must not select software decode"
            },
        )
    }

    private data class Candidate(
        val mimeType: String,
        val bitrateKbps: Int,
    )
}
