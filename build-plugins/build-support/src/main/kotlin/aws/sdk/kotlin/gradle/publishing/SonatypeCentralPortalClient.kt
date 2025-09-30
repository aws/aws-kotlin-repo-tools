/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import java.io.File
import java.util.Base64
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock

/**
 * A client used for interacting with the Sonatype Publish Portal API
 * https://central.sonatype.org/publish/publish-portal-api/
 */
class SonatypeCentralPortalClient(
    private val authHeader: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(60))
        .writeTimeout(Duration.ofSeconds(60))
        .retryOnConnectionFailure(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {
    companion object {
        const val CENTRAL_PORTAL_USERNAME = "SONATYPE_CENTRAL_PORTAL_USERNAME"
        const val CENTRAL_PORTAL_PASSWORD = "SONATYPE_CENTRAL_PORTAL_PASSWORD"
        const val CENTRAL_PORTAL_BASE_URL = "https://central.sonatype.com"

        fun buildAuthHeader(user: String, password: String): String {
            val b64 = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
            return "Bearer $b64"
        }

        /** Helper to create a client using env vars for creds. */
        fun fromEnvironment(): SonatypeCentralPortalClient {
            val user = System.getenv(CENTRAL_PORTAL_USERNAME)?.takeIf { it.isNotBlank() } ?: error("$CENTRAL_PORTAL_USERNAME not configured")
            val pass = System.getenv(CENTRAL_PORTAL_PASSWORD)?.takeIf { it.isNotBlank() } ?: error("$CENTRAL_PORTAL_PASSWORD not configured")
            return SonatypeCentralPortalClient(buildAuthHeader(user, pass))
        }
    }

    private val apiBase = CENTRAL_PORTAL_BASE_URL.toHttpUrl()

    @Serializable
    data class StatusResponse(
        val deploymentId: String,
        val deploymentName: String? = null,
        val deploymentState: String,
        val purls: List<String>? = null,
        val errors: Map<String, List<String>>? = null,
    )

    /** Uploads a bundle and returns deploymentId. */
    fun uploadBundle(bundle: File, deploymentName: String): String {
        require(bundle.isFile && bundle.length() > 0L) { "Bundle does not exist or is empty: $bundle" }

        val url = apiBase.newBuilder()
            .addPathSegments("api/v1/publisher/upload")
            .addQueryParameter("name", deploymentName)
            .addQueryParameter("publishingType", "AUTOMATIC") // set USER_MANAGED to upload the deployment, but not release it
            .build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "bundle",
                bundle.name,
                bundle.asRequestBody("application/octet-stream".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw httpError("upload", resp)
            val id = resp.body?.string()?.trim().orEmpty()
            if (resp.code != 201 || id.isEmpty()) {
                throw RuntimeException("Upload returned ${resp.code} but no deploymentId body; body=$id")
            }
            return id
        }
    }

    /** Returns current deployment status. */
    fun getStatus(deploymentId: String): StatusResponse {
        val url = apiBase.newBuilder()
            .addPathSegments("api/v1/publisher/status")
            .addQueryParameter("id", deploymentId)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .post("".toRequestBody(null))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw httpError("status", resp)
            val payload = resp.body?.string().orEmpty()
            return try {
                json.decodeFromString<StatusResponse>(payload)
            } catch (e: Exception) {
                throw RuntimeException("Failed to parse status JSON (HTTP ${resp.code}): $payload", e)
            }
        }
    }

    /** Polls until one of [terminalStates] is reached, returning the final StatusResponse. */
    @OptIn(ExperimentalTime::class) // for Clock.System.now()
    fun waitForStatus(
        deploymentId: String,
        terminalStates: Set<String>,
        pollInterval: kotlin.time.Duration,
        timeout: kotlin.time.Duration,
        onStateChange: (old: String?, new: String) -> Unit = { _, _ -> }
    ): StatusResponse {
        val deadline = Clock.System.now() + timeout
        var lastState: String? = null

        while (Clock.System.now() < deadline) {
            val status = getStatus(deploymentId)
            if (status.deploymentState != lastState) {
                onStateChange(lastState, status.deploymentState)
                lastState = status.deploymentState
            }
            if (status.deploymentState in terminalStates) return status
            Thread.sleep(pollInterval.inWholeMilliseconds)
        }
        throw RuntimeException("Timed out waiting for deployment $deploymentId to reach one of $terminalStates")
    }

    private fun httpError(context: String, resp: Response): RuntimeException {
        val body = resp.body?.string().orEmpty()
        return RuntimeException("HTTP error during $context: ${resp.code}.\nResponse: $body")
    }
}
