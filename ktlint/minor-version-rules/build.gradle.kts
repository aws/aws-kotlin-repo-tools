/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Lint rules for minor version bumps"

plugins {
    `maven-publish`
    kotlin("jvm")
}

kotlin {
    sourceSets {
        main {
            dependencies {
                implementation(libs.ktlint.cli.ruleset.core)
            }
        }

        test {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
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

publishing {
    publications {
        create<MavenPublication>("ktlintRules") {
            from(components["kotlin"])
        }
    }
}
