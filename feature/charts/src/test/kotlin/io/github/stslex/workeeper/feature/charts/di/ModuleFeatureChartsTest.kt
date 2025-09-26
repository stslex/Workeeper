package io.github.stslex.workeeper.feature.charts.di

import android.content.Context
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleFeatureChartsTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `feature charts module verification`() {
        ModuleFeatureCharts().module.verify(
            extraTypes = listOf(Context::class),
        )
    }
}
