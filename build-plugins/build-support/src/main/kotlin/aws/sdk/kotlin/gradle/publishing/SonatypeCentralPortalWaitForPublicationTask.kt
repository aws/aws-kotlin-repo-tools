/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Waits for a given deploymentId to enter the PUBLISHED state
 * https://central.sonatype.org/publish/publish-portal-api/
 */
@CacheableTask
abstract class SonatypeCentralPortalWaitForPublicationTask : DefaultTask() {
    @get:Input
    abstract val deploymentId: Property<String>

    @Option(option = "deploymentId", description = "Deployment ID to wait for")
    fun setDeploymentIdFromOption(id: String) {
        deploymentId.set(id)
    }

    /** Max time to wait for final state. */
    @get:Input
    @get:Optional
    abstract val timeoutDuration: Property<Duration>

    /** Poll interval. */
    @get:Input
    @get:Optional
    abstract val pollInterval: Property<Duration>

    init {
        timeoutDuration.convention(90.minutes)
        pollInterval.convention(30.seconds)
    }

    @TaskAction
    fun run() {
        val (result, time) = measureTimedValue {
            val client = SonatypeCentralPortalClient.fromEnvironment()
            val deploymentId = deploymentId.get().takeIf { it.isNotBlank() } ?: error("deploymentId not configured")

            client.waitForStatus(
                deploymentId,
                setOf("PUBLISHED", "FAILED"),
                pollInterval.get(),
                timeoutDuration.get(),
            ) { _, newState ->
                logger.lifecycle("📡 Status: $newState (deploymentId=$deploymentId)")
            }
        }

        try {
            when (result.deploymentState) {
                "PUBLISHED" -> {
                    logger.lifecycle("🚀 Deployment PUBLISHED (deploymentId=$deploymentId)")
                }

                "FAILED" -> {
                    val reasons =
                        result.errors?.values?.joinToString("\n- ", prefix = "\n- ") ?: "\n(no error details returned)"
                    throw RuntimeException("❌ Sonatype publication FAILED for $deploymentId$reasons")
                }

                else -> error("💥 Unexpected terminal state: ${result.deploymentState}")
            }
        } finally {
            logger.lifecycle("⏱️ Completed in $time")
        }
    }
}
