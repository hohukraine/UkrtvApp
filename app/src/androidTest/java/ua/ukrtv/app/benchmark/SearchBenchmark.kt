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
class SearchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun searchColdStart() {
        benchmarkRule.measureRepeated(
            packageName = "ua.ukrtv.app",
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric()
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            searchQuery("тест")
            device.waitForIdle()
        }
    }

    @Test
    fun searchResultsScroll() {
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
            searchQuery("тест")
            device.waitForIdle(2000)
            // Scroll down through search results
            repeat(3) {
                val height = device.displayHeight
                val width = device.displayWidth / 2
                device.swipe(width, (height * 0.7).toInt(), width, (height * 0.3).toInt(), 15)
                device.waitForIdle()
            }
        }
    }
}

private fun MacrobenchmarkScope.searchQuery(query: String) {
    // Navigate to search (assuming search button requires D-pad navigation)
    device.pressDPadUp()
    device.waitForIdle()
    device.pressDPadCenter()
    device.waitForIdle()
    // Type query using keyboard
    device.setText(query)
    device.waitForIdle()
    // Press enter to search
    device.pressDPadCenter()
    device.waitForIdle(1500)
}
