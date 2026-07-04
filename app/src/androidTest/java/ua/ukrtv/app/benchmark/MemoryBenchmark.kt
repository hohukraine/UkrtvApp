package ua.ukrtv.app.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MemoryBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeScreenMemoryUsage() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.MemoryCountersMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Scroll to trigger lazy loading of all sections
            repeat(4) {
                val height = device.displayHeight
                val width = device.displayWidth / 2
                device.swipe(width, (height * 0.7).toInt(), width, (height * 0.3).toInt(), 15)
                device.waitForIdle()
            }
        }
    }

    @Test
    fun detailPageMemoryAllocation() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.MemoryCountersMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            // Navigate to detail
            repeat(4) {
                device.pressDPadRight()
                device.waitForIdle()
            }
            device.pressDPadDown()
            device.waitForIdle()
            device.pressDPadCenter()
            device.waitForIdle(2000)
            // Scroll through full detail content
            repeat(4) {
                val height = device.displayHeight
                val width = device.displayWidth / 2
                device.swipe(width, (height * 0.7).toInt(), width, (height * 0.3).toInt(), 15)
                device.waitForIdle()
            }
        }
    }

    @Test
    fun searchResultsMemoryAllocation() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.MemoryCountersMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT
        ) {
            startActivityAndWait()
            device.waitForIdle()
            // Navigate to search
            device.pressDPadUp()
            device.waitForIdle()
            device.pressDPadCenter()
            device.waitForIdle()
            device.setText("тест")
            device.waitForIdle()
            device.pressDPadCenter()
            device.waitForIdle(2000)
            // Scroll results
            repeat(3) {
                val height = device.displayHeight
                val width = device.displayWidth / 2
                device.swipe(width, (height * 0.7).toInt(), width, (height * 0.3).toInt(), 15)
                device.waitForIdle()
            }
        }
    }
}
