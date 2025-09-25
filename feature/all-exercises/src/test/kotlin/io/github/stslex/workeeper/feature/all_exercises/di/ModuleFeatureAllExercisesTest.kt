package io.github.stslex.workeeper.feature.all_exercises.di

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleFeatureAllExercisesTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `feature all exercises module verification`() {
        ModuleFeatureAllExercises().module.verify()
    }
}
