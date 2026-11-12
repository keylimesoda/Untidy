package com.tidal.wear.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll-jank measurement for the Home screen.
 *
 * Home is a vertically scrollable Compose column (not a LazyColumn in the
 * current UI). UiAutomator swipes exercise the same scroll path a rotary/touch
 * user takes, and [FrameTimingMetric] reports frame duration percentiles.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHome() =
        benchmarkRule.measureRepeated(
            packageName = TargetApp.PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 3,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.None(),
            setupBlock = {
                pressHome()
                startActivityAndWait()

                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                // "UNTIDY" is the always-present Home wordmark.
                device.wait(Until.hasObject(By.text("UNTIDY")), 5_000)
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val centerX = device.displayWidth / 2
            val top = (device.displayHeight * 0.20f).toInt()
            val bottom = (device.displayHeight * 0.80f).toInt()

            // Scroll down through the shelf, then back up. Both directions are
            // inside the measured block so jank is captured either way.
            device.swipe(centerX, bottom, centerX, top, 12)
            device.swipe(centerX, bottom, centerX, top, 12)
            device.swipe(centerX, top, centerX, bottom, 12)
        }
}
