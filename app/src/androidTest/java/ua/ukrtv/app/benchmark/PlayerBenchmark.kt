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
class PlayerBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playerNavigateCold() {
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
            // Navigate to a detail page then tap play
            navigateToDetailAndPlay()
            device.waitForIdle(3000)
        }
    }

    @Test
    fun playerBackButton() {
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
            navigateToDetailAndPlay()
            device.waitForIdle(2000)
            // Press back to return to detail
            device.pressBack()
            device.waitForIdle(2000)
        }
    }
}

private fun MacrobenchmarkScope.navigateToDetailAndPlay() {
    // Navigate to first movie in the grid
    repeat(4) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    device.pressDPadDown()
    device.waitForIdle()
    device.pressDPadCenter()
    device.waitForIdle(2000)
    // Tap play button or stream link
    device.pressDPadCenter()
    device.waitForIdle(1000)
}
