/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
rootProject.name = "aws-kotlin-repo-tools"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    val kotlinVersion: String by settings
    // configure default plugin versions
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":build-plugins:api-scan")
include(":build-plugins:build-support")
include(":build-plugins:kmp-conventions")
include(":build-plugins:smithy-build")
include(":ktlint-rules")
