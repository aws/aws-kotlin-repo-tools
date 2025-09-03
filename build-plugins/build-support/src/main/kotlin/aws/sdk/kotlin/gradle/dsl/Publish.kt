/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.getOrNull
import aws.sdk.kotlin.gradle.util.verifyRootProject
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active
import java.time.Duration

private object Properties {
    const val SKIP_PUBLISHING = "skipPublish"
}

// TODO Remove once aws-sdk-kotlin migrates to Central Portal
private const val PUBLISH_GROUP_NAME_PROP = "publishGroupName"
private const val SIGNING_KEY_PROP = "signingKey"
private const val SIGNING_PASSWORD_PROP = "signingPassword"
private const val SONATYPE_USERNAME_PROP = "sonatypeUsername"
private const val SONATYPE_PASSWORD_PROP = "sonatypePassword"

private object EnvironmentVariables {
    const val GROUP_ID = "JRELEASER_PROJECT_JAVA_GROUP_ID"
    const val MAVEN_CENTRAL_USERNAME = "JRELEASER_MAVENCENTRAL_USERNAME"
    const val MAVEN_CENTRAL_TOKEN = "JRELEASER_MAVENCENTRAL_TOKEN"
    const val GPG_PASSPHRASE = "JRELEASER_GPG_PASSPHRASE"
    const val GPG_PUBLIC_KEY = "JRELEASER_GPG_PUBLIC_KEY"
    const val GPG_SECRET_KEY = "JRELEASER_GPG_SECRET_KEY"
    const val GENERIC_TOKEN = "JRELEASER_GENERIC_TOKEN"
}

internal val ALLOWED_PUBLICATION_NAMES = setOf(
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

// TODO Remove this once aws-sdk-kotlin migrates to Central Portal
fun Project.configureNexusPublishing(repoName: String, githubOrganization: String = "awslabs") {
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
                }
            }
        }

        if (project.hasProperty(SIGNING_KEY_PROP) && project.hasProperty(SIGNING_PASSWORD_PROP)) {
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    project.property(SIGNING_KEY_PROP) as String,
                    project.property(SIGNING_PASSWORD_PROP) as String,
                )
                sign(publications)
            }

            // FIXME - workaround for https://github.com/gradle/gradle/issues/26091
            val signingTasks = tasks.withType<Sign>()
            tasks.withType<AbstractPublishToMaven>().configureEach {
                mustRunAfter(signingTasks)
            }
        }
    }

    fun isAvailableForNexusPublication(project: Project, publication: MavenPublication): Boolean {
        var shouldPublish = true

        // Check SKIP_PUBLISH_PROP
        if (project.extra.has(Properties.SKIP_PUBLISHING)) shouldPublish = false

        // Only publish publications with the configured group from JReleaser or everything if JReleaser group is not configured
        val publishGroupName = project.findProperty(PUBLISH_GROUP_NAME_PROP) as? String
        shouldPublish = shouldPublish && (publishGroupName == null || publication.groupId.startsWith(publishGroupName))

        // Validate publication name is allowed to be published
        shouldPublish = shouldPublish && ALLOWED_PUBLICATION_NAMES.any { publication.name.equals(it, ignoreCase = true) }

        return shouldPublish
    }

    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf {
            isAvailableForNexusPublication(project, publication).also {
                if (!it) {
                    logger.warn("Skipping publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}")
                }
            }
        }
    }
}

/**
 * Configure publishing for this project. This applies the `maven-publish` and `signing` plugins and configures
 * the publications.
 * @param repoName the repository name (e.g. `smithy-kotlin`, `aws-sdk-kotlin`, etc)
 * @param githubOrganization the name of the GitHub organization that [repoName] is located in
 */
fun Project.configurePublishing(repoName: String, githubOrganization: String = "awslabs") {
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
                }
            }
        }

        val secretKey = System.getenv(EnvironmentVariables.GPG_SECRET_KEY)
        val passphrase = System.getenv(EnvironmentVariables.GPG_PASSPHRASE)

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

/**
 * Configure nexus publishing plugin. This (conditionally) enables the `gradle-nexus.publish-plugin` and configures it.
 */
fun Project.configureNexus(
    nexusUrl: String = "https://ossrh-staging-api.central.sonatype.com/service/local/",
    snapshotRepositoryUrl: String = "https://central.sonatype.com/repository/maven-snapshots/",
) {
    verifyRootProject { "Kotlin SDK nexus configuration must be applied to the root project only" }

    val requiredProps = listOf(SONATYPE_USERNAME_PROP, SONATYPE_PASSWORD_PROP, PUBLISH_GROUP_NAME_PROP)
    val doConfigure = requiredProps.all { project.hasProperty(it) }
    if (!doConfigure) {
        logger.info("skipping nexus configuration, missing one or more required properties: $requiredProps")
        return
    }

    apply(plugin = "io.github.gradle-nexus.publish-plugin")
    extensions.configure<NexusPublishExtension> {
        val publishGroupName = project.property(PUBLISH_GROUP_NAME_PROP) as String
        group = publishGroupName
        packageGroup.set(publishGroupName)
        repositories {
            create("awsNexus") {
                this.nexusUrl.set(uri(nexusUrl))
                this.snapshotRepositoryUrl.set(uri(snapshotRepositoryUrl))
                username.set(project.property(SONATYPE_USERNAME_PROP) as String)
                password.set(project.property(SONATYPE_PASSWORD_PROP) as String)
            }
        }

        transitionCheckOptions {
            maxRetries.set(180)
            delayBetween.set(Duration.ofSeconds(10))
        }
    }
}

/**
 * Configure JReleaser publishing plugin. This (conditionally) enables the `org.jreleaser` plugin and configures it.
 */
fun Project.configureJReleaser() {
    verifyRootProject { "JReleaser configuration must be applied to the root project only" }

    val requiredVariables = listOf(
        EnvironmentVariables.MAVEN_CENTRAL_USERNAME,
        EnvironmentVariables.MAVEN_CENTRAL_TOKEN,
        EnvironmentVariables.GPG_PASSPHRASE,
        EnvironmentVariables.GPG_PUBLIC_KEY,
        EnvironmentVariables.GPG_SECRET_KEY,
        EnvironmentVariables.GENERIC_TOKEN,
    )

    if (!requiredVariables.all { !System.getenv(it).isNullOrBlank() }) {
        logger.warn("Skipping JReleaser configuration, missing one or more required environment variables: ${requiredVariables.joinToString()}")
        return
    }

    // Collect a set of native artifact IDs from every project
    val nativeArtifactIds = providers.provider {
        allprojects.flatMap {
            it.extensions.findByType(PublishingExtension::class.java)
                ?.publications
                ?.withType(MavenPublication::class.java)
                ?.filter { it.name in ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES }
                ?.map { it.artifactId }
                ?: emptySet()
        }.toSet()
    }

    apply(plugin = "org.jreleaser")
    extensions.configure<JReleaserExtension> {
        project {
            version = providers.gradleProperty("sdkVersion").get()
        }

        // JReleaser requires a releaser to be configured even though we don't use it.
        // https://github.com/jreleaser/jreleaser/discussions/1725#discussioncomment-10674529
        release {
            generic {
                skipRelease = true
            }
        }

        // We don't announce our releases anywhere
        // https://jreleaser.org/guide/latest/reference/announce/index.html
        announce {
            active = Active.NEVER
        }

        deploy {
            maven {
                mavenCentral {
                    create("maven-central") {
                        active = Active.ALWAYS // the Maven deployer default is ALWAYS, but MavenCentral is NEVER
                        url = "https://central.sonatype.com/api/v1/publisher"
                        stagingRepository(rootProject.layout.buildDirectory.dir("m2").get().toString())
                        artifacts {
                            artifactOverride {
                                artifactId = "version-catalog"
                                jar = false // Version catalogs don't produce a JAR
                                verifyPom = false // JReleaser fails when processing <packaging>toml</packaging> tag: `Unknown packaging: toml`
                            }
                            gradle.projectsEvaluated {
                                nativeArtifactIds.get().forEach {
                                    artifactOverride {
                                        artifactId = it
                                        jar = false // Native artifacts produce klibs, not JARs
                                        verifyPom = false // JReleaser fails when processing <packaging>klib</packaging> tag: `Unknown packaging: klib`
                                    }
                                }
                                logger.info("Configured JReleaser artifact overrides for the following artifacts: ${nativeArtifactIds.get().joinToString()}")
                            }
                        }
                        maxRetries = 100
                        retryDelay = 60 // seconds
                    }
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
        if (overrideGroupNameValidation && publication.groupId !in ALLOWED_KOTLIN_NATIVE_PUBLICATION_NAMES) {
            println("Overriding K/N publication, project=${project.name}; publication=${publication.name}; group=${publication.groupId}")
        } else {
            shouldPublish = shouldPublish && publication.groupId in ALLOWED_KOTLIN_NATIVE_GROUP_NAMES
        }
    } else {
        shouldPublish = false
    }

    return shouldPublish
}
