package io.github.stslex.workeeper.core.database

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.ksp.generated.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class ModuleCoreDatabaseTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `core database module verification`() {
        ModuleCoreDatabase().module.verify()
    }
}
