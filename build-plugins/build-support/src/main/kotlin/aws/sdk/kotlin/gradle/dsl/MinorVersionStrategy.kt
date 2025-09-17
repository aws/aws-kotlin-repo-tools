/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Configures Gradle task for minor-version-bump-specific Ktlint rules.
 */
fun Project.configureMinorVersionStrategyRules(lintPaths: List<String>) {
    verifyRootProject { "Task configuration is expected to be configured on the root project" }

    val ktlintVersion = object {} // Can't use Project.javaClass because that's using the Gradle classloader
        .javaClass
        .getResource("ktlint-version.txt")
        ?.readText()
        ?: error("Missing ktlint-version.txt")

    val repoToolsVersion = extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("aws-kotlin-repo-tools-version")
        .get()
        .requiredVersion

    val minorVersionBumpKtlint by configurations.creating

    dependencies {
        minorVersionBumpKtlint("com.pinterest.ktlint:ktlint-cli:$ktlintVersion") {
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
            }
        }
        minorVersionBumpKtlint("aws.sdk.kotlin.gradle:minor-version-rules:$repoToolsVersion")
    }

    tasks.register<JavaExec>("verifyMinorVersionBump") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check minor version bump rules"
        classpath = minorVersionBumpKtlint
        mainClass.set("com.pinterest.ktlint.Main")
        args = lintPaths
    }
}
