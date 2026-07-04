package ua.ukrtv.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
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
class HomeScreenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeScreenColdStart() {
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
            // Wait for home screen to fully render (hero + content rows)
            device.waitForIdle()
            // Scroll down through content rows
            scrollHomeScreen()
            device.waitForIdle()
        }
    }

    @Test
    fun homeScreenWarmStart() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    @Test
    fun homeScreenScrollJank() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.FrameTimingMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            repeat(3) {
                scrollHomeScreen()
                device.waitForIdle()
            }
        }
    }
}

private fun MacrobenchmarkScope.scrollHomeScreen() {
    // Scroll down through the home screen content rows
    val height = device.displayHeight
    val width = device.displayWidth / 2
    val startY = (height * 0.7).toInt()
    val endY = (height * 0.3).toInt()
    repeat(5) {
        device.swipe(width, startY, width, endY, 15)
        device.waitForIdle()
    }
}
