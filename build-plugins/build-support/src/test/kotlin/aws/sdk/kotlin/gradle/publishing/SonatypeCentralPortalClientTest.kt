/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.publishing

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DEPLOYMENT_ID = "28570f16-da32-4c14-bd2e-c1acc0782365"

/** Serves canned responses per endpoint and records each request's effective read timeout. */
private class FakePortal : Interceptor {
    val uploadResponses = ArrayDeque<() -> Pair<Int, String>>()
    val statusResponses = ArrayDeque<() -> Pair<Int, String>>()
    val calls = mutableListOf<Pair<String, Int>>() // path to readTimeoutMillis

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        calls += path to chain.readTimeoutMillis()
        val queue = when (path) {
            "/api/v1/publisher/upload" -> uploadResponses
            "/api/v1/publisher/status" -> statusResponses
            else -> error("unexpected path $path")
        }
        val (code, body) = queue.removeFirst().invoke()
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody(null))
            .build()
    }

    fun count(path: String) = calls.count { it.first == path }
}

private fun status(state: String) = 200 to """{"deploymentId":"$DEPLOYMENT_ID","deploymentState":"$state"}"""

class SonatypeCentralPortalClientTest {
    private val fake = FakePortal()
    private val sleeps = mutableListOf<kotlin.time.Duration>()
    private val tempFiles = mutableListOf<File>()

    private fun client(maxAttempts: Int = 4) = SonatypeCentralPortalClient(
        authHeader = "Bearer test",
        client = OkHttpClient.Builder()
            .readTimeout(Duration.ofSeconds(60))
            .addInterceptor(fake)
            .build(),
        json = Json { ignoreUnknownKeys = true },
        uploadReadTimeout = Duration.ofMinutes(30),
        statusRetryPolicy = SonatypeCentralPortalClient.StatusRetryPolicy(maxAttempts = maxAttempts, sleep = { sleeps += it }),
    )

    private fun bundle(): File = Files.createTempFile("bundle", ".zip").toFile().also {
        it.writeText("not really a zip")
        tempFiles += it
    }

    @AfterTest
    fun cleanup() {
        tempFiles.forEach(File::delete)
    }

    @Test
    fun `upload uses its own long read timeout while status keeps the short one`() {
        fake.uploadResponses += { 201 to DEPLOYMENT_ID }
        fake.statusResponses += { status("PUBLISHING") }

        val c = client()
        assertEquals(DEPLOYMENT_ID, c.uploadBundle(bundle(), "bundle.zip"))
        c.getStatus(DEPLOYMENT_ID)

        assertEquals(
            listOf(
                "/api/v1/publisher/upload" to Duration.ofMinutes(30).toMillis().toInt(),
                "/api/v1/publisher/status" to Duration.ofSeconds(60).toMillis().toInt(),
            ),
            fake.calls,
        )
    }

    @Test
    fun `upload is not retried on server error`() {
        fake.uploadResponses += { 503 to "unavailable" }
        fake.uploadResponses += { 201 to DEPLOYMENT_ID }

        assertFailsWith<SonatypeCentralPortalClient.HttpException> {
            client().uploadBundle(bundle(), "bundle.zip")
        }
        assertEquals(1, fake.count("/api/v1/publisher/upload"))
    }

    @Test
    fun `upload is not retried on IOException and says the deployment may exist`() {
        fake.uploadResponses += { throw IOException("connection reset") }
        fake.uploadResponses += { 201 to DEPLOYMENT_ID }

        val e = assertFailsWith<RuntimeException> {
            client().uploadBundle(bundle(), "bundle.zip")
        }
        assertEquals(1, fake.count("/api/v1/publisher/upload"))
        assertTrue(e.message!!.contains("before re-uploading"), e.message)
    }

    @Test
    fun `status polling retries 5xx 429 and IOException then succeeds`() {
        fake.statusResponses += { 500 to "boom" }
        fake.statusResponses += { 429 to "slow down" }
        fake.statusResponses += { throw IOException("timeout") }
        fake.statusResponses += { status("PUBLISHING") }

        val result = client(maxAttempts = 4).waitForStatus(DEPLOYMENT_ID, setOf("PUBLISHING"), 1.milliseconds, 10.seconds)

        assertEquals("PUBLISHING", result.deploymentState)
        assertEquals(4, fake.count("/api/v1/publisher/status"))
        assertEquals(listOf(2.seconds, 4.seconds, 8.seconds), sleeps)
    }

    @Test
    fun `status polling does not retry client errors`() {
        fake.statusResponses += { 401 to "unauthorized" }

        val e = assertFailsWith<RuntimeException> {
            client().waitForStatus(DEPLOYMENT_ID, setOf("PUBLISHING"), 1.milliseconds, 10.seconds)
        }
        assertEquals(1, fake.count("/api/v1/publisher/status"))
        assertTrue(e.message!!.contains(DEPLOYMENT_ID), e.message)
    }

    @Test
    fun `exhausted status retries report deployment ID and that it may still complete`() {
        repeat(3) { fake.statusResponses += { 502 to "bad gateway" } }

        val e = assertFailsWith<RuntimeException> {
            client(maxAttempts = 3).waitForStatus(DEPLOYMENT_ID, setOf("PUBLISHING"), 1.milliseconds, 10.seconds)
        }
        assertEquals(3, fake.count("/api/v1/publisher/status"))
        assertTrue(e.message!!.contains(DEPLOYMENT_ID), e.message)
        assertTrue(e.message!!.contains("may still complete"), e.message)
    }

    @Test
    fun `timeout reports deployment ID and that it may still complete`() {
        repeat(1000) { fake.statusResponses += { status("VALIDATING") } }

        val e = assertFailsWith<RuntimeException> {
            client().waitForStatus(DEPLOYMENT_ID, setOf("PUBLISHING"), 5.milliseconds, 50.milliseconds)
        }
        assertTrue(e.message!!.contains("Timed out"), e.message)
        assertTrue(e.message!!.contains(DEPLOYMENT_ID), e.message)
        assertTrue(e.message!!.contains("may still complete"), e.message)
        assertTrue(e.message!!.contains("VALIDATING"), e.message)
    }
}
