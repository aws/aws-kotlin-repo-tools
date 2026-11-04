/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

description = "Dokka plugin that splits Dokka's monolithic navigation.html into lazily loaded per-node nav.json files"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Dokka APIs are provided by the Dokka runtime the plugin is loaded into.
    // Compile against them but do NOT bundle them (they must resolve to the consumer's Dokka version).
    compileOnly(libs.dokka.base)
    compileOnly(libs.dokka.core)
    compileOnly(libs.dokka.templating.plugin)

    // Used by NavigationSplitter and RootOnlyNavigationCommandHandler. dokka-base already puts jsoup on the Dokka
    // runtime classpath, so compile against the same version and leave it out of the published dependencies.
    compileOnly(libs.jsoup)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.jsoup)
    testImplementation(libs.dokka.base)
    testImplementation(libs.dokka.core)
    testImplementation(libs.dokka.templating.plugin)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
        allWarningsAsErrors.set(false) // Dokka bundles stdlib into the classpath, causing an unfixable warning
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

// Publish the plugin jar so consuming repos can resolve it as a Dokka plugin dependency. group
// ("aws.sdk.kotlin.gradle"), version, and the publishing repositories are set by the root `subprojects {}` block.
publishing {
    publications {
        create<MavenPublication>("dokkaLazyLoad") {
            from(components["java"])
            artifactId = "dokka-lazy-load"
        }
    }
}
