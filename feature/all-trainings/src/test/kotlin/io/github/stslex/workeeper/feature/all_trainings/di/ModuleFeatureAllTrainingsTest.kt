package io.github.stslex.workeeper.feature.all_trainings.di

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleFeatureAllTrainingsTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `feature all trainings module verification`() {
        ModuleFeatureAllTrainings().module.verify()
    }
}
