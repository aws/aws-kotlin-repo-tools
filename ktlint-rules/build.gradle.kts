/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Lint rules for the AWS SDK for Kotlin"

plugins {
    `maven-publish`
    kotlin("jvm")
}

kotlin {
    sourceSets {
        main {
            dependencies {
                api(libs.ktlint.cli.ruleset.core)
            }
        }

        test {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktlint.rule.engine)
                implementation("org.slf4j:slf4j-simple:2.0.7") // TODO: Move to libs
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
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
