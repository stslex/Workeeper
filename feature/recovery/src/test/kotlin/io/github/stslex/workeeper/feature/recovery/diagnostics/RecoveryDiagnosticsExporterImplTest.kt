// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.nio.file.Path

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecoveryDiagnosticsExporterImplTest {

    @TempDir
    lateinit var cacheDir: Path

    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private val snapshotProvider = mockk<DatabaseSnapshotProvider>()

    @BeforeEach
    @Suppress("DEPRECATION")
    fun setUp() {
        val packageInfo = PackageInfo().apply {
            versionName = APP_VERSION_NAME
            longVersionCode = APP_VERSION_CODE
        }
        every { context.cacheDir } returns cacheDir.toFile()
        every { context.packageName } returns PACKAGE_NAME
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo(PACKAGE_NAME, 0) } returns packageInfo
        every { packageManager.getInstallerPackageName(PACKAGE_NAME) } returns INSTALL_SOURCE
        every { snapshotProvider.availableMigrationsLabel() } returns MIGRATIONS

        mockkStatic(FileProvider::class)
        every {
            FileProvider.getUriForFile(
                context,
                "$PACKAGE_NAME.fileprovider",
                any(),
            )
        } returns DIAGNOSTIC_URI
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(FileProvider::class)
    }

    @Test
    fun `minimum supported API exports the unchanged startup diagnostic`() = runTest {
        val exporter = RecoveryDiagnosticsExporterImpl(
            context = context,
            snapshotProvider = snapshotProvider,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertEquals(DIAGNOSTIC_URI, exporter.exportStartupMigrationFailure())

        val diagnostic = cacheDir
            .resolve("recovery_share")
            .toFile()
            .listFiles()
            .orEmpty()
            .single()
            .readText()
            .replace(Regex("(?m)^Generated: .+$"), "Generated: <timestamp>")
        val expected = """
            Workeeper recovery diagnostic
            Generated: <timestamp>
            Scenario: startup-time (Scenario 2)

            == App ==
            versionName: $APP_VERSION_NAME
            versionCode: $APP_VERSION_CODE
            currentSchemaVersion: $APP_DATABASE_VERSION

            == Device ==
            model: ${Build.MODEL}
            manufacturer: ${Build.MANUFACTURER}
            androidApi: 28

            == Migrations ==
            registered: $MIGRATIONS

            == Install ==
            installSource: $INSTALL_SOURCE

            == Exception ==
            (no captured exception)
        """.trimIndent() + "\n"

        assertEquals(expected, diagnostic)
    }

    private companion object {
        const val PACKAGE_NAME = "io.github.stslex.workeeper.dev"
        const val APP_VERSION_NAME = "1.2.3"
        const val APP_VERSION_CODE = 4_294_967_296L
        const val INSTALL_SOURCE = "com.android.vending"
        const val MIGRATIONS = "5→6"
        val DIAGNOSTIC_URI: Uri = Uri.parse("content://test/recovery-diagnostic")
    }
}
