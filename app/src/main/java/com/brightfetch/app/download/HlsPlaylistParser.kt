package com.brightfetch.app.download

object HlsPlaylistParser {
    data class Variant(
        val reference: String,
        val bandwidthBitsPerSecond: Long?,
        val averageBandwidthBitsPerSecond: Long?,
        val width: Int?,
        val height: Int?,
        val codecs: String?,
        val name: String?,
        val audioGroup: String?,
    )

    data class Encryption(
        val method: String,
        val keyReference: String?,
        val ivHex: String?,
        val keyFormat: String?,
    )

    data class Segment(
        val reference: String,
        val sequenceNumber: Long,
        val encryption: Encryption?,
        val durationSeconds: Double? = null,
        val isInitializationSegment: Boolean = false,
    )

    data class MediaPlaylist(
        val segments: List<Segment>,
        val isVod: Boolean,
        val usesByteRanges: Boolean,
    ) {
        val segmentReferences: List<String> get() = segments.map(Segment::reference)
        val mediaSegmentCount: Int get() = segments.count { !it.isInitializationSegment }
        val totalDurationSeconds: Double?
            get() = segments
                .asSequence()
                .filterNot(Segment::isInitializationSegment)
                .mapNotNull(Segment::durationSeconds)
                .toList()
                .takeIf { it.isNotEmpty() }
                ?.sum()
        val isEncrypted: Boolean get() = segments.any { it.encryption != null }
        val hasUnsupportedEncryption: Boolean get() = segments.any { segment ->
            segment.encryption?.let { encryption ->
                !encryption.method.equals("AES-128", ignoreCase = true) ||
                    encryption.keyReference.isNullOrBlank() ||
                    (encryption.keyFormat != null &&
                        !encryption.keyFormat.equals("identity", ignoreCase = true))
            } == true
        }
    }

    fun parseMasterPlaylist(playlist: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        var pendingAttributes: String? = null
        playlist.lineSequence().map(String::trim).forEach { line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    pendingAttributes = line.substringAfter(':', "")
                }

                line.isNotEmpty() && !line.startsWith('#') -> {
                    pendingAttributes?.let { attributes ->
                        val resolution = attribute(attributes, "RESOLUTION")
                            ?.split('x', 'X', limit = 2)
                        variants += Variant(
                            reference = line,
                            bandwidthBitsPerSecond = attribute(attributes, "BANDWIDTH")
                                ?.toLongOrNull()
                                ?.takeIf { it > 0L },
                            averageBandwidthBitsPerSecond = attribute(attributes, "AVERAGE-BANDWIDTH")
                                ?.toLongOrNull()
                                ?.takeIf { it > 0L },
                            width = resolution?.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 },
                            height = resolution?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 },
                            codecs = attribute(attributes, "CODECS")?.takeIf(String::isNotBlank),
                            name = attribute(attributes, "NAME")?.takeIf(String::isNotBlank),
                            audioGroup = attribute(attributes, "AUDIO")?.takeIf(String::isNotBlank),
                        )
                    }
                    pendingAttributes = null
                }
            }
        }
        return variants
    }

    fun highestBandwidthVariant(playlist: String): String? {
        return parseMasterPlaylist(playlist)
            .maxByOrNull { it.bandwidthBitsPerSecond ?: 0L }
            ?.reference
    }

    fun parseMediaPlaylist(playlist: String): MediaPlaylist {
        val segments = mutableListOf<Segment>()
        var sequenceNumber = 0L
        var currentEncryption: Encryption? = null
        var pendingDurationSeconds: Double? = null
        var usesByteRanges = false
        playlist.lineSequence().map(String::trim).forEach { line ->
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                    sequenceNumber = line.substringAfter(':', "0").trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                    val method = attribute(line, "METHOD").orEmpty()
                    currentEncryption = if (method.equals("NONE", ignoreCase = true)) {
                        null
                    } else {
                        Encryption(
                            method = method,
                            keyReference = attribute(line, "URI"),
                            ivHex = attribute(line, "IV"),
                            keyFormat = attribute(line, "KEYFORMAT"),
                        )
                    }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingDurationSeconds = line
                        .substringAfter(':', "")
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull()
                        ?.takeIf { it >= 0.0 && it.isFinite() }
                }
                line.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) -> {
                    usesByteRanges = true
                }
                line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                    if (attribute(line, "BYTERANGE") != null) usesByteRanges = true
                    attribute(line, "URI")?.let { reference ->
                        segments += Segment(
                            reference = reference,
                            sequenceNumber = sequenceNumber,
                            encryption = currentEncryption,
                            isInitializationSegment = true,
                        )
                    }
                }
                line.isNotEmpty() && !line.startsWith('#') -> {
                    segments += Segment(
                        reference = line,
                        sequenceNumber = sequenceNumber,
                        encryption = currentEncryption,
                        durationSeconds = pendingDurationSeconds,
                    )
                    sequenceNumber += 1L
                    pendingDurationSeconds = null
                }
            }
        }
        return MediaPlaylist(
            segments = segments,
            isVod = playlist.contains("#EXT-X-ENDLIST", ignoreCase = true),
            usesByteRanges = usesByteRanges,
        )
    }

    fun initializationVector(ivHex: String?, sequenceNumber: Long): ByteArray? {
        if (ivHex.isNullOrBlank()) {
            return ByteArray(16).also { result ->
                for (index in 0 until 8) {
                    result[15 - index] = (sequenceNumber ushr (index * 8)).toByte()
                }
            }
        }
        val normalized = ivHex.removePrefix("0x").removePrefix("0X")
        if (normalized.length > 32 || normalized.any { it.digitToIntOrNull(16) == null }) return null
        val padded = normalized.padStart(32, '0')
        return ByteArray(16) { index ->
            padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun attribute(line: String, name: String): String? {
        val attributes = line.substringAfter(':', line)
        var index = 0
        while (index < attributes.length) {
            while (index < attributes.length && (attributes[index] == ',' || attributes[index].isWhitespace())) {
                index += 1
            }
            val keyStart = index
            while (index < attributes.length && attributes[index] != '=' && attributes[index] != ',') {
                index += 1
            }
            if (index >= attributes.length || attributes[index] != '=') {
                while (index < attributes.length && attributes[index] != ',') index += 1
                continue
            }
            val key = attributes.substring(keyStart, index).trim()
            index += 1
            val value = if (index < attributes.length && attributes[index] == '"') {
                index += 1
                val valueStart = index
                while (index < attributes.length && attributes[index] != '"') index += 1
                attributes.substring(valueStart, index).also {
                    if (index < attributes.length) index += 1
                }
            } else {
                val valueStart = index
                while (index < attributes.length && attributes[index] != ',') index += 1
                attributes.substring(valueStart, index).trim()
            }
            if (key.equals(name, ignoreCase = true)) return value
            while (index < attributes.length && attributes[index] != ',') index += 1
        }
        return null
    }
}
