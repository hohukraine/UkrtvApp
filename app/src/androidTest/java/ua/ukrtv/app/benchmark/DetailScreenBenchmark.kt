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

@RunWith(AndroidJUnit4::class)
@LargeTest
class DetailScreenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun detailScreenColdStart() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric(),
                androidx.benchmark.macro.MemoryCountersMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Tap first movie item to navigate to detail
            navigateToDetail()
            device.waitForIdle()
        }
    }

    @Test
    fun detailScreenScrollMetadata() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.FrameTimingMetric(),
                androidx.benchmark.macro.MemoryCountersMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            navigateToDetail()
            device.waitForIdle()
            // Scroll through metadata (actors, comments, seasons)
            scrollDetailScreen()
            device.waitForIdle()
        }
    }
}

private fun MacrobenchmarkScope.navigateToDetail() {
    // D-pad right to focus first non-hero item, then click center
    repeat(4) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    device.pressDPadDown()
    device.waitForIdle()
    device.pressDPadCenter()
    device.waitForIdle(2000)
}

private fun MacrobenchmarkScope.scrollDetailScreen() {
    val height = device.displayHeight
    val width = device.displayWidth / 2
    val startY = (height * 0.7).toInt()
    val endY = (height * 0.3).toInt()
    repeat(3) {
        device.swipe(width, startY, width, endY, 15)
        device.waitForIdle()
    }
}
