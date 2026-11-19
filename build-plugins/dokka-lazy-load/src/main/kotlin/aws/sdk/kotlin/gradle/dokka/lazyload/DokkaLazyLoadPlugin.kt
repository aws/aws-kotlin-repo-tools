/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload

import aws.sdk.kotlin.gradle.dokka.lazyload.transformers.LazyNavigationLoaderInstaller
import aws.sdk.kotlin.gradle.dokka.lazyload.transformers.NavigationSplitter
import aws.sdk.kotlin.gradle.dokka.lazyload.transformers.RootOnlyNavigationCommandHandler
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.renderers.PostAction
import org.jetbrains.dokka.templates.CommandHandler
import org.jetbrains.dokka.templates.TemplatingPlugin
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Dokka plugin that replaces Dokka's single monolithic `navigation.html` (the whole documented corpus, roughly
 * 200 MB for the AWS SDK for Kotlin, injected into every page's DOM) with a lazy-loading navigation pane.
 *
 * ## Why
 * Dokka's default navigation tree serializes the entire ~438-module corpus into one `navigation.html` that
 * `navigation-loader.js` fetches and `innerHTML`s into the DOM on every page, then sweeps with several full-tree
 * `querySelectorAll` passes. Browsers lag or crash. This is a client-side DOM/render/memory problem, not a
 * network one — the fix is to never ship the whole tree.
 *
 * ## The lazy model
 * The pane loads only the top-level items (the modules) at first. Expanding a node dynamically fetches and renders
 * only that node's DIRECT children:
 *  - expanding a module fetches the module's package list,
 *  - expanding a package fetches that package's declarations,
 *  - and so on.
 *
 * The navigation data lives in a tree of small per-node `nav.json` files (one per expandable node, each holding
 * only that node's direct children), placed in the same directory that hosts the node's own content page
 * (`s3/nav.json`, `s3/<pkg>/nav.json`, `s3/<pkg>/-s3-client/nav.json`), plus a shared top-level `<root>/nav.json`
 * listing the modules.
 *
 * ## Extensions
 *  - [LazyNavigationLoaderInstaller] (an `htmlPreprocessors` extension) ships the replacement
 *    `scripts/navigation-loader.js` that renders `nav.json` and fetches children on demand.
 *  - [RootOnlyNavigationCommandHandler] overrides the templating plugin's `addToNavigationCommandHandler` so a
 *    multi-module build writes the assembled `navigation.html` only at the root, not once more per module.
 *  - [NavigationSplitter] (a [CoreExtensions.postActions] extension) runs AFTER rendering, parses Dokka's own
 *    fully-resolved `navigation.html`, splits it into the per-node `nav.json` tree, and deletes the monolith.
 *    A node's `nav.json` path derives from its resolved href, which only exists once rendering has run.
 *
 * ## Requirements
 * Dokka's `templating-plugin`, at the same version as Dokka, must be on the Dokka plugin classpath; the plugin
 * loads it at install time. The Dokka Gradle plugin adds it by default (`addDefaultDokkaDependencies`).
 *
 * Registered via `META-INF/services/org.jetbrains.dokka.plugability.DokkaPlugin`.
 */
public class DokkaLazyLoadPlugin : DokkaPlugin() {
    private val dokkaBase: DokkaBase by lazy { plugin<DokkaBase>() }
    private val templatingPlugin: TemplatingPlugin by lazy { plugin<TemplatingPlugin>() }

    /**
     * Replace the bundled `navigation-loader.js` with the lazy loader. Runs after the base
     * [ScriptsInstaller][org.jetbrains.dokka.base.renderers.html.ScriptsInstaller] so it can remove the default
     * loader page and add its own; see [LazyNavigationLoaderInstaller].
     */
    public val lazyNavigationLoaderInstaller: Extension<PageTransformer, *, *> by extending {
        dokkaBase.htmlPreprocessors providing ::LazyNavigationLoaderInstaller order {
            after(dokkaBase.scriptsInstaller)
        }
    }

    /**
     * Split Dokka's rendered `navigation.html` into the per-node `nav.json` tree and delete the monolith. Runs as
     * a post-render action so it sees fully-resolved hrefs; see [NavigationSplitter].
     */
    public val navigationSplitter: Extension<PostAction, *, *> by extending {
        CoreExtensions.postActions providing ::NavigationSplitter
    }

    /**
     * Assemble the multi-module `navigation.html` at the output root only; see [RootOnlyNavigationCommandHandler].
     */
    public val rootOnlyNavigationCommandHandler: Extension<CommandHandler, *, *> by extending {
        templatingPlugin.directiveBasedCommandHandlers providing ::RootOnlyNavigationCommandHandler override
            templatingPlugin.addToNavigationCommandHandler
    }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement(): PluginApiPreviewAcknowledgement = PluginApiPreviewAcknowledgement
}
