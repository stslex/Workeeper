// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = UserInfoFetcherImplTest.TestApplication::class, sdk = [33])
internal class UserInfoFetcherImplTest {

    class TestApplication : Application()

    private fun jsonClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private fun newFetcher(httpClient: HttpClient) = UserInfoFetcherImpl(
        httpClient = httpClient,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `fetch parses email and name from full userinfo response`() = runTest {
        val client = jsonClient {
            respond(
                content = """{"sub":"1","email":"u@example.com","name":"User One","picture":"x"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = newFetcher(client).fetch("token")

        assertNotNull(result)
        assertEquals(UserInfo(email = "u@example.com", name = "User One"), result)
    }

    @Test
    fun `fetch attaches Bearer token in Authorization header`() = runTest {
        var capturedAuth: String? = null
        val client = jsonClient { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"email":"u@example.com","name":"User"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        newFetcher(client).fetch("my-token")

        assertEquals("Bearer my-token", capturedAuth)
    }

    @Test
    fun `fetch returns UserInfo with null fields when payload omits them`() = runTest {
        val client = jsonClient {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = newFetcher(client).fetch("token")

        assertEquals(UserInfo(email = null, name = null), result)
    }

    @Test
    fun `fetch returns null on 4xx error response`() = runTest {
        val client = jsonClient { respondError(HttpStatusCode.Unauthorized) }

        val result = newFetcher(client).fetch("token")

        assertNull(result)
    }

    @Test
    fun `fetch hits oauth2 v3 userinfo endpoint`() = runTest {
        var capturedUrl: String? = null
        val client = jsonClient { request ->
            capturedUrl = request.url.toString()
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        newFetcher(client).fetch("token")

        assertTrue(
            capturedUrl?.contains("oauth2/v3/userinfo") == true,
            "expected userinfo URL, got $capturedUrl",
        )
    }
}
