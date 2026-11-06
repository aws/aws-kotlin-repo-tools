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
    compileOnly(libs.dokka.gradle.plugin)

    testImplementation(libs.dokka.gradle.plugin)
    testImplementation(libs.kotlin.test)
}

gradlePlugin {
    plugins {
        val awsKotlinDokkaGfmPlugin by creating {
            id = "aws.sdk.kotlin.gradle.dokkagfm"
            implementationClass = "aws.sdk.kotlin.gradle.dokka.DokkaGfmPlugin"
            description = "Adds a GitHub Flavored Markdown (GFM) publication to Dokka"
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.test {
    useJUnitPlatform()
}
