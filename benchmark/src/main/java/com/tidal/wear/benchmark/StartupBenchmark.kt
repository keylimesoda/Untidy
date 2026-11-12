package com.tidal.wear.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for the watch app's launcher activity.
 *
 * No baseline profile is used (see [CompilationMode.None]); this is a scaffold
 * for relative comparisons between builds, not a store-ready startup budget.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() =
        benchmarkRule.measureRepeated(
            packageName = TargetApp.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 3,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            // Startup is fully measured by StartupTimingMetric around the
            // startActivityAndWait() call above.
        }
}
