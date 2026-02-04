/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.getOrNull
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

// FIXME Relocate this file to `aws.sdk.kotlin.gradle.publishing`

object Properties {
    const val SKIP_PUBLISHING = "skipPublish"
}

private const val SIGNING_PUBLIC_KEY = "SIGNING_KEY"
private const val SIGNING_SECRET_KEY = "SIGNING_PASSWORD"

val ALLOWED_PUBLICATION_NAMES = setOf(
    "common",
    "jvm",
    "kotlinMultiplatform",
    "metadata",
    "bom",
    "versionCatalog",
    "codegen",
    "codegen-testutils",

    // aws-sdk-kotlin:hll
    "hll-codegen",
    "dynamodb-mapper-codegen",
    "dynamodb-mapper-schema-generator-plugin",
    "dynamodb-mapper-schema-codegen",
    "dynamodb-mapper-schema-generatorPluginMarkerMaven",
)

internal val ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES = setOf(
    "iosArm64",
    "iosSimulatorArm64",
    "iosX64",
    "linuxArm64",
    "linuxX64",
    "macosArm64",
    "macosX64",
    "mingwX64",
)

// Group names which are allowed to publish K/N artifacts
private val ALLOWED_KOTLIN_NATIVE_GROUP_NAMES = setOf(
    "aws.sdk.kotlin.crt",
    "aws.smithy.kotlin",
    "com.sonatype.central.testing.amazon",
    "aws.sdk.kotlin",
)

// Optional override to the above set.
// Used to support local development where you want to run publishToMavenLocal in smithy-kotlin, aws-sdk-kotlin.
internal const val OVERRIDE_KOTLIN_NATIVE_GROUP_NAME_VALIDATION = "aws.kotlin.native.allowPublication"

/**
 * Mark this project as excluded from publishing
 */
fun Project.skipPublishing() {
    extra.set(Properties.SKIP_PUBLISHING, true)
}

/**
 * Configure publishing for this project. This applies the `maven-publish` and `signing` plugins and configures
 * the publications.
 * @param repoName the repository name (e.g. `smithy-kotlin`, `aws-sdk-kotlin`, etc)
 * @param githubOrganization the name of the GitHub organization that [repoName] is located in
 */
fun Project.configurePublishing(repoName: String, githubOrganization: String = "aws") {
    val project = this
    apply(plugin = "maven-publish")

    // FIXME: create a real "javadoc" JAR from Dokka output
    val javadocJar = tasks.register<Jar>("emptyJar") {
        archiveClassifier.set("javadoc")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        from()
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "testLocal"
                url = rootProject.layout.buildDirectory.dir("m2").get().asFile.toURI()
            }
        }

        publications.all {
            if (this !is MavenPublication) return@all
            val publication = this

            project.afterEvaluate {
                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/$githubOrganization/$repoName")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set(repoName)
                            name.set("AWS SDK Kotlin Team")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/$githubOrganization/$repoName.git")
                        developerConnection.set("scm:git:ssh://github.com/$githubOrganization/$repoName.git")
                        url.set("https://github.com/$githubOrganization/$repoName")
                    }

                    artifact(javadocJar)

                    // Add <type>klib</type> for Native platform dependencies
                    if (publication.name in ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES) {
                        withXml {
                            val depsNode = asNode().get("dependencies") as? groovy.util.NodeList
                            if (depsNode == null || depsNode.isEmpty()) {
                                project.logger.info("No dependencies node found for native publication ${publication.name}")
                                return@withXml
                            }

                            val deps = (depsNode.first() as groovy.util.Node).children()

                            deps.forEach { dep ->
                                val node = dep as groovy.util.Node
                                val artifactId = (node.get("artifactId") as? groovy.util.NodeList)
                                    ?.firstOrNull()
                                    ?.let { (it as groovy.util.Node).text() }

                                if (artifactId != null && ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES.any { artifactId.endsWith("-$it", ignoreCase = true) }) {
                                    val existingType = node.get("type") as? groovy.util.NodeList
                                    if (existingType == null || existingType.isEmpty()) {
                                        project.logger.info("Adding <type>klib</type> to dependency on $artifactId")
                                        node.appendNode("type", "klib")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val secretKey = System.getenv(SIGNING_PUBLIC_KEY)
        val passphrase = System.getenv(SIGNING_SECRET_KEY)

        if (!secretKey.isNullOrBlank() && !passphrase.isNullOrBlank()) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(secretKey, passphrase)
                sign(publications)
            }

            // FIXME - workaround for https://github.com/gradle/gradle/issues/26091
            val signingTasks = tasks.withType<Sign>()
            tasks.withType<AbstractPublishToMaven>().configureEach {
                mustRunAfter(signingTasks)
            }
        } else {
            logger.info("Skipping signing configuration, $SIGNING_PUBLIC_KEY or $SIGNING_SECRET_KEY are not set")
        }
    }

    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf {
            isAvailableForPublication(project, publication).also {
                if (!it) {
                    logger.warn("Skipping publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}")
                }
            }
        }
    }
}

internal fun isAvailableForPublication(project: Project, publication: MavenPublication): Boolean {
    var shouldPublish = true

    // Check SKIP_PUBLISH_PROP
    if (project.extra.has(Properties.SKIP_PUBLISHING)) shouldPublish = false

    // Allow overriding K/N publications for local development
    val overrideGroupNameValidation = project.extra.getOrNull<String>(OVERRIDE_KOTLIN_NATIVE_GROUP_NAME_VALIDATION) == "true"

    // Validate publication name
    if (publication.name in ALLOWED_PUBLICATION_NAMES) {
        // Standard publication
    } else if (publication.name in ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES) {
        // Kotlin/Native publication
        if (overrideGroupNameValidation && publication.groupId !in ALLOWED_KOTLIN_NATIVE_GROUP_NAMES) {
            println("Overriding K/N publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}")
        } else {
            shouldPublish = shouldPublish && publication.groupId in ALLOWED_KOTLIN_NATIVE_GROUP_NAMES
        }
    } else {
        shouldPublish = false
    }

    return shouldPublish
}
