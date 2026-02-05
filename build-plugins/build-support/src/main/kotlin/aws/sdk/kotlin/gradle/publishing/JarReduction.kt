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
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import proguard.gradle.ProGuardTask
import java.io.File

fun Project.configureJarReduction(group: String): Set<Task> {
    val jarReplacementTasks = mutableSetOf<Task>()

    subprojects {
        afterEvaluate {
            val jarTask = tasks.findByName("jvmJar") ?: tasks.findByName("jar") ?: return@afterEvaluate

            // Don't reduce empty JARs, attempting it throws an exception.
            if ((jarTask as Jar).source.isEmpty) return@afterEvaluate

            val jar = jarTask.archiveFile
            val jarName = jar.get().asFile.name

            val reduceJarSizeTask = tasks.register<ProGuardTask>("reduceJarSize") {
                dependsOn(jarTask)

                injars(jar)
                outjars(layout.buildDirectory.file("proguard/$jarName"))

                val runtimeClasspath = project.configurations.findByName("jvmRuntimeClasspath") ?: project.configurations.findByName("runtimeClasspath")
                libraryjars(runtimeClasspath)

                // Provide Java runtime
                libraryjars(
                    fileTree("${System.getProperty("java.home")}/jmods") {
                        include("*.jmod")
                    },
                )

                // ProGuard rules
                keep("class * { *; }")
                keepattributes("Signature,InnerClasses,EnclosingMethod,MethodParameters,*Annotation*")
                keepparameternames()
                dontoptimize()
            }

            val mavenPublications = extensions
                .findByType(PublishingExtension::class.java)
                ?.publications
                ?.filter { publication ->
                    publication is MavenPublication
                }.let {
                    if (it.isNullOrEmpty()) {
                        return@afterEvaluate
                    } else {
                        it
                    }
                }
            val kmpPublication = mavenPublications.any { it.name == "kotlinMultiplatform" }
            val publicationName = if (kmpPublication) {
                "jvm"
            } else {
                mavenPublications.first().name
            }
            if (publicationName !in ALLOWED_PUBLICATION_NAMES || extra.getOrNull<Boolean>(Properties.SKIP_PUBLISHING) == true) return@afterEvaluate

            extensions.configure<PublishingExtension> {
                publications {
                    val publication = findByName(publicationName) as MavenPublication
                    publication.artifact(reduceJarSizeTask) {
                        classifier = "reduced"
                    }
                }
            }

            val jarReplacementTask = tasks.register("replaceFullSizeJar") {
                doLast {
                    val mavenLocal = project.findProperty("mavenLocal") as String? == "true"
                    val suffix = if (kmpPublication) "-jvm" else ""
                    val dir = if (mavenLocal) {
                        "${System.getProperty("user.home")}/.m2/repository/$group/${project.name}$suffix/${project.version}"
                    } else {
                        "${rootProject.layout.buildDirectory.get()}/m2/$group/${project.name}$suffix/${project.version}"
                    }

                    File(dir)
                        .listFiles()
                        .filter { it.isFile }
                        .forEach { artifact ->
                            if (artifact.name.startsWith(jarName)) {
                                val reducedArtifactName = artifact.name.replace(".jar", "-reduced.jar")
                                val reducedArtifact = file("$dir/$reducedArtifactName")

                                reducedArtifact.copyTo(artifact, overwrite = true)
                                reducedArtifact.delete()
                            }
                        }
                }
            }
            jarReplacementTasks.add(jarReplacementTask.get())
        }
    }

    return jarReplacementTasks
}
