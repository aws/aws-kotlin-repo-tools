/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.publishing

import aws.sdk.kotlin.gradle.util.getOrNull
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import proguard.gradle.ProGuardTask
import java.io.File

fun Project.configureJarReduction(group: String) {
    val testLocalJarReplacementTasks = mutableSetOf<Task>()
    val mavenLocalJarReplacementTasks = mutableSetOf<Task>()

    subprojects {
        afterEvaluate {
            // Filter out subprojects
            val jarTask = tasks.findByName("jvmJar") ?: tasks.findByName("jar")
            if (jarTask == null) {
                project.logger.debug("Skipping JAR reduction task configuration for project: ${project.name}. No JAR task found.")
                return@afterEvaluate
            }
            if ((jarTask as Jar).source.isEmpty) {
                project.logger.debug("Skipping JAR reduction task configuration for project: ${project.name}. Empty JAR detected.")
                return@afterEvaluate
            }

            // Configure jar reduction
            val jar = jarTask.archiveFile
            val jarName = jar.get().asFile.name
            val reduceJarSizeTask = tasks.register<ProGuardTask>("reduceJarSize") {
                dependsOn(jarTask)
                injars(jar)
                outjars(layout.buildDirectory.file("proguard/$jarName"))
                // Provide runtime classpath
                val runtimeClasspath = project.configurations.findByName("jvmRuntimeClasspath") ?: project.configurations.findByName("runtimeClasspath")
                libraryjars(runtimeClasspath)
                // Provide Java runtime
                libraryjars(
                    fileTree("${System.getProperty("java.home")}/jmods") {
                        include("*.jmod")
                    },
                )
                // ProGuard rules
                // TODO: Optimize the classes we keep
                keep("class * { *; }")
                keepattributes("Signature,InnerClasses,EnclosingMethod,MethodParameters,*Annotation*")
                keepparameternames()
                dontoptimize()
            }

            // Configure publication of reduced JAR
            val mavenPublications = extensions
                .findByType(PublishingExtension::class.java)
                ?.publications
                ?.filter { it is MavenPublication }
                ?.takeUnless { it.isEmpty() }
            if (mavenPublications == null) {
                project.logger.debug("Skipping reduced JAR artifact for project: ${project.name}. No Maven publication found.")
                return@afterEvaluate
            }
            val kmpPublication = mavenPublications.any { it.name == "kotlinMultiplatform" }
            val publicationName = if (kmpPublication) "jvm" else mavenPublications.single().name
            if (publicationName !in ALLOWED_PUBLICATION_NAMES || extra.getOrNull<Boolean>(Properties.SKIP_PUBLISHING) == true) {
                project.logger.debug("Skipping reduced JAR artifact for project: ${project.name}. Publication '$publicationName' not allowed.")
                return@afterEvaluate
            }
            extensions.configure<PublishingExtension> {
                publications {
                    val publication = findByName(publicationName) as MavenPublication
                    publication.artifact(reduceJarSizeTask) {
                        classifier = "optimized"
                    }
                }
            }

            /**
             * Registers JAR replacement tasks i.e. overwriting reduced JARs into base JARs.
             *
             * Note: We must include the base JAR in our publication or else we have to build our own POM and .module files.
             * The POM and .module files are built in `publishXtoY` tasks if the base JARs are included (`component["java"]` e.g. - the default artifacts)
             * When we want to default to reduced JARs we won't want the base JARs to be published to Maven Central, but we want the POM and .module files to be created.
             */
            fun jarReplacementTask(mavenLocal: Boolean, kmpPublication: Boolean, jarName: String): TaskProvider<Task> {
                val taskName = if (mavenLocal) "replaceMavenLocalFullSizeJar" else "replaceTestLocalFullSizeJar"
                return tasks.register(taskName) {
                    doLast {
                        val suffix = if (kmpPublication) "-jvm" else ""
                        val pathToArtifacts = "$group/${project.name}$suffix/${project.version}"
                        val artifactsDir = if (mavenLocal) {
                            "${repositories.mavenLocal().url.path}/$pathToArtifacts"
                        } else {
                            "${testLocalRepo.absolutePath}/$pathToArtifacts"
                        }
                        File(artifactsDir)
                            .listFiles()
                            .filter { it.isFile && it.name.startsWith(jarName) }
                            .forEach { artifact ->
                                val reducedArtifactName = artifact.name.replace(".jar", "-optimized.jar")
                                val reducedArtifact = file("$artifactsDir/$reducedArtifactName")
                                reducedArtifact.copyTo(artifact, overwrite = true)
                                reducedArtifact.delete()
                            }
                    }
                }
            }
            testLocalJarReplacementTasks.add(jarReplacementTask(mavenLocal = false, kmpPublication, jarName).get())
            mavenLocalJarReplacementTasks.add(jarReplacementTask(mavenLocal = true, kmpPublication, jarName).get())
        }
    }

    // Root project JAR replacement tasks
    tasks.register("replaceTestLocalFullSizeJars") {
        dependsOn(testLocalJarReplacementTasks)
    }
    tasks.register("replaceMavenLocalFullSizeJars") {
        dependsOn(mavenLocalJarReplacementTasks)
    }
}
