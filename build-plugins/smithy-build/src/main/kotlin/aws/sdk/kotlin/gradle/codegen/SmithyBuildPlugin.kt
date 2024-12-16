/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.codegen

import aws.sdk.kotlin.gradle.codegen.dsl.TASK_GENERATE_SMITHY_BUILD
import aws.sdk.kotlin.gradle.codegen.dsl.TASK_GENERATE_SMITHY_PROJECTIONS
import aws.sdk.kotlin.gradle.codegen.dsl.smithyBuildExtension
import aws.sdk.kotlin.gradle.codegen.tasks.GenerateSmithyBuild
import aws.sdk.kotlin.gradle.codegen.tasks.json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import software.amazon.smithy.gradle.tasks.SmithyBuildTask

const val SMITHY_BUILD_EXTENSION_NAME = "smithyBuild"

/**
 * This plugin provides the following functionality:
 * - DSL for programmatically defining a projection
 * - A configuration to add dependencies used for generating projections
 * - Task for generating `smithy-build.json`
 * - Task for generating code using the generated `smithy-build.json`
 *
 * It re-uses tasks from the `software.amazon.smithy.gradle.smithy-base` plugin in an opinionated way.
 *
 * Example usage:
 *
 * ```
 * plugins {
 *     id("aws.sdk.kotlin.gradle.smithybuild")
 * }
 *
 * // Configure `smithy-build.json`
 * smithyBuild {
 *     projections {
 *         create("myProjection") { ... }
 *     }
 * }
 *
 * // Get the codegen config and add the dependencies required for generating the projection(s)
 * val codegen by configurations.getting
 * dependencies {
 *     codegen(project(":codegen:smithy-kotlin-codegen"))
 *     codegen(libs.smithy.cli)
 *     codegen(libs.smithy.model)
 * }
 *
 * // use the generated code in the compilation of this project
 * tasks.withType<KotlinCompile> {
 *     dependsOn(tasks.generateSmithyProjections)
 * }
 *
 * // register the generated code as a kotlin source directory
 * codegen.projections.all {
 *     kotlin.sourceSets.main {
 *         kotlin.srcDir(smithyBuild.smithyKotlinProjectionSrcDir(projectionName))
 *     }
 * }
 * ```
 *
 * Alternatively if you have a static build config you can override the configuration used when generating projections:
 *
 * ```
 * tasks.generateSmithyProjections {
 *     smithyBuildConfigs.set(files("path-to-my-smithy-build.json"))
 * }
 * ```
 */
class SmithyBuildPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = target.run {
        installExtension()
        registerCodegenTasks()
    }

    private fun Project.installExtension() = extensions.create(SMITHY_BUILD_EXTENSION_NAME, SmithyBuildExtension::class.java, project)

    private fun Project.registerCodegenTasks() {
        val generateSmithyBuild = tasks.register<GenerateSmithyBuild>(TASK_GENERATE_SMITHY_BUILD) {
            group = "codegen"
            val configProvider = project.provider {
                smithyBuildExtension.projections.json
            }
            smithyBuildConfig.set(configProvider)
            onlyIf {
                smithyBuildExtension.projections.isNotEmpty()
            }
        }

        val codegenConfig = configurations.register("codegen")

        // FIXME - bug in smithy gradle base plugin that requires these configurations to exist
        // https://github.com/smithy-lang/smithy-gradle-plugin/pull/112
        listOf(
            "smithyCli",
            "smithyBuild",
            "runtimeClasspath",
        ).map(configurations::maybeCreate)

        tasks.register<SmithyBuildTask>(TASK_GENERATE_SMITHY_PROJECTIONS) {
            group = "codegen"
            dependsOn(generateSmithyBuild)
            cliClasspath.set(codegenConfig)
            buildClasspath.set(codegenConfig)
            smithyBuildConfigs.set(project.files(generateSmithyBuild))
        }
    }
}
