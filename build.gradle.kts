/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.jvm) apply false
}

val releaseVersion = findProperty("release.version") as? String
if (releaseVersion == null) logger.warn("no release version set")

val s3Url = propertyOrEnv("release.s3.url", "RELEASE_S3_URL")
if (s3Url == null) logger.warn("S3 repository not configured, missing S3 url")

allprojects {
    // Enables running `./gradlew allDeps` to get a comprehensive list of dependencies for every subproject
    tasks.register<DependencyReportTask>("allDeps") { }
}

subprojects {
    group = "aws.sdk.kotlin.gradle"
    version = releaseVersion ?: "0.0.1"

    apply(plugin = "maven-publish")
    publishing {
        repositories {
            maven {
                name = "testLocal"
                url = rootProject.layout.buildDirectory.dir("m2").get().asFile.toURI()
            }

            if (s3Url != null) {
                maven {
                    name = "release"
                    url = java.net.URI(s3Url)
                    credentials(AwsCredentials::class.java) {
                        accessKey = propertyOrEnv("aws.accessKeyId", "AWS_ACCESS_KEY_ID")
                        secretKey = propertyOrEnv("aws.secretAccessKey", "AWS_SECRET_ACCESS_KEY")
                        sessionToken = propertyOrEnv("aws.sessionToken", "AWS_SESSION_TOKEN")
                    }
                }
            }
        }
    }
}

fun propertyOrEnv(propName: String, envName: String): String? {
    val env = System.getenv()
    return findProperty(propName) as? String ?: env[envName]
}

val ktlint by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        }
    }

    ktlint(project(":ktlint:style-rules"))
}

val lintPaths = listOf(
    "**/*.{kt,kts}",
)

tasks.register<JavaExec>("ktlint") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = lintPaths
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Auto fix Kotlin code style violations"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F") + lintPaths
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
