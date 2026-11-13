/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import aws.sdk.kotlin.gradle.util.prop
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Publishes a bundle to Sonatype Portal and waits for it to be validated (but not fully published).
 * States: PENDING, VALIDATING, VALIDATED, FAILED
 * https://central.sonatype.org/publish/publish-portal-api/
 */
@CacheableTask
abstract class SonatypeCentralPortalPublishTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundle: RegularFileProperty

    @Option(option = "bundle", description = "Path to bundle")
    fun setBundleFromOption(path: String) {
        check(Path(path).exists()) { "Bundle not found at $path" }
        bundle.set(Path(path).toFile())
    }

    /**
     * Max time to wait for final state. Defaults to the `aws.sonatype.centralPortal.publishTimeout` property (parsed
     * by [Duration.parse], e.g. `3h` or `PT90M`) if set, otherwise 2 hours.
     */
    @get:Input
    @get:Optional
    abstract val timeoutDuration: Property<Duration>

    /**
     * Max bundle size in bytes, checked before uploading. Defaults to the
     * `aws.sonatype.centralPortal.maxBundleSizeBytes` property; unchecked if neither is set.
     */
    @get:Input
    @get:Optional
    abstract val maxBundleSizeBytes: Property<Long>

    /** Poll interval. */
    @get:Input
    @get:Optional
    abstract val pollInterval: Property<Duration>

    init {
        timeoutDuration.convention(
            project.prop(PUBLISH_TIMEOUT_PROPERTY)?.toString()?.let { Duration.parse(it) } ?: DEFAULT_PUBLISH_TIMEOUT,
        )
        project.prop(MAX_BUNDLE_SIZE_PROPERTY)?.toString()?.toLong()?.let(maxBundleSizeBytes::convention)
        pollInterval.convention(15.seconds)
    }

    internal companion object {
        const val PUBLISH_TIMEOUT_PROPERTY = "aws.sonatype.centralPortal.publishTimeout"
        const val MAX_BUNDLE_SIZE_PROPERTY = "aws.sonatype.centralPortal.maxBundleSizeBytes"
        val DEFAULT_PUBLISH_TIMEOUT = 2.hours
    }

    @TaskAction
    fun run() {
        val (result, time) = measureTimedValue {
            val client = SonatypeCentralPortalClient.fromEnvironment()
            val file = bundle.asFile.get()
            maxBundleSizeBytes.orNull?.let { checkBundleSize(file, it) }

            // 1) Upload
            val deploymentId = client.uploadBundle(file, file.name)
            logger.lifecycle("📤 Uploaded bundle; deploymentId=$deploymentId")

            // 2) Wait for PUBLISHING (which comes after VALIDATING) or FAILED
            client.waitForStatus(
                deploymentId = deploymentId,
                terminalStates = setOf("PUBLISHING", "FAILED"),
                pollInterval = pollInterval.get(),
                timeout = timeoutDuration.get(),
            ) { _, new ->
                logger.lifecycle("📡 Status: $new (deploymentId=$deploymentId)")
            }
        }

        // 3) Evaluate
        try {
            when (result.deploymentState) {
                "PUBLISHING" -> logger.lifecycle("✅ Bundle validated by Maven Central")

                "FAILED" -> {
                    val reasons = result.errors?.values?.joinToString("\n- ", prefix = "\n- ")
                        ?: "\n(no error details returned)"
                    throw RuntimeException("❌ Sonatype deployment FAILED for ${result.deploymentId}$reasons")
                }

                else -> error("💥 Unexpected terminal state: ${result.deploymentState}")
            }
        } finally {
            logger.lifecycle("⏱️ Completed in $time")
        }
    }
}

internal fun checkBundleSize(bundle: File, maxBytes: Long) {
    val size = bundle.length()
    check(size <= maxBytes) {
        "Bundle ${bundle.name} is $size bytes, which exceeds the configured limit of $maxBytes bytes " +
            "(${SonatypeCentralPortalPublishTask.MAX_BUNDLE_SIZE_PROPERTY})."
    }
}
