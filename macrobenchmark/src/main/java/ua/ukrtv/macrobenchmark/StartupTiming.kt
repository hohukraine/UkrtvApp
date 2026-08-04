package ua.ukrtv.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTiming {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdStartup() = benchmarkRule.measureRepeated(
        packageName = "ua.ukrtv.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarmStartup() = benchmarkRule.measureRepeated(
        packageName = "ua.ukrtv.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        startActivityAndWait()
    }
}
