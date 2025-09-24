/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.smithy.model)
    implementation(libs.smithy.gradle.base.plugin)
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        val awsKotlinSmithyBuildPlugin by creating {
            id = "aws.sdk.kotlin.gradle.smithybuild"
            implementationClass = "aws.sdk.kotlin.gradle.codegen.SmithyBuildPlugin"
            description = "A plugin that wraps smithy gradle base plugin and provides a DSL for generating smithy-build.json dynamically"
        }
    }
}

tasks.withType<KotlinCompile> {
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
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
