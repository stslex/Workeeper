// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.network

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * Wire-level tests over a ktor [MockEngine]. The first test is the C2.1 binary
 * regression guard: after `DriveApi` was made space-aware, the appDataFolder list
 * request must still carry `spaces=appDataFolder` + the `app_` name query. The rest
 * cover the new snapshot-facing surface (byte upload + folder create).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveApiImplTest.TestApplication::class, sdk = [33])
internal class DriveApiImplTest {

    class TestApplication : Application()

    private val fileJson = """{"id":"fid","name":"app_1.db"}"""

    private fun client(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun api(httpClient: HttpClient) = DriveApiImpl(
        httpClient = httpClient,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `listFiles wires the given spaces and query into the request (binary regression)`() = runTest {
        var spaces: String? = null
        var query: String? = null
        var path: String? = null
        val api = api(
            client { request ->
                spaces = request.url.parameters["spaces"]
                query = request.url.parameters["q"]
                path = request.url.encodedPath
                respond(
                    content = """{"files":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        api.listFiles(spaces = "appDataFolder", query = "name contains 'app_' and trashed=false")

        assertEquals("appDataFolder", spaces)
        assertEquals("/drive/v3/files", path)
        assertTrue(query?.contains("app_") == true, "expected app_ prefix query, got $query")
    }

    @Test
    fun `uploadMultipart sends a multipart request carrying metadata parents and payload`() = runTest {
        var path: String? = null
        var uploadType: String? = null
        var body: String? = null
        val api = api(
            client { request ->
                path = request.url.encodedPath
                uploadType = request.url.parameters["uploadType"]
                body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond(
                    content = fileJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val metadata = DriveFileMetadataDto(
            name = "app_1.db",
            parents = listOf("appDataFolder"),
            mimeType = "application/x-sqlite3",
            appProperties = mapOf("app_version" to "1.0"),
        )

        api.uploadMultipart(metadata, "db-bytes".toByteArray())

        assertEquals("/upload/drive/v3/files", path)
        assertEquals("multipart", uploadType)
        assertTrue(body?.contains("appDataFolder") == true, "metadata parents missing: $body")
        assertTrue(body?.contains("db-bytes") == true, "payload missing: $body")
    }

    @Test
    fun `uploadMultipart file overload reads the file bytes into the request`() = runTest {
        var body: String? = null
        val api = api(
            client { request ->
                body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond(
                    content = fileJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val dbFile = File.createTempFile("drive-api-test", ".db").apply { writeText("file-payload") }
        val metadata = DriveFileMetadataDto(
            name = "app_1.db",
            parents = listOf("appDataFolder"),
            mimeType = "application/x-sqlite3",
            appProperties = emptyMap(),
        )

        try {
            api.uploadMultipart(metadata, dbFile)
        } finally {
            dbFile.delete()
        }

        assertTrue(body?.contains("file-payload") == true, "file bytes missing: $body")
    }

    @Test
    fun `createFolder posts folder metadata to the files endpoint`() = runTest {
        var path: String? = null
        var body: String? = null
        val api = api(
            client { request ->
                path = request.url.encodedPath
                body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond(
                    content = """{"id":"folder-id","name":"Workeeper"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val result = api.createFolder("Workeeper")

        assertEquals("folder-id", result.id)
        assertEquals("/drive/v3/files", path)
        assertFalse(path?.contains("upload") == true, "createFolder must not hit the upload endpoint: $path")
        assertTrue(body?.contains("application/vnd.google-apps.folder") == true, "folder mimeType missing: $body")
        assertTrue(body?.contains("Workeeper") == true, "folder name missing: $body")
    }
}
