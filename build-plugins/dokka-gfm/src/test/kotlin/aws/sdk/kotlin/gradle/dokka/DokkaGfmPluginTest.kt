/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dokka

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DokkaGfmPluginTest {
    @TempDir
    lateinit var projectDir: File

    private fun project(): Project {
        // Dokka's v1 migration helpers register a `dokkaGfmPlugin` configuration which conflicts with this plugin
        projectDir.resolve("gradle.properties").writeText("org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled\n")
        return ProjectBuilder.builder().withProjectDir(projectDir).build().also(::applyPlugins)
    }

    private fun applyPlugins(project: Project) {
        project.plugins.apply("org.jetbrains.dokka")
        project.plugins.apply(DokkaGfmPlugin::class.java)
    }

    private fun Project.dependencyNames(configuration: String): Set<String> = configurations
        .getByName(configuration)
        .allDependencies
        .map { "${it.group}:${it.name}" }
        .toSet()

    @Test
    fun registersGfmTasks() {
        val project = project()

        assertContains(project.tasks.names, "dokkaGenerateModuleGfm")
        assertContains(project.tasks.names, "dokkaGeneratePublicationGfm")
    }

    @Test
    fun lifecycleTaskGeneratesGfm() {
        val project = project()

        val dokkaGenerate = project.tasks.getByName("dokkaGenerate")
        val dependencies = dokkaGenerate.taskDependencies.getDependencies(dokkaGenerate).map { it.name }
        assertContains(dependencies, "dokkaGeneratePublicationGfm")
    }

    @Test
    fun addsGfmRendererToPluginsClasspath() {
        val project = project()

        assertContains(project.dependencyNames("dokkaGfmPlugin"), "org.jetbrains.dokka:gfm-plugin")
    }

    @Test
    fun addsTemplateProcessingOnlyToPublicationClasspath() {
        val project = project()
        val templateProcessing = "org.jetbrains.dokka:gfm-template-processing-plugin"

        // When applied while generating a module, this plugin replaces the module's pages with an all-modules index
        assertFalse(templateProcessing in project.dependencyNames("dokkaGfmPlugin"))
        assertTrue(templateProcessing in project.dependencyNames("dokkaGfmPublicationPluginApiOnly~internal"))
    }
}
