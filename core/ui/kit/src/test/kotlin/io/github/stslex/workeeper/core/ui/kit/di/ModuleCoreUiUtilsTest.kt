package io.github.stslex.workeeper.core.ui.kit.di

import android.content.Context
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleCoreUiUtilsTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `core ui utils module verification`() {
        ModuleCoreUiUtils().module.verify(
            extraTypes = listOf(Context::class),
        )
    }
}
