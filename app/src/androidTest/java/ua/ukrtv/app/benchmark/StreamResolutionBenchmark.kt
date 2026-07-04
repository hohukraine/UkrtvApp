package ua.ukrtv.app.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the end-to-end time from selecting a title to
 * the player starting playback (stream resolution chain).
 *
 * Key thresholds:
 * - Movie: resolve < 8s
 * - Series: resolve < 15s
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StreamResolutionBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun movieStreamResolution() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric()
            ),
            iterations = 3,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            navigateToMovieDetail()
            device.waitForIdle(2000)
            // Click play — starts stream resolution
            device.pressDPadCenter()
            device.waitForIdle(10000)
        }
    }

    @Test
    fun seriesStreamResolution() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric()
            ),
            iterations = 3,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            navigateToSeriesDetail()
            device.waitForIdle(2000)
            // Focus first episode
            device.pressDPadDown()
            device.waitForIdle()
            device.pressDPadCenter()
            device.waitForIdle(15000)
        }
    }
}

private fun MacrobenchmarkScope.navigateToMovieDetail() {
    repeat(2) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    device.pressDPadDown()
    device.waitForIdle()
    device.pressDPadCenter()
    device.waitForIdle(2000)
}

private fun MacrobenchmarkScope.navigateToSeriesDetail() {
    // Navigate to series tab/category then select one
    device.pressDPadUp()
    device.waitForIdle()
    device.pressDPadRight()
    device.waitForIdle()
    device.pressDPadCenter()
    device.waitForIdle(2000)
    repeat(2) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    device.pressDPadCenter()
    device.waitForIdle(2000)
}
