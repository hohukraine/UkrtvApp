package ua.ukrtv.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = "ua.ukrtv.app",
            maxIterations = 15,
            stableIterations = 3,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Scroll through home content rows
            val height = device.displayHeight
            val width = device.displayWidth / 2
            val startY = (height * 0.7).toInt()
            val endY = (height * 0.3).toInt()
            repeat(3) {
                device.swipe(width, startY, width, endY, 15)
                device.waitForIdle()
            }
        }
    }
}
