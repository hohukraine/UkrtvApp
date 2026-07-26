package ua.ukrtv.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "ua.ukrtv.app",
        includeInStartupProfile = true
    ) {
        // Cold start
        pressHome()
        startActivityAndWait()

        // HomeScreen scroll
        val homeGrid = device.findObject(By.res("home_grid"))
        homeGrid?.setGestureMargin(device.displayWidth / 5)
        repeat(4) {
            homeGrid?.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle()
        }

        // Open DetailScreen
        // Try to find any movie card
        val movieItem = device.findObject(By.res("movie_item"))
        movieItem?.click()
        device.waitForIdle()
        
        device.pressBack()
        device.waitForIdle()

        // Open Settings
        val settingsButton = device.findObject(By.desc("Settings")) 
            ?: device.findObject(By.text("Settings"))
            ?: device.findObject(By.desc("Налаштування"))
            ?: device.findObject(By.text("Налаштування"))
        settingsButton?.click()
        device.waitForIdle()

        // Scroll Settings
        val settingsList = device.findObject(By.res("settings_list"))
        settingsList?.scroll(Direction.DOWN, 1.0f)
        device.waitForIdle()
    }
}
