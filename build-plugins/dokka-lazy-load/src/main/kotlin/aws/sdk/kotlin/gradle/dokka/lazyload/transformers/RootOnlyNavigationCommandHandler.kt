/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.base.templating.AddToNavigationCommand
import org.jetbrains.dokka.base.templating.Command
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.templates.CommandHandler
import org.jsoup.nodes.Attributes
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Assembles the multi-module `navigation.html` from each module's navigation fragment and writes it **once**, at
 * the output root.
 *
 * Dokka's stock `AddToNavigationCommandHandler` also writes a full copy of the assembled tree into every module's
 * directory. The lazy loader never reads those copies and [NavigationSplitter] replaces the root file with the
 * `nav.json` tree, so writing them only costs time and disk (one full-corpus copy per module).
 *
 * This is a copy of the Dokka 2.2.0 `AddToNavigationCommandHandler` (plugin-templating,
 * `org/jetbrains/dokka/templates/AddToNavigationCommandHandler.kt`) minus the per-module writes. After a Dokka
 * upgrade, diff it against the new upstream version and port any fixes.
 *
 * Adapted from Dokka's `AddToNavigationCommandHandler`, Copyright 2014-2024 JetBrains s.r.o., licensed under the
 * Apache 2.0 license.
 */
internal class RootOnlyNavigationCommandHandler(
    private val context: DokkaContext,
) : CommandHandler {
    private val navigationFragments = ConcurrentHashMap<String, Element>()

    override fun canHandle(command: Command): Boolean = command is AddToNavigationCommand

    override fun handleCommandAsTag(command: Command, body: Element, input: File, output: File) {
        command as AddToNavigationCommand
        val matches = context.configuration.modules.filter { it.name == command.moduleName }
        when {
            matches.isEmpty() -> context.logger.warn(
                "[RootOnlyNavigationCommandHandler] no module named ${command.moduleName}; its navigation is dropped",
            )

            matches.size > 1 -> context.logger.warn(
                "[RootOnlyNavigationCommandHandler] ${matches.size} modules named ${command.moduleName}; using the first",
            )
        }
        val module = matches.firstOrNull() ?: return
        val key = module.relativePathToOutputDirectory.relativeToOrSelf(context.configuration.outputDir)
        navigationFragments[key.invariantSeparatorsPath] = body
    }

    override fun finish(output: File) {
        if (navigationFragments.isEmpty()) return
        output.resolve(NAVIGATION_HTML).writeText(assemble(navigationFragments).outerHtml() + "\n")
        // assemble() moves the fragments' nodes, so the map can't be reused; drop it to free the parsed documents.
        navigationFragments.clear()
    }

    internal companion object {
        private const val NAVIGATION_HTML = "navigation.html"

        /**
         * Merge module fragments (keyed by the module's output dir relative to the root) into one `div.sideMenu`,
         * in key order. Hrefs are rebased onto the module dir and each module's first node gets the
         * `<module>-nav-submenu` id, exactly as Dokka's stock handler does.
         */
        fun assemble(fragments: Map<String, Element>): Element {
            val sideMenu = Element(Tag.valueOf("div"), "", Attributes().apply { put("class", "sideMenu") })
            fragments.entries.sortedBy { it.key }.forEach { (moduleDir, fragment) ->
                fragment.select("a").forEach { a -> a.attr("href", "$moduleDir/${a.attr("href")}") }
                fragment.childNodes().toList().forEachIndexed { index, child ->
                    if (index == 0) child.attr("id", "$moduleDir-nav-submenu")
                    sideMenu.appendChild(child)
                }
            }
            return sideMenu
        }
    }
}
