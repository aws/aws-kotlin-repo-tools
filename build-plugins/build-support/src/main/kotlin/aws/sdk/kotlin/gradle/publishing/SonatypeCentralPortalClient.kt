/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * A client used for interacting with the Sonatype Publish Portal API
 * https://central.sonatype.org/publish/publish-portal-api/
 */
class SonatypeCentralPortalClient internal constructor(
    private val authHeader: String,
    private val client: OkHttpClient,
    private val json: Json,
    uploadReadTimeout: Duration,
    private val statusRetryPolicy: StatusRetryPolicy,
) {
    constructor(
        authHeader: String,
        client: OkHttpClient = defaultHttpClient(),
        json: Json = defaultJson(),
    ) : this(authHeader, client, json, DEFAULT_UPLOAD_READ_TIMEOUT, StatusRetryPolicy())

    companion object {
        const val CENTRAL_PORTAL_USERNAME = "SONATYPE_CENTRAL_PORTAL_USERNAME"
        const val CENTRAL_PORTAL_PASSWORD = "SONATYPE_CENTRAL_PORTAL_PASSWORD"
        const val CENTRAL_PORTAL_BASE_URL = "https://central.sonatype.com"
        internal const val CENTRAL_PORTAL_DEPLOYMENTS_URL = "$CENTRAL_PORTAL_BASE_URL/publishing/deployments"

        /**
         * After the multi-GB request body is sent, the server may take a long time to respond with the deployment ID.
         */
        internal val DEFAULT_UPLOAD_READ_TIMEOUT: Duration = Duration.ofMinutes(30)

        fun buildAuthHeader(user: String, password: String): String {
            val b64 = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
            return "Bearer $b64"
        }

        fun fromEnvironment(): SonatypeCentralPortalClient {
            val user = System.getenv(CENTRAL_PORTAL_USERNAME)?.takeIf { it.isNotBlank() } ?: error("$CENTRAL_PORTAL_USERNAME not configured")
            val pass = System.getenv(CENTRAL_PORTAL_PASSWORD)?.takeIf { it.isNotBlank() } ?: error("$CENTRAL_PORTAL_PASSWORD not configured")
            return SonatypeCentralPortalClient(buildAuthHeader(user, pass))
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .retryOnConnectionFailure(true)
            .build()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    /** Bounded exponential backoff applied to [getStatus] calls made while polling. */
    internal data class StatusRetryPolicy(
        val maxAttempts: Int = 6,
        val initialBackoff: kotlin.time.Duration = 2.seconds,
        val maxBackoff: kotlin.time.Duration = 30.seconds,
        val sleep: (kotlin.time.Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
    ) {
        init {
            require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        }
    }

    internal class HttpException(
        val context: String,
        val code: Int,
        body: String,
    ) : RuntimeException("HTTP error during $context: $code.\nResponse: $body") {
        val isRetryable: Boolean get() = code == 429 || code in 500..599
    }

    private val apiBase = CENTRAL_PORTAL_BASE_URL.toHttpUrl()

    // OkHttp may transparently replay a non-one-shot request body after a connection failure, which could create a
    // duplicate deployment, so connection-failure retries are disabled for the upload.
    private val uploadClient: OkHttpClient = client.newBuilder()
        .readTimeout(uploadReadTimeout)
        .retryOnConnectionFailure(false)
        .build()

    @Serializable
    data class StatusResponse(
        val deploymentId: String,
        val deploymentName: String? = null,
        val deploymentState: String,
        val purls: List<String>? = null,
        val errors: Map<String, List<String>>? = null,
    )

    /** Uploads a bundle and returns deploymentId. The upload is never retried. */
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

        val response = try {
            uploadClient.newCall(request).execute()
        } catch (e: IOException) {
            throw RuntimeException(
                "Upload of $deploymentName failed before a deployment ID was received. The server may still have " +
                    "accepted it; check $CENTRAL_PORTAL_DEPLOYMENTS_URL for a deployment named \"$deploymentName\" " +
                    "before re-uploading.",
                e,
            )
        }

        response.use { resp ->
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
        onStateChange: (old: String?, new: String) -> Unit = { _, _ -> },
    ): StatusResponse {
        val deadline = Clock.System.now() + timeout
        var lastState: String? = null

        while (Clock.System.now() < deadline) {
            val status = try {
                getStatusWithRetry(deploymentId)
            } catch (e: Exception) {
                throw RuntimeException(
                    "Failed to poll status of deployment $deploymentId (last observed state: ${lastState ?: "unknown"}). " +
                        stillInProgressHint(deploymentId),
                    e,
                )
            }
            if (status.deploymentState != lastState) {
                onStateChange(lastState, status.deploymentState)
                lastState = status.deploymentState
            }
            if (status.deploymentState in terminalStates) return status
            Thread.sleep(pollInterval.inWholeMilliseconds)
        }
        throw RuntimeException(
            "Timed out after $timeout waiting for deployment $deploymentId to reach one of $terminalStates " +
                "(last observed state: ${lastState ?: "unknown"}). " + stillInProgressHint(deploymentId),
        )
    }

    private fun stillInProgressHint(deploymentId: String) = "The deployment may still complete; check deployment $deploymentId at $CENTRAL_PORTAL_DEPLOYMENTS_URL " +
        "instead of re-uploading the bundle."

    private fun getStatusWithRetry(deploymentId: String): StatusResponse {
        var backoff = statusRetryPolicy.initialBackoff
        var attempt = 1
        while (true) {
            try {
                return getStatus(deploymentId)
            } catch (e: Exception) {
                val retryable = e is IOException || (e is HttpException && e.isRetryable)
                if (!retryable || attempt >= statusRetryPolicy.maxAttempts) throw e
            }
            statusRetryPolicy.sleep(backoff)
            backoff = (backoff * 2).coerceAtMost(statusRetryPolicy.maxBackoff)
            attempt++
        }
    }

    private fun httpError(context: String, resp: Response): RuntimeException {
        val body = resp.body?.string().orEmpty()
        return HttpException(context, resp.code, body)
    }
}
