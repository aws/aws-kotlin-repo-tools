/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.gradle.dokka.lazyload

import aws.sdk.kotlin.gradle.dokka.lazyload.transformers.LazyNavigationLoaderInstaller
import aws.sdk.kotlin.gradle.dokka.lazyload.transformers.NavigationSplitter
import org.htmlunit.BrowserVersion
import org.htmlunit.MockWebConnection
import org.htmlunit.WebClient
import org.htmlunit.html.DomElement
import org.htmlunit.html.HtmlPage
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File
import java.net.URL
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real `navigation-loader.js` in HtmlUnit against a small docs tree whose `nav.json` files are produced by
 * [NavigationSplitter] from a fixture `navigation.html`.
 *
 * Each page installs two small shims for HtmlUnit gaps before the loader runs: a `fetch` built on
 * `XMLHttpRequest`, and a `safeSessionStorage.getKeys()` that lists keys with `sessionStorage.key(i)`.
 */
class NavigationLoaderBrowserTest {
    private val siteDir: File = Files.createTempDirectory("lazy-nav-site").toFile()
    private val connection = MockWebConnection()
    private val client = WebClient(BrowserVersion.CHROME)

    @BeforeTest
    fun setUp() {
        siteDir.resolve("navigation.html").writeText(
            """<div class="sideMenu">""" +
                part(
                    "mod-a-nav-submenu",
                    "mod-a::////PointingToDeclaration//",
                    "mod-a/index.html",
                    "mod-a",
                    part(
                        "mod-a-nav-submenu-0",
                        "mod-a::pkg////PointingToDeclaration//",
                        "mod-a/pkg/index.html",
                        "pkg",
                        part(
                            "mod-a-nav-submenu-0-0",
                            "mod-a::pkg/Foo///PointingToDeclaration//",
                            "mod-a/pkg/-foo/index.html",
                            "Foo",
                            part("mod-a-nav-submenu-0-0-0", "mod-a::pkg/Foo.Bar///PointingToDeclaration//", "mod-a/pkg/-foo/-bar/index.html", "Bar"),
                        ),
                    ),
                ) +
                part(
                    "mod-b-nav-submenu",
                    "mod-b::////PointingToDeclaration//",
                    "mod-b/index.html",
                    "mod-b",
                    part("mod-b-nav-submenu-0", "mod-b::qux////PointingToDeclaration//", "mod-b/qux/index.html", "qux"),
                ) +
                "</div>",
        )
        NavigationSplitter.splitNavigation(
            siteDir,
            DokkaConsoleLogger(LoggingLevel.WARN),
            listOf(siteDir.resolve("mod-a"), siteDir.resolve("mod-b")),
        )

        page("index.html", "", "root")
        page("mod-a/pkg/-foo/index.html", "../../../", "mod-a::pkg/Foo///PointingToDeclaration//")
        page("mod-a/pkg/-foo/bar.html", "../../../", "mod-a::pkg/Foo/bar/#/PointingToDeclaration//123")
        resource("scripts/safe-local-storage_blocking.js", classpathText("/dokka/scripts/safe-local-storage_blocking.js"))
        resource("scripts/navigation-loader.js", classpathText(LazyNavigationLoaderInstaller.LOADER_RESOURCE_PATH))

        siteDir.walk().filter { it.isFile && it.extension == "json" }.forEach {
            connection.setResponse(url(it.relativeTo(siteDir).invariantSeparatorsPath), it.readText(), "application/json")
        }
        connection.setDefaultResponse("Not Found", 404, "Not Found", "text/plain")

        client.webConnection = connection
        client.options.isCssEnabled = false
        client.options.isThrowExceptionOnScriptError = true
    }

    @AfterTest
    fun tearDown() {
        client.close()
        siteDir.deleteRecursively()
    }

    @Test
    fun `the shell renders the modules collapsed inside the stock wrapper`() {
        val page = load("index.html")

        val modules = page.querySelectorAll("#sideMenu > div.sideMenu > .toc--part").map { it as DomElement }
        assertEquals(listOf("mod-a-nav-submenu", "mod-b-nav-submenu"), modules.map { it.id })
        modules.forEach { assertTrue(it.isHidden(), it.id) }
        assertEquals("mod-a", button(page, "mod-a-nav-submenu").getAttribute("aria-label"))
        assertEquals("mod-a/index.html", link(page, "mod-a-nav-submenu").getAttribute("href"))
        assertNull(page.getElementById("mod-a-nav-submenu-0"), "children are not loaded until expanded")
    }

    @Test
    fun `the current page's path is opened and its node is marked active`() {
        val page = load("mod-a/pkg/-foo/index.html")

        listOf("mod-a-nav-submenu", "mod-a-nav-submenu-0", "mod-a-nav-submenu-0-0").forEach {
            assertFalse(part(page, it).isHidden(), "$it is open")
            assertEquals("true", button(page, it).getAttribute("aria-expanded"), it)
        }
        assertEquals("true", part(page, "mod-a-nav-submenu-0-0").getAttribute("data-active"))
        assertEquals(1, page.querySelectorAll("[data-active]").size)
        assertEquals("../../../mod-a/pkg/-foo/-bar/index.html", link(page, "mod-a-nav-submenu-0-0-0").getAttribute("href"))
        assertTrue(part(page, "mod-b-nav-submenu").isHidden())
    }

    @Test
    fun `a member page marks its enclosing declaration`() {
        val page = load("mod-a/pkg/-foo/bar.html")

        assertEquals("true", part(page, "mod-a-nav-submenu-0-0").getAttribute("data-active"))
        assertEquals(1, page.querySelectorAll("[data-active]").size)
    }

    @Test
    fun `clicking a node loads its children, and clicking again closes it`() {
        val page = load("index.html")

        click(page, "mod-b-nav-submenu")
        assertFalse(part(page, "mod-b-nav-submenu").isHidden())
        assertNotNull(page.getElementById("mod-b-nav-submenu-0"))
        assertEquals("true", button(page, "mod-b-nav-submenu").getAttribute("aria-expanded"))
        assertEquals("true", stored(page, "mod-b-nav-submenu"))

        click(page, "mod-b-nav-submenu")
        assertTrue(part(page, "mod-b-nav-submenu").isHidden())
        assertEquals("false", button(page, "mod-b-nav-submenu").getAttribute("aria-expanded"))
        assertEquals("false", stored(page, "mod-b-nav-submenu"))
    }

    @Test
    fun `a failed fetch leaves the node closed and the next click retries`() {
        val navUrl = url("mod-b/nav.json")
        val navJson = siteDir.resolve("mod-b/nav.json").readText()
        connection.setResponse(navUrl, "Not Found", 404, "Not Found", "text/plain", emptyList())
        val page = load("index.html")

        click(page, "mod-b-nav-submenu")
        assertTrue(part(page, "mod-b-nav-submenu").isHidden())
        assertNull(page.getElementById("mod-b-nav-submenu-0"))
        assertNull(button(page, "mod-b-nav-submenu").getAttribute("aria-busy").ifEmpty { null })

        connection.setResponse(navUrl, navJson, "application/json")
        click(page, "mod-b-nav-submenu")
        assertFalse(part(page, "mod-b-nav-submenu").isHidden())
        assertNotNull(page.getElementById("mod-b-nav-submenu-0"))
    }

    @Test
    fun `opened nodes are restored on the next page load`() {
        click(load("index.html"), "mod-b-nav-submenu")

        val next = load("index.html")
        assertFalse(part(next, "mod-b-nav-submenu").isHidden())
        assertNotNull(next.getElementById("mod-b-nav-submenu-0"))
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private fun load(path: String): HtmlPage {
        val page = client.getPage<HtmlPage>(url(path))
        client.waitForBackgroundJavaScript(WAIT_MS)
        return page
    }

    private fun click(page: HtmlPage, navId: String) {
        button(page, navId).click<HtmlPage>()
        client.waitForBackgroundJavaScript(WAIT_MS)
    }

    private fun part(page: HtmlPage, navId: String): DomElement = assertNotNull(page.getElementById(navId), "no part $navId")

    private fun button(page: HtmlPage, navId: String): DomElement = assertNotNull(page.querySelector<DomElement>("[id='$navId'] > .toc--row > .toc--button"), "no button on $navId")

    private fun link(page: HtmlPage, navId: String): DomElement = assertNotNull(page.querySelector<DomElement>("[id='$navId'] > .toc--row > .toc--link"), "no link on $navId")

    private fun stored(page: HtmlPage, navId: String): String? = page.executeJavaScript("sessionStorage.getItem('TOC_STATE::$navId')").javaScriptResult?.toString()

    private fun DomElement.isHidden(): Boolean = getAttribute("class").split(' ').contains("toc--part_hidden")

    private fun url(path: String) = URL("$ORIGIN/$path")

    private fun resource(path: String, text: String) = connection.setResponse(url(path), text, "application/javascript")

    private fun classpathText(path: String): String = assertNotNull(javaClass.getResource(path), "missing classpath resource $path").readText()

    /** A minimal Dokka content page: the sidebar container, the content's pageIds, and Dokka's script order. */
    private fun page(path: String, pathToRoot: String, pageIds: String) {
        connection.setResponse(
            url(path),
            """
            <!DOCTYPE html>
            <html><head>
            <script>var pathToRoot = "$pathToRoot";</script>
            <script src="${pathToRoot}scripts/safe-local-storage_blocking.js"></script>
            <script>$FETCH_SHIM</script>
            <script>$STORAGE_KEYS_SHIM</script>
            </head><body>
            <div id="leftColumn"><div class="sidebar--inner" id="sideMenu"></div></div>
            <div class="main-content" id="content" pageIds="$pageIds"></div>
            <script src="${pathToRoot}scripts/navigation-loader.js"></script>
            </body></html>
            """.trimIndent(),
            "text/html",
        )
    }

    /** A `toc--part` in Dokka's navigation markup, with an expand button when it has children. */
    private fun part(id: String, pageId: String, href: String, label: String, vararg children: String): String {
        val button = if (children.isEmpty()) "" else """<button class="toc--button" aria-expanded="false" aria-label="$label"></button>"""
        val skip = if (children.isEmpty()) "" else """<a class="toc--skip-link" href="#main">Skip to content</a>"""
        return """<div class="toc--part" id="$id" pageId="$pageId" data-nesting-level="0"><div class="toc--row">$button""" +
            """<a href="$href" class="toc--link"><span><span>$label</span></span></a>$skip</div>${children.joinToString("")}</div>"""
    }

    private companion object {
        const val ORIGIN = "http://docs.test"
        const val WAIT_MS = 5_000L

        /** Just enough of `fetch` for the loader: `ok` and `json()`, rejecting on a network error. */
        val FETCH_SHIM = """
            window.fetch = function (url) {
              return new Promise(function (resolve, reject) {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', url);
                xhr.onload = function () {
                  resolve({
                    ok: xhr.status >= 200 && xhr.status < 300,
                    status: xhr.status,
                    json: function () { return Promise.resolve(JSON.parse(xhr.responseText)); }
                  });
                };
                xhr.onerror = function () { reject(new TypeError('network error')); };
                xhr.send();
              });
            };
        """.trimIndent()

        /**
         * Dokka's `safeSessionStorage.getKeys()` uses `Object.keys(sessionStorage)`, which returns the stored keys
         * in browsers but `[]` in HtmlUnit. List them with `key(i)` instead.
         */
        val STORAGE_KEYS_SHIM = """
            safeSessionStorage.getKeys = function () {
              var keys = [];
              for (var i = 0; i < sessionStorage.length; i++) keys.push(sessionStorage.key(i));
              return keys;
            };
        """.trimIndent()
    }
}
