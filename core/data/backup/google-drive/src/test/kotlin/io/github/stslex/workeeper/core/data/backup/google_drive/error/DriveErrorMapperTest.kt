// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.error

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

internal class DriveErrorMapperTest {

    @Test
    fun `DriveException NotAuthenticated maps to BackupError NotAuthenticated`() {
        val mapped = DriveErrorMapper.toBackupError(DriveException.NotAuthenticated())
        assertEquals(BackupError.NotAuthenticated, mapped)
    }

    @Test
    fun `DriveException AuthRevoked maps to BackupError AuthRevoked`() {
        val mapped = DriveErrorMapper.toBackupError(DriveException.AuthRevoked("401"))
        assertEquals(BackupError.AuthRevoked, mapped)
    }

    @Test
    fun `DriveException QuotaExceeded maps to StorageQuotaExceeded`() {
        val mapped = DriveErrorMapper.toBackupError(DriveException.QuotaExceeded("quota"))
        assertEquals(BackupError.StorageQuotaExceeded, mapped)
    }

    @Test
    fun `DriveException Forbidden maps to AuthRevoked`() {
        val mapped = DriveErrorMapper.toBackupError(DriveException.Forbidden("scope removed"))
        assertEquals(BackupError.AuthRevoked, mapped)
    }

    @Test
    fun `Ktor 401 ClientRequestException maps to AuthRevoked`() {
        val ex = clientRequestException(HttpStatusCode.Unauthorized, "401 unauthorized")
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertEquals(BackupError.AuthRevoked, mapped)
    }

    @Test
    fun `Ktor 403 with quotaExceeded reason maps to StorageQuotaExceeded`() {
        val ex = clientRequestException(
            HttpStatusCode.Forbidden,
            "Client request: 403. Reason: quotaExceeded",
        )
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertEquals(BackupError.StorageQuotaExceeded, mapped)
    }

    @Test
    fun `Ktor 403 with userRateLimitExceeded maps to StorageQuotaExceeded`() {
        val ex = clientRequestException(
            HttpStatusCode.Forbidden,
            "Reason: userRateLimitExceeded",
        )
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertEquals(BackupError.StorageQuotaExceeded, mapped)
    }

    @Test
    fun `Ktor 403 with other reason maps to AuthRevoked`() {
        val ex = clientRequestException(
            HttpStatusCode.Forbidden,
            "Reason: insufficientFilePermissions",
        )
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertEquals(BackupError.AuthRevoked, mapped)
    }

    @Test
    fun `Ktor 4xx other status maps to Io`() {
        val ex = clientRequestException(HttpStatusCode.BadRequest, "bad request")
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertTrue(mapped is BackupError.Io, "expected Io, got $mapped")
    }

    @Test
    fun `IOException maps to NetworkUnavailable`() {
        val mapped = DriveErrorMapper.toBackupError(IOException("offline"))
        assertEquals(BackupError.NetworkUnavailable, mapped)
    }

    @Test
    fun `SerializationException maps to CorruptedBackup`() {
        val mapped = DriveErrorMapper.toBackupError(SerializationException("bad json"))
        assertTrue(mapped is BackupError.CorruptedBackup)
        assertEquals("bad json", (mapped as BackupError.CorruptedBackup).reason)
    }

    @Test
    fun `arbitrary Throwable maps to Unknown`() {
        val ex = RuntimeException("???")
        val mapped = DriveErrorMapper.toBackupError(ex)
        assertTrue(mapped is BackupError.Unknown)
        assertEquals(ex, (mapped as BackupError.Unknown).cause)
    }

    private fun clientRequestException(
        status: HttpStatusCode,
        body: String,
    ): ClientRequestException {
        val response = mockk<HttpResponse>(relaxed = true) {
            every { this@mockk.status } returns status
        }
        return ClientRequestException(response, body)
    }
}
