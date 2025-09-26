package io.github.stslex.workeeper.feature.single_training.di

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleFeatureSingleTrainingTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `feature single training module verification`() {
        ModuleFeatureSingleTraining().module.verify()
    }
}
