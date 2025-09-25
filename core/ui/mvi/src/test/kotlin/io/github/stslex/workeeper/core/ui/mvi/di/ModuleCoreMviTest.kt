package io.github.stslex.workeeper.core.ui.mvi.di

import android.content.Context
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleCoreMviTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `core mvi module verification`() {
        ModuleCoreMvi().module.verify(
            extraTypes = listOf(Context::class),
        )
    }
}
