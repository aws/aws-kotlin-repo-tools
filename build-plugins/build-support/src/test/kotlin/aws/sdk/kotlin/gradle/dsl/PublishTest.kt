/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import kotlinx.coroutines.test.runTest
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.internal.extensions.core.extra
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishTest {
    @Test
    fun `aws-crt-kotlin can publish Kotlin Native artifacts`() = runTest {
        val project = ProjectBuilder.builder().withName("aws-crt-kotlin").build()
        project.group = "aws.sdk.kotlin.crt"
        project.version = "1.2.3"

        project.configurePublishing("aws-crt-kotlin")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            ALLOWED_PUBLICATION_NAMES.forEach {
                val jvmRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.sdk.kotlin.crt"
                    version = "1.2.3"
                    artifactId = "aws-crt-kotlin"
                }
                assertTrue(isAvailableForPublication(project, jvmRuntimePublication))
            }

            ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.forEach {
                val nativeRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.sdk.kotlin.crt"
                    version = "1.2.3"
                    artifactId = "aws-crt-kotlin"
                }
                assertTrue(isAvailableForPublication(project, nativeRuntimePublication))
            }
        }
    }

    @Test
    fun `aws-sdk-kotlin can publish Kotlin Native artifacts`() = runTest {
        val project = ProjectBuilder.builder().withName("aws-sdk-kotlin").build()
        project.group = "aws.sdk.kotlin"
        project.version = "1.2.3"

        project.configurePublishing("aws-sdk-kotlin")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            ALLOWED_PUBLICATION_NAMES.forEach {
                val jvmRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.sdk.kotlin"
                    version = "1.2.3"
                    artifactId = "aws-runtime"
                }
                assertTrue(isAvailableForPublication(project, jvmRuntimePublication))
            }

            ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.forEach {
                val nativeRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.sdk.kotlin"
                    version = "1.2.3"
                    artifactId = "aws-runtime"
                }
                assertTrue(isAvailableForPublication(project, nativeRuntimePublication))
            }
        }
    }

    @Test
    fun `smithy-kotlin can publish Kotlin Native artifacts`() = runTest {
        val project = ProjectBuilder.builder().withName("aws-smithy-kotlin").build()
        project.group = "aws.smithy.kotlin"
        project.version = "1.2.3"

        project.configurePublishing("smithy-kotlin", "smithy-lang")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            ALLOWED_PUBLICATION_NAMES.forEach {
                val jvmRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.smithy.kotlin"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertTrue(isAvailableForPublication(project, jvmRuntimePublication))
            }

            ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.forEach {
                val nativeRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "aws.smithy.kotlin"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertTrue(isAvailableForPublication(project, nativeRuntimePublication))
            }
        }
    }

    @Test
    fun `users can override publication`() = runTest {
        val project = ProjectBuilder.builder().withName("aws-smithy-kotlin").build()
        project.group = "aws.sdk.kotlin"
        project.version = "1.2.3"
        project.extra.set(OVERRIDE_KOTLIN_NATIVE_GROUP_NAME_VALIDATION, "true")

        project.configurePublishing("aws-sdk-kotlin", "aws")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            ALLOWED_PUBLICATION_NAMES.forEach {
                val jvmRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "foo.bar.baz"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertTrue(isAvailableForPublication(project, jvmRuntimePublication))
            }

            ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.forEach {
                val nativeRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "foo.bar.baz"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertTrue(isAvailableForPublication(project, nativeRuntimePublication))
            }
        }
    }

    @Test
    fun `override only works when set to true`() = runTest {
        val project = ProjectBuilder.builder().withName("aws-smithy-kotlin").build()
        project.group = "aws.sdk.kotlin"
        project.version = "1.2.3"
        project.extra.set(OVERRIDE_KOTLIN_NATIVE_GROUP_NAME_VALIDATION, "this is not true")

        project.configurePublishing("aws-sdk-kotlin", "aws")

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            ALLOWED_PUBLICATION_NAMES.forEach {
                val jvmRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "foo.bar.baz"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertTrue(isAvailableForPublication(project, jvmRuntimePublication))
            }

            ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.forEach {
                val nativeRuntimePublication = create(it, MavenPublication::class.java).apply {
                    groupId = "foo.bar.baz"
                    version = "1.2.3"
                    artifactId = "runtime"
                }
                assertFalse(isAvailableForPublication(project, nativeRuntimePublication))
            }
        }
    }
}
