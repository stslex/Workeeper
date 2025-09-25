package io.github.stslex.workeeper.core.exercise.di

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleCoreExerciseTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `core exercise module verification`() {
        ModuleCoreExercise().module.verify()
    }
}
