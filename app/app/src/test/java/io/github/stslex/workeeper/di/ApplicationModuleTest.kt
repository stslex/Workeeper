package io.github.stslex.workeeper.di

import android.content.Context
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ApplicationModuleTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `all app modules`() {
        ApplicationModule().module.verify(
            extraTypes = listOf(Context::class),
        )
    }
}
