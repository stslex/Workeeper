// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.ui.golden

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import io.github.stslex.workeeper.probe.ui.ProbeCard
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * P1: the kit golden mechanism (Jupiter driving Paparazzi.setup/snapshot/teardown directly —
 * no JUnit4, no vintage engine), minus the kit harness this probe must not depend on.
 * Device config mirrors GOLDEN_DEVICE (Pixel 5, fontScale pinned, no soft buttons) and the
 * load-bearing maxPercentDifference = 0.0.
 */
internal class ProbeCardGoldenTest {

    @Test
    fun probeCard(testInfo: TestInfo) {
        val paparazzi = Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.0f, softButtons = false),
            maxPercentDifference = 0.0,
        )
        paparazzi.setup(testInfo.toTestName())
        try {
            paparazzi.snapshot {
                ProbeCard(label = "golden")
            }
        } finally {
            paparazzi.teardown()
        }
    }
}

private fun TestInfo.toTestName(): TestName {
    val method = testMethod.get()
    return TestName(
        packageName = method.declaringClass.packageName,
        className = method.declaringClass.simpleName,
        methodName = method.name,
    )
}
