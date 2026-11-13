/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.dokka.markdown

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DokkaMarkdownPluginTest {
    @TempDir
    lateinit var tempDir: File

    private fun project(pluginMode: String? = null): Project {
        val projectDir = tempDir.resolve(pluginMode ?: "default").apply { mkdirs() }
        if (pluginMode != null) {
            projectDir.resolve("gradle.properties").writeText("org.jetbrains.dokka.experimental.gradle.pluginMode=$pluginMode\n")
        }

        return ProjectBuilder.builder().withProjectDir(projectDir).build().also {
            it.plugins.apply("org.jetbrains.dokka")
            it.plugins.apply(DokkaMarkdownPlugin::class.java)
        }
    }

    private fun Project.dependencyNames(configuration: String): Set<String> = configurations
        .getByName(configuration)
        .allDependencies
        .map { "${it.group}:${it.name}" }
        .toSet()

    @Test
    fun appliesInEveryV2PluginMode() {
        listOf("V2Enabled", "V2EnabledWithHelpers").forEach { pluginMode ->
            assertContains(project(pluginMode).tasks.names, "dokkaGeneratePublicationMarkdown", pluginMode)
        }
    }

    @Test
    fun registersMarkdownTasks() {
        val project = project()

        assertContains(project.tasks.names, "dokkaGenerateModuleMarkdown")
        assertContains(project.tasks.names, "dokkaGeneratePublicationMarkdown")
    }

    @Test
    fun lifecycleTaskGeneratesMarkdown() {
        val project = project()

        val dokkaGenerate = project.tasks.getByName("dokkaGenerate")
        val dependencies = dokkaGenerate.taskDependencies.getDependencies(dokkaGenerate).map { it.name }
        assertContains(dependencies, "dokkaGeneratePublicationMarkdown")
    }

    @Test
    fun addsGfmRendererToPluginsClasspath() {
        val project = project()

        assertContains(project.dependencyNames("dokkaMarkdownPlugin"), "org.jetbrains.dokka:gfm-plugin")
    }

    @Test
    fun addsTemplateProcessingOnlyToPublicationClasspath() {
        val project = project()
        val templateProcessing = "org.jetbrains.dokka:gfm-template-processing-plugin"

        // When applied while generating a module, this plugin replaces the module's pages with an all-modules index
        assertFalse(templateProcessing in project.dependencyNames("dokkaMarkdownPlugin"))
        assertTrue(templateProcessing in project.dependencyNames("dokkaMarkdownPublicationPluginApiOnly~internal"))
    }
}
