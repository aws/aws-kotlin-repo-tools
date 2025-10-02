/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    kotlin("jvm")
    kotlin("plugin.serialization") version embeddedKotlinVersion
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.nexus.publish.plugin)
    implementation(libs.jreleaser.plugin)
    compileOnly(gradleApi())
    implementation(libs.aws.sdk.s3)
    implementation(libs.aws.sdk.cloudwatch)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

gradlePlugin {
    plugins {
        create("artifact-size-metrics") {
            id = "aws.sdk.kotlin.gradle.artifactsizemetrics"
            implementationClass = "aws.sdk.kotlin.gradle.plugins.artifactsizemetrics.ArtifactSizeMetricsPlugin"
        }
    }
}

val generateKtlintVersion by tasks.registering {
    // generate the version of the runtime to use as a resource.
    // this keeps us from having to manually change version numbers in multiple places
    val resourcesDir = layout.buildDirectory.dir("resources/main/aws/sdk/kotlin/gradle/dsl").get()

    val versionCatalog = rootProject.file("gradle/libs.versions.toml")
    inputs.file(versionCatalog)

    val versionFile = file("$resourcesDir/ktlint-version.txt")
    outputs.file(versionFile)

    val version = libs.ktlint.cli.ruleset.core.get().version
    sourceSets.main.get().output.dir(resourcesDir)
    doLast {
        versionFile.writeText("$version")
    }
}

tasks.withType<KotlinCompile> {
    dependsOn(generateKtlintVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.test {
    useJUnitPlatform()
}
