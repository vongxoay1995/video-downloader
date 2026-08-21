package com.brightfetch.app.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsTimestampNormalizerTest {
    @Test
    fun `keeps an increasing timeline unchanged`() {
        val normalizer = HlsTimestampNormalizer(trackCount = 1, fallbackStepsUs = longArrayOf(40_000L))

        val result = longArrayOf(0L, 40_000L, 80_000L, 120_000L).map {
            normalizer.normalize(trackIndex = 0, rawTimestampUs = it)
        }.toLongArray()

        assertArrayEquals(longArrayOf(0L, 40_000L, 80_000L, 120_000L), result)
    }

    @Test
    fun `rebases a large timestamp reset onto the output timeline`() {
        val normalizer = HlsTimestampNormalizer(trackCount = 1, fallbackStepsUs = longArrayOf(40_000L))

        val result = longArrayOf(0L, 40_000L, 80_000L, 1_000_000L, 0L, 40_000L).map {
            normalizer.normalize(trackIndex = 0, rawTimestampUs = it)
        }.toLongArray()

        assertArrayEquals(
            longArrayOf(0L, 40_000L, 80_000L, 1_000_000L, 1_040_000L, 1_080_000L),
            result,
        )
    }

    @Test
    fun `normalizes each media track independently`() {
        val normalizer = HlsTimestampNormalizer(
            trackCount = 2,
            fallbackStepsUs = longArrayOf(40_000L, 20_000L),
        )

        val video = longArrayOf(0L, 40_000L, 800_000L, 0L).map {
            normalizer.normalize(trackIndex = 0, rawTimestampUs = it)
        }
        val audio = longArrayOf(0L, 20_000L, 40_000L, 60_000L).map {
            normalizer.normalize(trackIndex = 1, rawTimestampUs = it)
        }

        assertArrayEquals(longArrayOf(0L, 40_000L, 800_000L, 840_000L), video.toLongArray())
        assertArrayEquals(longArrayOf(0L, 20_000L, 40_000L, 60_000L), audio.toLongArray())
    }

    @Test
    fun `clamps a small backwards movement until raw timestamps catch up`() {
        val normalizer = HlsTimestampNormalizer(trackCount = 1, fallbackStepsUs = longArrayOf(40_000L))

        val result = longArrayOf(0L, 40_000L, 80_000L, 60_000L, 100_000L).map {
            normalizer.normalize(trackIndex = 0, rawTimestampUs = it)
        }

        assertStrictlyIncreasing(result)
        assertArrayEquals(longArrayOf(0L, 40_000L, 80_000L, 80_001L, 100_000L), result.toLongArray())
    }

    @Test
    fun `replaces an invalid negative timestamp with the next sample time`() {
        val normalizer = HlsTimestampNormalizer(trackCount = 1, fallbackStepsUs = longArrayOf(33_333L))

        val result = longArrayOf(-1L, -1L, 66_666L).map {
            normalizer.normalize(trackIndex = 0, rawTimestampUs = it)
        }

        assertArrayEquals(longArrayOf(0L, 33_333L, 66_666L), result.toLongArray())
    }

    private fun assertStrictlyIncreasing(values: List<Long>) {
        values.zipWithNext().forEach { (previous, next) ->
            assertTrue("Expected $next to be greater than $previous", next > previous)
        }
    }
}
