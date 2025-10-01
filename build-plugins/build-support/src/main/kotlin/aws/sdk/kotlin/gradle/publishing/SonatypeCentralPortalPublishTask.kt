/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Publishes a bundle to Sonatype Portal and waits for it to be validated (but not fully published).
 * States: PENDING, VALIDATING, VALIDATED, FAILED
 * https://central.sonatype.org/publish/publish-portal-api/
 */
abstract class SonatypeCentralPortalPublishTask : DefaultTask() {
    @get:InputFile
    abstract val bundle: RegularFileProperty

    /** Max time to wait for final state. */
    @get:Input
    @get:Optional
    abstract val timeoutDuration: Property<Duration>

    /** Poll interval. */
    @get:Input
    @get:Optional
    abstract val pollInterval: Property<Duration>

    init {
        timeoutDuration.convention(45.minutes)
        pollInterval.convention(15.seconds)
    }

    @TaskAction
    fun run() {
        val client = SonatypeCentralPortalClient.fromEnvironment()
        val file = bundle.asFile.get()

        // 1) Upload
        val deploymentId = client.uploadBundle(file, file.name)
        logger.lifecycle("📤 Uploaded bundle; deploymentId=$deploymentId")

        // 2) Wait for VALIDATED or FAILED
        val result = client.waitForStatus(
            deploymentId = deploymentId,
            terminalStates = setOf("VALIDATED", "FAILED"),
            pollInterval = pollInterval.get(),
            timeout = timeoutDuration.get(),
        ) { _, new ->
            logger.lifecycle("📡 Status: $new (deploymentId=$deploymentId)")
        }

        // 3) Evaluate
        when (result.deploymentState) {
            "VALIDATED" -> logger.lifecycle("✅ Bundle validated by Maven Central")
            "FAILED" -> {
                val reasons = result.errors?.values?.joinToString("\n- ", prefix = "\n- ")
                    ?: "\n(no error details returned)"
                throw RuntimeException("❌ Sonatype deployment FAILED for ${result.deploymentId}$reasons")
            }
            else -> error("Unexpected terminal state: ${result.deploymentState}")
        }
    }
}
