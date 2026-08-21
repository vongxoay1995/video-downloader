package com.brightfetch.app.download

/**
 * Converts per-track HLS timestamps into the strictly increasing timeline required by MediaMuxer.
 *
 * Some transport streams restart their presentation timestamps at a segment/discontinuity boundary.
 * Small backwards movements are clamped because they can also be caused by reordered video frames;
 * a substantial backwards jump rebases the rest of that track onto the current output timeline.
 */
internal class HlsTimestampNormalizer(
    trackCount: Int,
    fallbackStepsUs: LongArray = LongArray(trackCount) { MIN_STEP_US },
) {
    private val states = Array(trackCount) { trackIndex ->
        TrackState(
            estimatedStepUs = fallbackStepsUs
                .getOrElse(trackIndex) { MIN_STEP_US }
                .coerceAtLeast(MIN_STEP_US),
        )
    }

    fun normalize(trackIndex: Int, rawTimestampUs: Long): Long {
        val state = states[trackIndex]
        if (!state.initialized) {
            val firstTimestampUs = rawTimestampUs.coerceAtLeast(0L)
            state.initialized = true
            state.lastRawUs = firstTimestampUs
            state.lastOutputUs = firstTimestampUs
            return firstTimestampUs
        }

        val usableRawTimestampUs = if (rawTimestampUs >= 0L) {
            rawTimestampUs
        } else {
            state.lastRawUs + state.estimatedStepUs
        }
        val rawDeltaUs = usableRawTimestampUs - state.lastRawUs
        val isTimelineReset = rawDeltaUs < -RESET_THRESHOLD_US

        var outputTimestampUs = usableRawTimestampUs + state.offsetUs
        if (isTimelineReset) {
            outputTimestampUs = state.lastOutputUs + state.estimatedStepUs
            state.offsetUs = outputTimestampUs - usableRawTimestampUs
        } else if (outputTimestampUs <= state.lastOutputUs) {
            outputTimestampUs = state.lastOutputUs + MIN_STEP_US
        }

        val maxLearnableStepUs = (state.estimatedStepUs * MAX_STEP_GROWTH)
            .coerceAtMost(MAX_LEARNED_STEP_US)
        if (rawDeltaUs in MIN_STEP_US..maxLearnableStepUs) {
            state.estimatedStepUs = rawDeltaUs
        }
        state.lastRawUs = usableRawTimestampUs
        state.lastOutputUs = outputTimestampUs
        return outputTimestampUs
    }

    private data class TrackState(
        var initialized: Boolean = false,
        var lastRawUs: Long = 0L,
        var lastOutputUs: Long = -1L,
        var offsetUs: Long = 0L,
        var estimatedStepUs: Long,
    )

    private companion object {
        const val MIN_STEP_US = 1L
        const val RESET_THRESHOLD_US = 250_000L
        const val MAX_STEP_GROWTH = 4L
        const val MAX_LEARNED_STEP_US = 10_000_000L
    }
}
