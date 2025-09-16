/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dsl

import aws.sdk.kotlin.gradle.util.verifyRootProject
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Configures Gradle tasks that run or fix minor-version-bump-specific Ktlint rules.
 */
fun Project.configureMinorVersionStrategyRules(lintPaths: List<String>) {
    verifyRootProject { "Task configuration is expected to be configured on the root project" }

    val ktlintVersion = object {} // Can't use Project.javaClass because that's using the Gradle classloader
        .javaClass
        .getResource("ktlint-version.txt")
        ?.readText()
        ?: error("Missing ktlint-version.txt")

    val minorVersionBumpKtlint by configurations.creating

    dependencies {
        minorVersionBumpKtlint("com.pinterest.ktlint:ktlint-cli:$ktlintVersion") {
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
            }
        }
        minorVersionBumpKtlint(project(":ktlint-rules:minor-version-strategy"))
    }

    tasks.register<JavaExec>("minorVersionBumpScan") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check minor version bump rules"
        classpath = minorVersionBumpKtlint
        mainClass.set("com.pinterest.ktlint.Main")
        args = lintPaths
    }

    tasks.register<JavaExec>("minorVersionBumpFix") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check minor version bump rules"
        classpath = minorVersionBumpKtlint
        mainClass.set("com.pinterest.ktlint.Main")
        args = listOf("-F") + lintPaths
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}
