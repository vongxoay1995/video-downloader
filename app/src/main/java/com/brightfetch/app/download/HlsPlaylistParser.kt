package com.brightfetch.app.download

object HlsPlaylistParser {
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
    )

    data class MediaPlaylist(
        val segments: List<Segment>,
        val isVod: Boolean,
        val usesByteRanges: Boolean,
    ) {
        val segmentReferences: List<String> get() = segments.map(Segment::reference)
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

    fun highestBandwidthVariant(playlist: String): String? {
        val lines = playlist.lines()
        val variants = mutableListOf<Pair<Long, String>>()
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) return@forEachIndexed
            val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val reference = lines.drop(index + 1)
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
            if (reference != null) variants += bandwidth to reference
        }
        return variants.maxByOrNull { it.first }?.second
    }

    fun parseMediaPlaylist(playlist: String): MediaPlaylist {
        val segments = mutableListOf<Segment>()
        var sequenceNumber = 0L
        var currentEncryption: Encryption? = null
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
                line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                    attribute(line, "URI")?.let { reference ->
                        segments += Segment(reference, sequenceNumber, currentEncryption)
                    }
                }
                line.isNotEmpty() && !line.startsWith('#') -> {
                    segments += Segment(line, sequenceNumber, currentEncryption)
                    sequenceNumber += 1L
                }
            }
        }
        return MediaPlaylist(
            segments = segments,
            isVod = playlist.contains("#EXT-X-ENDLIST", ignoreCase = true),
            usesByteRanges = playlist.contains("#EXT-X-BYTERANGE", ignoreCase = true),
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
        val quoted = Regex("(?:^|[, :])${Regex.escape(name)}=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1)
        if (quoted != null) return quoted
        return Regex("(?:^|[, :])${Regex.escape(name)}=([^,\\s]*)", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1)
    }
}
