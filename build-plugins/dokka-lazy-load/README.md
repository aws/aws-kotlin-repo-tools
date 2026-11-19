# dokka-lazy-load

A Dokka plugin that replaces Dokka's single monolithic `navigation.html` with a lazy-loading navigation pane,
so large API-reference sites stay fast in the browser.

## Why this is necessary

Dokka's default HTML format serializes the *entire* documented corpus into one `navigation.html` file. Every
page fetches that file, injects it into the DOM with `innerHTML`, and then sweeps it with several full-tree
`querySelectorAll` passes (to mark the active node, wire expand buttons, etc.).

For the AWS SDK for Kotlin this file is roughly **200 MB** and describes ~438 modules and their packages,
classes, and members. Loading, parsing, and DOM-building that on every page navigation makes the browser lag
badly and, on lower-memory machines, crash the tab. This is a **client-side DOM/render/memory problem, not a
network one** — gzip makes the download tolerable, but the browser still has to materialize the whole tree.
The only real fix is to **never ship the whole tree to the client at once.**

## How it works

The pane loads only the **top-level items** (the modules) at first. Expanding a node dynamically fetches and
renders only that node's **direct children**:

- expanding a module &rarr; fetches the module's package list
- expanding a package &rarr; fetches that package's declarations
- expanding a class &rarr; fetches its nested declarations

Navigation data lives in a tree of small per-node `nav.json` files — one per expandable node, each holding only
that node's direct children — placed in the **same directory that hosts the node's own content page**
(named `nav-<navId>.json` instead when two nodes share a directory, as in a single-module publication):

```
<root>/nav.json                              # the shell: the module list, loaded by every page
s3/nav.json                                  # module s3's packages
s3/aws.sdk.kotlin.services.s3/nav.json       # that package's declarations
s3/aws.sdk.kotlin.services.s3/-s3-client/nav.json   # that class's nested declarations
```

Each child row in a `nav.json` carries everything the loader needs to render it and decide whether it is
expandable:

| Field         | Meaning                                                                              |
|---------------|--------------------------------------------------------------------------------------|
| `navId`       | Dokka's `toc--part` id (positional, unique within a build) — the element id and expand-state key |
| `label`       | the stock expand-button `aria-label` (the node's readable name)                      |
| `pageId`      | Dokka's page id — matched against the page's `#content[pageIds]` to mark it active   |
| `textHtml`    | the label markup (Dokka's `<wbr>` word-break hints preserved)                        |
| `href`        | link target, **relative to this node's own directory**, or `null` when Dokka rendered no link |
| `iconHtml`    | the icon `<span>` Dokka rendered (kind badge), or `null`                             |
| `hasChildren` | whether to draw an expand button without fetching first                              |
| `navFile`     | path to this child's own `nav.json` (**node-relative**), or `null` when it is a leaf |

`href` and `navFile` are stored **relative to the node's own directory** so a `nav.json` composes cleanly
regardless of where it sits; the loader prefixes the page-relative base at render time.

The loader also **persists expansion state** across page loads (via `sessionStorage`) and **auto-expands to the
current page**, marking the active node with the stock loader's `pageId` prefix match (so a member page marks its
enclosing declaration).

## How the plugin is structured

The plugin registers three Dokka extensions:

1. **`LazyNavigationLoaderInstaller`** (a `DokkaBase.htmlPreprocessors` extension) ships the replacement
   `navigation-loader.js`. It runs after the base `ScriptsInstaller`, removes Dokka's default loader page, and
   adds its own under the same output path, so every page loads the lazy loader.

2. **`NavigationSplitter`** (a `CoreExtensions.postActions` extension) runs **after** rendering completes. It
   parses Dokka's own fully-resolved `navigation.html` (found via `DokkaConfiguration.outputDir`) with jsoup,
   splits it into the per-node `nav.json` tree, and then **deletes** the `navigation.html` files Dokka wrote
   (the root file and, in a multi-module build, each module's own navigation fragment) so nothing ships the
   whole corpus.

3. **`RootOnlyNavigationCommandHandler`** overrides the templating plugin's `addToNavigationCommandHandler`. In a
   multi-module build Dokka's stock handler writes the assembled `navigation.html` at the root **and** a full copy
   into every module directory; this handler writes only the root copy, which is all `NavigationSplitter` needs.

### Why split post-render instead of emitting pages during rendering

A node's `nav.json` **path** derives from its resolved `href`, but Dokka fixes each output page's path *before*
rendering while hrefs only resolve *during* rendering. Emitting the `nav.json` files as Dokka pages would
therefore mean reproducing Dokka's `DRI`&rarr;path scheme by hand — fragile across Dokka versions. A
`PostAction` runs once rendering has finished and `navigation.html` is fully written, so parsing that file reuses
Dokka's resolved hrefs, icons, and `<wbr>` labels (as re-serialized by jsoup) with no coupling to Dokka's
`DRI`&rarr;path scheme. It does depend on the `navigation.html` markup classes (`toc--part`, `toc--row`,
`toc--link`, ...).

In multi-module builds each module renders a partial tree that an aggregator later assembles; `NavigationSplitter`
skips those partial renders (`delayTemplateSubstitution == true`) and splits once, on the assembled output.

### Known cost: Dokka still builds the full tree

Dokka still builds the whole navigation tree in memory and writes it once, as the root `navigation.html`, before
`NavigationSplitter` runs, and the splitter then parses that file again. So the plugin does not reduce peak memory
during generation (it has not been measured); it only avoids the per-module copies.

## Consuming this plugin

A repo that generates Dokka HTML needs only to add the plugin as a `dokkaHtmlPlugin` dependency in its Dokka
convention (HTML only, since the plugin does nothing for other formats):

```kotlin
dependencies {
    dokkaHtmlPlugin(libs.aws.kotlin.repo.tools.dokka.lazy.load)
}
```

The plugin uses the jsoup that `dokka-base` already puts on the Dokka runtime classpath, so it adds no runtime
dependencies of its own beyond the Kotlin standard library.

It requires Dokka's `templating-plugin` (same version as Dokka) on the Dokka plugin classpath. The Dokka Gradle
plugin adds it by default; a build that turns off `addDefaultDokkaDependencies` must add it back.

Do **not** also inject a custom navigation loader through Dokka's `customAssets`. Two loaders would both render
into `#sideMenu`, and a stock-derived loader also fetches the `navigation.html` this plugin deletes.

### Local development note (stale plugin jar)

When iterating on the plugin locally and republishing to Maven Local at the **same** version, a consuming repo's
Dokka build may keep using the previous jar (e.g. a reused Gradle daemon or Dokka worker still holds it). Stop the
daemon (`./gradlew --stop`) or bump a dev version.
