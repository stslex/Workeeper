package io.github.stslex.workeeper.feature.exercise.di

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleFeatureExerciseTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `feature exercise module verification`() {
        ModuleFeatureExercise().module.verify()
    }
}
