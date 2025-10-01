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
    implementation(project(":build-plugins:build-support"))
    compileOnly(kotlin("gradle-plugin", "2.1.0"))
}

gradlePlugin {
    plugins {
        val awsKotlinRepoToolsPlugin by creating {
            id = "aws.sdk.kotlin.gradle.kmp"
            implementationClass = "aws.sdk.kotlin.gradle.kmp.KmpDefaultsPlugin"
            description = "Kotlin Multiplatform defaults and build settings for AWS Kotlin repositories"
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
