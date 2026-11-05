/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.publishing

import org.gradle.internal.extensions.core.extra
import org.gradle.testfixtures.ProjectBuilder
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SonatypeCentralPortalPublishTaskTest {
    @Test
    fun `timeout defaults to two hours`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("publishToCentralPortal", SonatypeCentralPortalPublishTask::class.java).get()

        assertEquals(2.hours, task.timeoutDuration.get())
        assertEquals(1024L * 1024 * 1024, task.maxBundleSizeBytes.get())
    }

    @Test
    fun `timeout and max bundle size are configurable via properties`() {
        val project = ProjectBuilder.builder().build()
        project.extra.set(SonatypeCentralPortalPublishTask.PUBLISH_TIMEOUT_PROPERTY, "3h 30m")
        project.extra.set(SonatypeCentralPortalPublishTask.MAX_BUNDLE_SIZE_PROPERTY, "5000000000")
        val task = project.tasks.register("publishToCentralPortal", SonatypeCentralPortalPublishTask::class.java).get()

        assertEquals(3.hours + 30.minutes, task.timeoutDuration.get())
        assertEquals(5_000_000_000L, task.maxBundleSizeBytes.get())
    }

    @Test
    fun `explicit task configuration overrides the property`() {
        val project = ProjectBuilder.builder().build()
        project.extra.set(SonatypeCentralPortalPublishTask.PUBLISH_TIMEOUT_PROPERTY, "3h")
        val task = project.tasks.register("publishToCentralPortal", SonatypeCentralPortalPublishTask::class.java) {
            timeoutDuration.set(10.minutes)
        }.get()

        assertEquals(10.minutes, task.timeoutDuration.get())
    }

    @Test
    fun `bundle size check fails fast above the limit and cites the documented limit`() {
        val bundle = Files.createTempFile("bundle", ".zip").toFile()
        try {
            RandomAccessFile(bundle, "rw").use { it.setLength(2048) }

            checkBundleSize(bundle, 2048)
            val e = assertFailsWith<IllegalStateException> { checkBundleSize(bundle, 2047) }
            assertTrue(e.message!!.contains("2048 bytes"), e.message)
            assertTrue(e.message!!.contains("https://central.sonatype.org/publish/publish-portal-upload/"), e.message)
        } finally {
            bundle.delete()
        }
    }
}
