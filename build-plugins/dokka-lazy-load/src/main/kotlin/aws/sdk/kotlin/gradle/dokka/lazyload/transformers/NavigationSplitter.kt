/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload.transformers

import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.renderers.PostAction
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File

/**
 * A Dokka [PostAction] that splits Dokka's single monolithic `navigation.html` into a tree of small per-node
 * `nav.json` files backing the lazy-loading navigation pane.
 *
 * It runs after rendering because a node's `nav.json` path derives from its resolved `href`, which only exists
 * once rendering has written `navigation.html`. Labels and icons are jsoup's re-serialization of Dokka's markup.
 *
 * ## What it emits
 * For every **expandable** `toc--part` (a node Dokka drew an expand button for, i.e. one with child `toc--part`s),
 * one `nav.json` in that node's own content directory, holding only that node's DIRECT children:
 *  - module `s3`               (href `s3/index.html`)                  -> `s3/nav.json`
 *  - package `...services.s3`  (href `s3/<pkg>/index.html`)            -> `s3/<pkg>/nav.json`
 *  - class `S3Client`          (href `s3/<pkg>/-s3-client/index.html`) -> `s3/<pkg>/-s3-client/nav.json`
 *
 * plus a top-level `<root>/nav.json` listing the modules (the shell every page loads). When two nodes would share
 * a directory (e.g. a single-module publication, whose module page is the root `index.html`), the later node's
 * file is named `nav-<navId>.json` instead; rows always point at their child file explicitly via `navFile`.
 *
 * Each child row carries `navId`, `label` (the stock expand-button `aria-label`), `pageId` (Dokka's page id, used
 * for active-node matching), `textHtml`, `href` (or null when Dokka rendered the node without a link), `iconHtml`
 * (or null), `hasChildren`, and `navFile` (or null). `href` and `navFile` are stored RELATIVE TO THE nav.json's OWN
 * DIRECTORY so a file composes cleanly.
 *
 * After splitting, the `navigation.html` fragments Dokka wrote (the root one, plus one per module in an aggregate)
 * are deleted so nothing ships the whole tree.
 */
public class NavigationSplitter(
    private val context: DokkaContext,
) : PostAction {
    override fun invoke() {
        // In multi-module generation each module first renders a partial output tree that the aggregator later
        // assembles; the final navigation.html only exists after substitution. Skip partial renders and split
        // once, on the assembled output.
        if (context.configuration.delayTemplateSubstitution) return
        val outputDir = context.configuration.outputDir
        val moduleDirs = context.configuration.modules.map { outputDir.resolve(it.relativePathToOutputDirectory) }
        splitNavigation(outputDir, context.logger, moduleDirs)
    }

    internal companion object {
        /** The default `nav.json` filename. Mirrors the loader constant for the shell. */
        const val NAV_FILE_NAME: String = "nav.json"

        /** Synthetic navId of the top-level shell (`<root>/nav.json`) — it has no Dokka node of its own. */
        const val SHELL_ROOT_NAV_ID: String = "nav-root"

        private const val NAVIGATION_HTML = "navigation.html"

        private const val ADD_TO_NAVIGATION_COMMAND = "org.jetbrains.dokka.base.templating.AddToNavigationCommand"

        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

        /**
         * Split `<outputDir>/navigation.html` into the `nav.json` tree and delete the navigation fragments: the
         * root one and, in an aggregate, each module's own fragment in [moduleDirs].
         */
        fun splitNavigation(outputDir: File, logger: DokkaLogger, moduleDirs: List<File> = emptyList()) {
            val rootNavFile = outputDir.resolve(NAVIGATION_HTML)
            if (!rootNavFile.exists()) {
                logger.warn("[NavigationSplitter] $rootNavFile not found; nothing to split, so the sidebar will be empty")
                return
            }

            val topLevelParts = topLevelParts(Jsoup.parse(rootNavFile))
            if (topLevelParts.isEmpty()) {
                logger.warn("[NavigationSplitter] $rootNavFile has no toc--part entries; the sidebar will be empty")
            }

            // Lexical containment (not canonical), so a symlinked module dir inside the output still counts as inside.
            val root = outputDir.toPath().toAbsolutePath().normalize()
            fun isInsideOutput(file: File): Boolean = file.toPath().toAbsolutePath().normalize().startsWith(root)

            // Assign and validate every expandable node's root-relative nav file path before writing anything, so
            // a bad path fails the run without leaving a half-written tree, and collisions resolve deterministically.
            // Names are compared case-insensitively so they can't overwrite each other on case-insensitive disks.
            val navFiles = LinkedHashMap<Element, String>()
            val used = mutableSetOf(NAV_FILE_NAME) // reserved for the shell
            fun claim(path: String): Boolean = used.add(path.lowercase())
            fun assign(part: Element) {
                val childParts = directChildParts(part)
                if (childParts.isEmpty()) return
                // A node whose link is a full URL has no directory in the output; host its file at the root.
                val href = hrefOf(part)
                val dir = if (isAbsoluteHref(href)) "" else contentDirOf(normalizeHref(href))
                var path = joinPath(dir, NAV_FILE_NAME)
                if (!claim(path)) {
                    val base = "nav-${part.id().replace(UNSAFE_FILE_CHARS, "_")}"
                    path = joinPath(dir, "$base.json")
                    var n = 2
                    while (!claim(path)) path = joinPath(dir, "$base-${n++}.json")
                }
                check(!isAbsoluteHref(path) && isInsideOutput(outputDir.resolve(path))) {
                    "[NavigationSplitter] nav file $path for ${part.id()} resolves outside $outputDir"
                }
                navFiles[part] = path
                childParts.forEach(::assign)
            }
            topLevelParts.forEach(::assign)

            fun write(path: String, navId: String, rows: List<Row>) {
                val file = outputDir.resolve(path)
                file.parentFile.mkdirs()
                file.writeBytes(renderNavJson(navId, rows))
            }

            write(NAV_FILE_NAME, SHELL_ROOT_NAV_ID, topLevelParts.map { toRow(it, "", navFiles) })
            navFiles.forEach { (part, path) ->
                val fromDir = contentDirOf(path)
                write(path, part.id(), directChildParts(part).map { toRow(it, fromDir, navFiles) })
            }

            // Delete only the navigation fragments Dokka writes: the root file (always Dokka's, even when empty) and
            // each module's fragment. A blind name match would also delete real pages named `navigation`.
            val moduleCandidates = moduleDirs.map { it.resolve(NAVIGATION_HTML) }.filter { it != rootNavFile }.distinct()
            val deleted = (listOf(rootNavFile).filter { it.delete() }) + moduleCandidates.filter {
                isInsideOutput(it) && it.isFile && isNavigationFragment(it) && it.delete()
            }

            logger.progress(
                "[NavigationSplitter] Emitted ${navFiles.size + 1} nav.json files; deleted ${deleted.size} $NAVIGATION_HTML",
            )
        }

        /**
         * The outermost `toc--part`s: the aggregate wraps modules in `div.sideMenu`, a single-module publication
         * emits its module at the top level. Descent stops at the first `toc--part` on each branch, so this
         * touches only the wrapper elements, not the whole tree.
         */
        private fun topLevelParts(root: Element): List<Element> = root.children().flatMap { child ->
            if (child.hasClass("toc--part")) listOf(child) else topLevelParts(child)
        }

        /** One row for [part] as it appears in a `nav.json` at [fromDir], with href/navFile rebased against it. */
        private fun toRow(part: Element, fromDir: String, navFiles: Map<Element, String>): Row {
            val navFile = navFiles[part]
            val ownRow = ownRow(part)
            val link = ownRow?.selectFirst("a.toc--link")
            val textHtml = textHtmlOf(link, ownRow)
            return Row(
                navId = part.id(),
                label = ownRow?.selectFirst("button.toc--button")?.attr("aria-label")?.takeIf { it.isNotEmpty() }
                    ?: Jsoup.parse(textHtml).text(),
                pageId = part.attr("pageid"),
                textHtml = textHtml,
                href = hrefOf(part).takeIf { it.isNotEmpty() }?.let { relativize(fromDir, it) },
                iconHtml = link?.selectFirst("span.toc--link-grid")?.children()?.firstOrNull()?.outerHtml(),
                navFile = navFile?.let { relativize(fromDir, it) },
            )
        }

        /** Direct child `toc--part` elements of [part] (grandchildren excluded). */
        private fun directChildParts(part: Element): List<Element> = part.children().filter { it.hasClass("toc--part") }

        /** The `toc--link` href of [part]'s own row, or "" when Dokka rendered the node without a link. */
        private fun hrefOf(part: Element): String = ownRow(part)?.selectFirst("a.toc--link")?.attr("href").orEmpty()

        /**
         * The label markup of a row. When an icon is present Dokka wraps icon+label in `span.toc--link-grid`;
         * return just the label span (the second child) so the icon travels separately in `iconHtml`. When Dokka
         * could not resolve the node's link it renders the label without an `<a>`; return the row's own markup minus
         * its button and skip link.
         */
        private fun textHtmlOf(link: Element?, ownRow: Element?): String {
            if (link == null) {
                val row = ownRow?.clone() ?: return ""
                row.select("button.toc--button, a.toc--skip-link").remove()
                return row.html()
            }
            val grid = link.selectFirst("span.toc--link-grid") ?: return link.html()
            return grid.children().getOrNull(1)?.outerHtml().orEmpty()
        }

        /** [part]'s own `toc--row` (the first one that is a direct child, not a descendant's row). */
        private fun ownRow(part: Element): Element? = part.children().firstOrNull { it.hasClass("toc--row") }

        /**
         * A navigation fragment, not a real page: the document's first element is navigation markup (a `toc--part`
         * or `div.sideMenu`) or the module's `AddToNavigationCommand` template wrapper, which is how a module's own
         * fragment is saved in an aggregate.
         */
        private fun isNavigationFragment(file: File): Boolean {
            val first = Jsoup.parse(file).body().children().firstOrNull() ?: return false
            return first.hasClass("toc--part") ||
                first.hasClass("sideMenu") ||
                (first.tagName() == "dokka-template-command" && ADD_TO_NAVIGATION_COMMAND in first.attr("data"))
        }

        /** A full URL or a site-absolute path, which has no place in the output tree. */
        private fun isAbsoluteHref(href: String): Boolean = href.startsWith("/") || SCHEME.containsMatchIn(href)

        /** Split [href] into its path and its `?query`/`#fragment` suffix, as a browser does. */
        private fun splitSuffix(href: String): Pair<String, String> {
            val cut = href.indexOfFirst { it == '?' || it == '#' }
            return if (cut < 0) href to "" else href.substring(0, cut) to href.substring(cut)
        }

        private fun joinPath(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"

        private fun renderNavJson(navId: String, children: List<Row>): ByteArray {
            val sb = StringBuilder()
            sb.append("{\"navId\":").append(jsonString(navId)).append(",\"children\":[")
            children.forEachIndexed { i, row ->
                if (i > 0) sb.append(',')
                sb.append('{')
                    .append("\"navId\":").append(jsonString(row.navId)).append(',')
                    .append("\"label\":").append(jsonString(row.label)).append(',')
                    .append("\"pageId\":").append(jsonString(row.pageId)).append(',')
                    .append("\"textHtml\":").append(jsonString(row.textHtml)).append(',')
                    .append("\"href\":").append(row.href?.let(::jsonString) ?: "null").append(',')
                    .append("\"iconHtml\":").append(row.iconHtml?.let(::jsonString) ?: "null").append(',')
                    .append("\"hasChildren\":").append(row.navFile != null).append(',')
                    .append("\"navFile\":").append(row.navFile?.let(::jsonString) ?: "null")
                    .append('}')
            }
            sb.append("]}")
            return sb.toString().toByteArray(Charsets.UTF_8)
        }

        /**
         * The directory hosting the page at [href] (root-relative): the href minus its trailing file segment, with
         * query/fragment stripped. Returns "" for a root-level page.
         */
        fun contentDirOf(href: String): String {
            val path = splitSuffix(href).first
            val slash = path.lastIndexOf('/')
            return if (slash < 0) "" else path.substring(0, slash)
        }

        /**
         * Resolve `.` and `..` segments in the path of [href], leaving any query/fragment untouched and keeping a
         * trailing `/`. In a relative href, a `..` that would climb above the root is kept, so the result still points
         * outside the output and fails containment checks instead of being silently rewritten to an in-tree path. In
         * an absolute href (`/...`) it stops at `/`.
         */
        fun normalizeHref(href: String): String {
            val (path, suffix) = splitSuffix(href)
            val absolute = path.startsWith("/")
            val segments = path.split('/')
            val out = ArrayList<String>()
            segments.forEachIndexed { i, seg ->
                when (seg) {
                    "", "." -> if (i == segments.lastIndex && out.isNotEmpty()) out.add("")

                    ".." -> if (out.isNotEmpty() && out.last() != "..") {
                        out.removeAt(out.lastIndex)
                    } else if (!absolute) {
                        out.add("..")
                    }

                    else -> out.add(seg)
                }
            }
            return (if (absolute) "/" else "") + out.joinToString("/") + suffix
        }

        /**
         * Rebase [targetHref] (root-relative) to be relative to [fromDir] (also root-relative), the value stored in
         * a `nav.json` at [fromDir]. Emits `../` to climb out of [fromDir], then the remainder of [targetHref],
         * keeping a trailing `/` and any query/fragment. With an empty [fromDir] the target is only normalized; an
         * absolute target or one with a URL scheme is returned as is.
         */
        fun relativize(fromDir: String, targetHref: String): String {
            if (isAbsoluteHref(targetHref)) return targetHref
            if (fromDir.isEmpty()) return normalizeHref(targetHref)
            val (path, suffix) = splitSuffix(normalizeHref(targetHref))
            val from = fromDir.split('/').filter { it.isNotEmpty() }
            val to = path.split('/').filter { it.isNotEmpty() }
            var common = 0
            while (common < from.size && common < to.size && from[common] == to[common]) common++
            val up = "../".repeat(from.size - common)
            val down = to.subList(common, to.size).joinToString("/")
            val slash = if (path.endsWith("/") && down.isNotEmpty()) "/" else ""
            return (up + down + slash).ifEmpty { "." } + suffix
        }

        /** Minimal JSON string escaper for the values embedded in nav.json (HTML markup + paths). */
        fun jsonString(value: String): String {
            val sb = StringBuilder(value.length + 2)
            sb.append('"')
            for (c in value) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }

    private data class Row(
        val navId: String,
        val label: String,
        val pageId: String,
        val textHtml: String,
        val href: String?,
        val iconHtml: String?,
        val navFile: String?,
    )
}
