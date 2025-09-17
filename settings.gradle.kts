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
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":build-plugins:build-support")
include(":build-plugins:kmp-conventions")
include(":build-plugins:smithy-build")
include(":ktlint:style-rules")
include(":ktlint:minor-version-rules")
